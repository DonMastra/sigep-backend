package com.sigep.payments.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.payments.application.dto.CreateFiscalInvoiceRequest
import com.sigep.payments.application.dto.FiscalInvoiceDetailDto
import com.sigep.payments.application.dto.FiscalInvoiceDto
import com.sigep.payments.application.gateway.AuthorizedVoucherKey
import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.application.gateway.FiscalAuthorizationResult
import com.sigep.payments.application.gateway.FiscalAuthorizationStatus
import com.sigep.payments.application.gateway.VoucherSequenceKey
import com.sigep.payments.domain.model.BillingOutboxEvent
import com.sigep.payments.domain.model.BillingOutboxEventType
import com.sigep.payments.domain.model.BillingOutboxStatus
import com.sigep.payments.domain.model.BillingCharge
import com.sigep.payments.domain.model.BillingProfile
import com.sigep.payments.domain.model.BillingProfileStatus
import com.sigep.payments.domain.model.FiscalAmountTreatment
import com.sigep.payments.domain.model.FiscalAttemptOutcome
import com.sigep.payments.domain.model.FiscalAttemptType
import com.sigep.payments.domain.model.FiscalInvoice
import com.sigep.payments.domain.model.FiscalInvoiceAttempt
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.payments.domain.model.FiscalOtherTax
import com.sigep.payments.domain.model.FiscalVatSubtotal
import com.sigep.payments.domain.model.PaymentStatus
import com.sigep.payments.domain.repository.BillingOutboxRepository
import com.sigep.payments.domain.repository.FiscalInvoiceAttemptRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import com.sigep.payments.domain.repository.PaymentReceiptRepository
import com.sigep.payments.domain.repository.PaymentRepository
import com.sigep.payments.domain.repository.VoucherSequenceRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.LocalDate

@Service
@Transactional
class BillingApplicationService(
    private val paymentRepository: PaymentRepository,
    private val receiptRepository: PaymentReceiptRepository,
    private val invoiceRepository: FiscalInvoiceRepository,
    private val attemptRepository: FiscalInvoiceAttemptRepository,
    private val outboxRepository: BillingOutboxRepository,
    private val sequenceRepository: VoucherSequenceRepository,
    private val fiscalAuthorityPort: FiscalAuthorityPort,
    private val issuerSettings: BillingIssuerSettings,
    private val preflightService: FiscalInvoicePreflightService
) {

    fun createInvoiceForCharge(
        charge: BillingCharge,
        profile: BillingProfile,
        idempotencyKey: String,
        issueDate: LocalDate,
        amountTreatment: FiscalAmountTreatment
    ): FiscalInvoiceDetailDto {
        validateIdempotencyKey(idempotencyKey)
        if (profile.status != BillingProfileStatus.READY) {
            throw ValidationException("Billing profile ${profile.id} is incomplete")
        }
        val chargeId = requireNotNull(charge.id)
        val fingerprint = BillingFingerprint.chargeInvoice(
            chargeId = chargeId,
            profileId = requireNotNull(profile.id),
            issueDate = issueDate,
            amountTreatment = amountTreatment,
            profileVersion = profile.version
        )
        invoiceRepository.findByChargeId(chargeId).orElse(null)?.let { existing ->
            if (existing.creationKey == idempotencyKey && existing.requestFingerprint == fingerprint) {
                return detail(existing)
            }
            throw ResourceConflictException("Charge $chargeId already has a fiscal invoice")
        }

        val concept = profile.defaultFiscalConcept
        val serviceFrom = if (concept == 1) null else charge.serviceFrom ?: charge.dueDate
        val serviceTo = if (concept == 1) null else charge.serviceTo ?: charge.dueDate
        val amount = money(charge.amount)
        val now = LocalDateTime.now()
        val draft = FiscalInvoice(
            charge = charge,
            creationKey = idempotencyKey,
            requestFingerprint = fingerprint,
            status = FiscalInvoiceStatus.DRAFT,
            issuerCuit = issuerSettings.cuit,
            pointOfSale = issuerSettings.pointOfSale,
            voucherType = requireNotNull(profile.defaultVoucherType),
            concept = concept,
            receiverName = profile.receiverName.trim(),
            receiverAddress = requireNotNull(profile.receiverAddress).trim(),
            receiverDocumentType = requireNotNull(profile.receiverDocumentType),
            receiverDocumentNumber = requireNotNull(profile.receiverDocumentNumber),
            receiverVatConditionId = requireNotNull(profile.receiverVatConditionId),
            issueDate = issueDate,
            serviceFrom = serviceFrom,
            serviceTo = serviceTo,
            paymentDueDate = if (concept == 1) null else charge.dueDate,
            currency = profile.fiscalCurrency,
            exchangeRate = BigDecimal.ONE.setScale(EXCHANGE_RATE_SCALE, RoundingMode.HALF_EVEN),
            totalAmount = amount,
            nonTaxedAmount = if (amountTreatment == FiscalAmountTreatment.NON_TAXED) amount else BigDecimal.ZERO.setScale(MONEY_SCALE),
            netAmount = BigDecimal.ZERO.setScale(MONEY_SCALE),
            exemptAmount = if (amountTreatment == FiscalAmountTreatment.EXEMPT) amount else BigDecimal.ZERO.setScale(MONEY_SCALE),
            vatAmount = BigDecimal.ZERO.setScale(MONEY_SCALE),
            otherTaxesAmount = BigDecimal.ZERO.setScale(MONEY_SCALE),
            createdAt = now,
            updatedAt = now
        )
        val preflightErrors = preflightService.evaluate(draft)
        return detail(
            invoiceRepository.save(
                draft.copy(
                    status = if (preflightErrors.isEmpty()) FiscalInvoiceStatus.READY else FiscalInvoiceStatus.DRAFT,
                    preflightErrors = preflightErrors.toStorage()
                )
            )
        )
    }

    fun createInvoice(
        paymentId: Long,
        idempotencyKey: String,
        request: CreateFiscalInvoiceRequest
    ): FiscalInvoiceDetailDto {
        validateIdempotencyKey(idempotencyKey)
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow { ResourceNotFoundException("Payment $paymentId not found") }
        if (payment.status != PaymentStatus.PAID) {
            throw ResourceConflictException("Payment $paymentId must be PAID before creating its fiscal invoice")
        }
        if (!receiptRepository.findByPaymentId(paymentId).isPresent) {
            throw ResourceConflictException("Payment $paymentId has no payment receipt")
        }

        val fingerprint = BillingFingerprint.invoice(request)
        invoiceRepository.findByPaymentId(paymentId).orElse(null)?.let { existing ->
            if (existing.creationKey == idempotencyKey && existing.requestFingerprint == fingerprint) {
                return detail(existing)
            }
            throw ResourceConflictException("Payment $paymentId already has a fiscal invoice")
        }

        val now = LocalDateTime.now()
        val draft = FiscalInvoice(
            payment = payment,
            creationKey = idempotencyKey,
            requestFingerprint = fingerprint,
            status = FiscalInvoiceStatus.DRAFT,
            issuerCuit = issuerSettings.cuit,
            pointOfSale = issuerSettings.pointOfSale,
            voucherType = request.voucherType,
            concept = request.concept,
            receiverName = request.receiverName.trim(),
            receiverAddress = request.receiverAddress.trim(),
            receiverDocumentType = request.receiverDocumentType,
            receiverDocumentNumber = request.receiverDocumentNumber,
            receiverVatConditionId = request.receiverVatConditionId,
            issueDate = request.issueDate,
            serviceFrom = request.serviceFrom,
            serviceTo = request.serviceTo,
            paymentDueDate = request.paymentDueDate,
            currency = request.currency,
            exchangeRate = request.exchangeRate.setScale(EXCHANGE_RATE_SCALE, RoundingMode.HALF_EVEN),
            totalAmount = money(payment.amount),
            nonTaxedAmount = money(request.nonTaxedAmount),
            netAmount = money(request.netAmount),
            exemptAmount = money(request.exemptAmount),
            vatAmount = money(request.vatAmount),
            otherTaxesAmount = money(request.otherTaxesAmount),
            vatSubtotals = request.vatSubtotals.map {
                FiscalVatSubtotal(
                    id = it.id,
                    baseAmount = money(it.baseAmount),
                    amount = money(it.amount)
                )
            },
            taxes = request.taxes.map {
                FiscalOtherTax(
                    id = it.id,
                    description = it.description.trim(),
                    baseAmount = money(it.baseAmount),
                    rate = it.rate.setScale(EXCHANGE_RATE_SCALE, RoundingMode.HALF_EVEN),
                    amount = money(it.amount)
                )
            },
            createdAt = now,
            updatedAt = now
        )
        val preflightErrors = preflightService.evaluate(draft)
        val saved = invoiceRepository.save(
            draft.copy(
                status = if (preflightErrors.isEmpty()) FiscalInvoiceStatus.READY else FiscalInvoiceStatus.DRAFT,
                preflightErrors = preflightErrors.toStorage()
            )
        )
        return detail(saved)
    }

    fun queueAuthorization(invoiceId: Long, idempotencyKey: String): FiscalInvoiceDetailDto {
        validateIdempotencyKey(idempotencyKey)
        val invoice = invoiceRepository.findByIdForUpdate(invoiceId)
            .orElseThrow { ResourceNotFoundException("Fiscal invoice $invoiceId not found") }

        if (invoice.authorizationKey != null) {
            if (invoice.authorizationKey == idempotencyKey) {
                return detail(invoice)
            }
            throw ResourceConflictException("Fiscal invoice $invoiceId was already queued with another key")
        }
        if (invoice.status != FiscalInvoiceStatus.READY) {
            throw ResourceConflictException("Fiscal invoice $invoiceId cannot be queued from status ${invoice.status}")
        }

        val preflightErrors = preflightService.evaluate(invoice)
        if (preflightErrors.isNotEmpty()) {
            throw ValidationException(
                message = "Fiscal invoice preflight failed",
                validationDetails = preflightErrors
            )
        }

        val now = LocalDateTime.now()
        val queued = invoiceRepository.save(
            invoice.copy(
                authorizationKey = idempotencyKey,
                status = FiscalInvoiceStatus.QUEUED,
                preflightErrors = null,
                updatedAt = now
            )
        )
        outboxRepository.save(
            BillingOutboxEvent(
                invoice = queued,
                status = BillingOutboxStatus.PENDING,
                nextAttemptAt = now,
                createdAt = now
            )
        )
        return detail(queued)
    }

    fun reconcile(invoiceId: Long): FiscalInvoiceDetailDto {
        val invoice = invoiceRepository.findByIdForUpdate(invoiceId)
            .orElseThrow { ResourceNotFoundException("Fiscal invoice $invoiceId not found") }
        if (invoice.status != FiscalInvoiceStatus.UNKNOWN) {
            throw ResourceConflictException("Only UNKNOWN invoices can be reconciled")
        }

        val voucher = AuthorizedVoucherKey(
            sequence = VoucherSequenceKey(
                issuerCuit = requireNotNull(invoice.issuerCuit),
                pointOfSale = requireNotNull(invoice.pointOfSale),
                voucherType = invoice.voucherType
            ),
            voucherNumber = requireNotNull(invoice.voucherNumber)
        )
        val requestedAt = LocalDateTime.now()
        val result = fiscalAuthorityPort.consult(voucher)
        val health = fiscalAuthorityPort.health()

        if (result == null) {
            attemptRepository.save(
                FiscalInvoiceAttempt(
                    invoice = invoice,
                    attemptNumber = nextAttemptNumber(invoiceId),
                    type = FiscalAttemptType.RECONCILIATION,
                    provider = health.provider,
                    environment = health.environment.name,
                    outcome = FiscalAttemptOutcome.UNKNOWN,
                    observations = "The provider did not return a definitive voucher",
                    requestedAt = requestedAt,
                    respondedAt = LocalDateTime.now()
                )
            )
            return detail(invoice)
        }

        val updated = applyResult(invoice, result)
        attemptRepository.save(result.toAttempt(updated, FiscalAttemptType.RECONCILIATION, nextAttemptNumber(invoiceId), health.provider, health.environment.name, requestedAt))
        if (result.status == FiscalAuthorizationStatus.APPROVED) {
            confirmSequence(updated)
            markOutboxProcessed(invoiceId)
        }
        return detail(invoiceRepository.save(updated))
    }

    @Transactional(readOnly = true)
    fun get(invoiceId: Long): FiscalInvoiceDetailDto = detail(
        invoiceRepository.findById(invoiceId)
            .orElseThrow { ResourceNotFoundException("Fiscal invoice $invoiceId not found") }
    )

    @Transactional(readOnly = true)
    fun list(status: FiscalInvoiceStatus?, page: Int, size: Int): PageResponse<FiscalInvoiceDto> {
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, 100),
            Sort.by(Sort.Direction.DESC, "createdAt")
        )
        val result = if (status == null) invoiceRepository.findAll(pageable) else invoiceRepository.findByStatus(status, pageable)
        return PageResponse(
            content = result.content.map { invoice -> invoice.toDto(outboxStatus(invoice)) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    private fun detail(invoice: FiscalInvoice): FiscalInvoiceDetailDto = FiscalInvoiceDetailDto(
        invoice = invoice.toDto(outboxStatus(invoice)),
        attempts = attemptRepository.findByInvoiceIdOrderByAttemptNumberAsc(requireNotNull(invoice.id)).map { it.toDto() }
    )

    private fun outboxStatus(invoice: FiscalInvoice): BillingOutboxStatus? = outboxRepository.findByInvoiceIdAndEventType(
        requireNotNull(invoice.id),
        BillingOutboxEventType.AUTHORIZE_INVOICE
    ).orElse(null)?.status

    private fun applyResult(invoice: FiscalInvoice, result: FiscalAuthorizationResult): FiscalInvoice {
        val now = LocalDateTime.now()
        return when (result.status) {
            FiscalAuthorizationStatus.APPROVED -> invoice.copy(
                status = if (result.observations.isEmpty()) {
                    FiscalInvoiceStatus.AUTHORIZED
                } else {
                    FiscalInvoiceStatus.AUTHORIZED_WITH_OBSERVATIONS
                },
                authorizationCode = result.authorizationCode,
                authorizationExpiresOn = result.authorizationExpiresOn,
                authorizedAt = result.processedAt,
                providerRequestId = result.providerRequestId,
                lastObservations = result.observations.map { "${it.code}: ${it.message}" }.toStorage(),
                lastErrors = null,
                updatedAt = now
            )
            FiscalAuthorizationStatus.REJECTED -> invoice.copy(
                status = FiscalInvoiceStatus.REJECTED,
                providerRequestId = result.providerRequestId,
                lastObservations = result.observations.map { "${it.code}: ${it.message}" }.toStorage(),
                lastErrors = result.errors.map { "${it.code}: ${it.message}" }.toStorage(),
                updatedAt = now
            )
            FiscalAuthorizationStatus.UNKNOWN -> invoice.copy(
                status = FiscalInvoiceStatus.UNKNOWN,
                providerRequestId = result.providerRequestId,
                lastObservations = result.observations.map { "${it.code}: ${it.message}" }.toStorage(),
                lastErrors = result.errors.map { "${it.code}: ${it.message}" }.toStorage(),
                updatedAt = now
            )
        }
    }

    private fun FiscalAuthorizationResult.toAttempt(
        invoice: FiscalInvoice,
        type: FiscalAttemptType,
        attemptNumber: Int,
        provider: String,
        environment: String,
        requestedAt: LocalDateTime
    ) = FiscalInvoiceAttempt(
        invoice = invoice,
        attemptNumber = attemptNumber,
        type = type,
        provider = provider,
        environment = environment,
        outcome = when (status) {
            FiscalAuthorizationStatus.APPROVED -> FiscalAttemptOutcome.APPROVED
            FiscalAuthorizationStatus.REJECTED -> FiscalAttemptOutcome.REJECTED
            FiscalAuthorizationStatus.UNKNOWN -> FiscalAttemptOutcome.UNKNOWN
        },
        providerRequestId = providerRequestId,
        observations = observations.map { "${it.code}: ${it.message}" }.toStorage(),
        errors = errors.map { "${it.code}: ${it.message}" }.toStorage(),
        requestedAt = requestedAt,
        respondedAt = processedAt
    )

    private fun confirmSequence(invoice: FiscalInvoice) {
        val issuerCuit = requireNotNull(invoice.issuerCuit)
        val pointOfSale = requireNotNull(invoice.pointOfSale)
        val voucherNumber = requireNotNull(invoice.voucherNumber)
        sequenceRepository.ensureExists(issuerCuit, pointOfSale, invoice.voucherType)
        val sequence = sequenceRepository.findForUpdate(issuerCuit, pointOfSale, invoice.voucherType)
            .orElseThrow { IllegalStateException("Voucher sequence could not be initialized") }
        if (voucherNumber > sequence.lastConfirmedNumber) {
            sequenceRepository.save(
                sequence.copy(lastConfirmedNumber = voucherNumber, updatedAt = LocalDateTime.now())
            )
        }
    }

    private fun markOutboxProcessed(invoiceId: Long) {
        val event = outboxRepository.findByInvoiceIdAndEventType(invoiceId, BillingOutboxEventType.AUTHORIZE_INVOICE)
            .orElse(null) ?: return
        outboxRepository.save(
            event.copy(
                status = BillingOutboxStatus.PROCESSED,
                processedAt = LocalDateTime.now(),
                lastError = null
            )
        )
    }

    private fun nextAttemptNumber(invoiceId: Long): Int = attemptRepository.countByInvoiceId(invoiceId).toInt() + 1

    private fun money(value: BigDecimal): BigDecimal = value.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN)

    private fun validateIdempotencyKey(key: String) {
        if (key.isBlank() || key.length > 128) {
            throw ValidationException("Idempotency-Key is required and must have at most 128 characters")
        }
    }

    private companion object {
        const val MONEY_SCALE = 2
        const val EXCHANGE_RATE_SCALE = 6
    }
}
