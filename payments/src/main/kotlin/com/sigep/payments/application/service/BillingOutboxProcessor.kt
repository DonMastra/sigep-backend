package com.sigep.payments.application.service

import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import com.sigep.payments.application.gateway.FiscalAuthorizationResult
import com.sigep.payments.application.gateway.FiscalAuthorizationStatus
import com.sigep.payments.application.gateway.FiscalOtherTaxRequest
import com.sigep.payments.application.gateway.FiscalPreDispatchException
import com.sigep.payments.application.gateway.FiscalVatSubtotalRequest
import com.sigep.payments.application.gateway.VoucherSequenceKey
import com.sigep.payments.domain.model.BillingOutboxStatus
import com.sigep.payments.domain.model.FiscalAttemptOutcome
import com.sigep.payments.domain.model.FiscalAttemptType
import com.sigep.payments.domain.model.FiscalInvoice
import com.sigep.payments.domain.model.FiscalInvoiceAttempt
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.payments.domain.repository.BillingOutboxRepository
import com.sigep.payments.domain.repository.FiscalInvoiceAttemptRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import com.sigep.payments.domain.repository.VoucherSequenceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.math.max

@Service
class BillingOutboxProcessor(
    private val outboxRepository: BillingOutboxRepository,
    private val invoiceRepository: FiscalInvoiceRepository,
    private val attemptRepository: FiscalInvoiceAttemptRepository,
    private val sequenceRepository: VoucherSequenceRepository,
    private val fiscalAuthorityPort: FiscalAuthorityPort,
    private val preflightService: FiscalInvoicePreflightService
) {

    private val logger = LoggerFactory.getLogger(BillingOutboxProcessor::class.java)

    @Transactional
    fun process(eventId: Long) {
        val event = outboxRepository.findByIdForUpdate(eventId).orElse(null) ?: return
        if (event.status !in setOf(BillingOutboxStatus.PENDING, BillingOutboxStatus.FAILED)) {
            return
        }

        val invoiceId = requireNotNull(event.invoice.id)
        val invoice = invoiceRepository.findByIdForUpdate(invoiceId).orElse(null) ?: run {
            outboxRepository.save(
                event.copy(
                    status = BillingOutboxStatus.FAILED,
                    attempts = event.attempts + 1,
                    lastError = "Invoice $invoiceId no longer exists"
                )
            )
            return
        }
        if (invoice.status != FiscalInvoiceStatus.QUEUED) {
            outboxRepository.save(event.copy(status = BillingOutboxStatus.PROCESSED, processedAt = LocalDateTime.now()))
            return
        }

        val preflightErrors = preflightService.evaluate(invoice)
        if (preflightErrors.isNotEmpty()) {
            invoiceRepository.save(
                invoice.copy(
                    status = FiscalInvoiceStatus.DRAFT,
                    preflightErrors = preflightErrors.toStorage(),
                    updatedAt = LocalDateTime.now()
                )
            )
            outboxRepository.save(
                event.copy(
                    status = BillingOutboxStatus.FAILED,
                    attempts = event.attempts + 1,
                    lastError = "Fiscal preflight failed"
                )
            )
            return
        }

        val sequenceKey = VoucherSequenceKey(
            issuerCuit = requireNotNull(invoice.issuerCuit),
            pointOfSale = requireNotNull(invoice.pointOfSale),
            voucherType = invoice.voucherType
        )
        sequenceRepository.ensureExists(sequenceKey.issuerCuit, sequenceKey.pointOfSale, sequenceKey.voucherType)
        var sequence = sequenceRepository.findForUpdate(
            sequenceKey.issuerCuit,
            sequenceKey.pointOfSale,
            sequenceKey.voucherType
        ).orElseThrow { IllegalStateException("Voucher sequence could not be initialized") }

        val providerLast = try {
            fiscalAuthorityPort.lastAuthorized(sequenceKey)
        } catch (exception: Exception) {
            val attempts = event.attempts + 1
            val retryAt = LocalDateTime.now().plusSeconds(preDispatchBackoffSeconds(attempts, eventId))
            logger.warn(
                "Fiscal pre-dispatch check failed for invoice {}; retry scheduled at {} ({})",
                invoiceId,
                retryAt,
                exception.javaClass.simpleName
            )
            outboxRepository.save(
                event.copy(
                    status = BillingOutboxStatus.PENDING,
                    attempts = attempts,
                    nextAttemptAt = retryAt,
                    lastError = "Pre-dispatch provider failure: ${exception.javaClass.simpleName}"
                )
            )
            return
        }
        if (providerLast > sequence.lastConfirmedNumber) {
            sequence = sequenceRepository.save(
                sequence.copy(lastConfirmedNumber = providerLast, updatedAt = LocalDateTime.now())
            )
        }
        val voucherNumber = max(sequence.lastConfirmedNumber, providerLast) + 1
        val now = LocalDateTime.now()
        val authorizing = invoiceRepository.save(
            invoice.copy(
                status = FiscalInvoiceStatus.AUTHORIZING,
                voucherNumber = voucherNumber,
                updatedAt = now
            )
        )
        val health = fiscalAuthorityPort.health()
        val attempt = attemptRepository.save(
            FiscalInvoiceAttempt(
                invoice = authorizing,
                attemptNumber = attemptRepository.countByInvoiceId(invoiceId).toInt() + 1,
                type = FiscalAttemptType.AUTHORIZATION,
                provider = health.provider,
                environment = health.environment.name,
                outcome = FiscalAttemptOutcome.PROCESSING,
                requestedAt = now
            )
        )
        val processingEvent = outboxRepository.save(
            event.copy(
                status = BillingOutboxStatus.PROCESSING,
                attempts = event.attempts + 1,
                lastError = null
            )
        )

        try {
            val result = fiscalAuthorityPort.authorize(authorizing.toAuthorizationRequest(sequenceKey, voucherNumber))
            val updatedInvoice = applyResult(authorizing, result)
            invoiceRepository.save(updatedInvoice)
            attemptRepository.save(attempt.withResult(result, updatedInvoice))

            if (result.status == FiscalAuthorizationStatus.APPROVED) {
                sequenceRepository.save(
                    sequence.copy(lastConfirmedNumber = voucherNumber, updatedAt = LocalDateTime.now())
                )
            }
            outboxRepository.save(
                processingEvent.copy(
                    status = if (result.status == FiscalAuthorizationStatus.UNKNOWN) {
                        BillingOutboxStatus.WAITING_RECONCILIATION
                    } else {
                        BillingOutboxStatus.PROCESSED
                    },
                    processedAt = if (result.status == FiscalAuthorizationStatus.UNKNOWN) null else LocalDateTime.now()
                )
            )
        } catch (exception: FiscalPreDispatchException) {
            val message = exception.message?.take(MAX_ERROR_LENGTH) ?: exception.javaClass.simpleName
            val retryAt = LocalDateTime.now().plusSeconds(
                preDispatchBackoffSeconds(processingEvent.attempts, eventId)
            )
            logger.warn(
                "Fiscal authorization for invoice {} was rejected before dispatch; retry scheduled at {}",
                invoiceId,
                retryAt
            )
            invoiceRepository.save(
                authorizing.copy(
                    status = FiscalInvoiceStatus.QUEUED,
                    lastErrors = "PRE_DISPATCH_FAILURE: $message",
                    updatedAt = LocalDateTime.now()
                )
            )
            attemptRepository.save(
                attempt.copy(
                    outcome = FiscalAttemptOutcome.FAILED,
                    errors = "PRE_DISPATCH_FAILURE: $message",
                    respondedAt = LocalDateTime.now()
                )
            )
            outboxRepository.save(
                processingEvent.copy(
                    status = BillingOutboxStatus.PENDING,
                    nextAttemptAt = retryAt,
                    lastError = message
                )
            )
        } catch (exception: Exception) {
            val message = exception.message?.take(MAX_ERROR_LENGTH) ?: exception.javaClass.simpleName
            logger.warn(
                "Fiscal authorization for invoice {} ended in an ambiguous state ({})",
                invoiceId,
                exception.javaClass.simpleName
            )
            invoiceRepository.save(
                authorizing.copy(
                    status = FiscalInvoiceStatus.UNKNOWN,
                    lastErrors = "AMBIGUOUS_PROVIDER_FAILURE: $message",
                    updatedAt = LocalDateTime.now()
                )
            )
            attemptRepository.save(
                attempt.copy(
                    outcome = FiscalAttemptOutcome.UNKNOWN,
                    errors = "AMBIGUOUS_PROVIDER_FAILURE: $message",
                    respondedAt = LocalDateTime.now()
                )
            )
            outboxRepository.save(
                processingEvent.copy(
                    status = BillingOutboxStatus.WAITING_RECONCILIATION,
                    lastError = message
                )
            )
        }
    }

    private fun FiscalInvoice.toAuthorizationRequest(
        sequence: VoucherSequenceKey,
        voucherNumber: Long
    ) = FiscalAuthorizationRequest(
        invoiceId = requireNotNull(id),
        idempotencyKey = requireNotNull(authorizationKey),
        sequence = sequence,
        voucherNumber = voucherNumber,
        concept = concept,
        receiverDocumentType = receiverDocumentType,
        receiverDocumentNumber = receiverDocumentNumber,
        receiverVatConditionId = receiverVatConditionId,
        issueDate = issueDate,
        serviceFrom = serviceFrom,
        serviceTo = serviceTo,
        paymentDueDate = paymentDueDate,
        currency = currency,
        exchangeRate = exchangeRate,
        totalAmount = totalAmount,
        nonTaxedAmount = nonTaxedAmount,
        netAmount = netAmount,
        exemptAmount = exemptAmount,
        vatAmount = vatAmount,
        otherTaxesAmount = otherTaxesAmount,
        vatSubtotals = vatSubtotals.map { FiscalVatSubtotalRequest(it.id, it.baseAmount, it.amount) },
        taxes = taxes.map {
            FiscalOtherTaxRequest(it.id, it.description, it.baseAmount, it.rate, it.amount)
        }
    )

    private fun applyResult(invoice: FiscalInvoice, result: FiscalAuthorizationResult): FiscalInvoice = when (result.status) {
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
            updatedAt = LocalDateTime.now()
        )
        FiscalAuthorizationStatus.REJECTED -> invoice.copy(
            status = FiscalInvoiceStatus.REJECTED,
            providerRequestId = result.providerRequestId,
            lastObservations = result.observations.map { "${it.code}: ${it.message}" }.toStorage(),
            lastErrors = result.errors.map { "${it.code}: ${it.message}" }.toStorage(),
            updatedAt = LocalDateTime.now()
        )
        FiscalAuthorizationStatus.UNKNOWN -> invoice.copy(
            status = FiscalInvoiceStatus.UNKNOWN,
            providerRequestId = result.providerRequestId,
            lastObservations = result.observations.map { "${it.code}: ${it.message}" }.toStorage(),
            lastErrors = result.errors.map { "${it.code}: ${it.message}" }.toStorage(),
            updatedAt = LocalDateTime.now()
        )
    }

    private fun FiscalInvoiceAttempt.withResult(
        result: FiscalAuthorizationResult,
        updatedInvoice: FiscalInvoice
    ) = copy(
        invoice = updatedInvoice,
        outcome = when (result.status) {
            FiscalAuthorizationStatus.APPROVED -> FiscalAttemptOutcome.APPROVED
            FiscalAuthorizationStatus.REJECTED -> FiscalAttemptOutcome.REJECTED
            FiscalAuthorizationStatus.UNKNOWN -> FiscalAttemptOutcome.UNKNOWN
        },
        providerRequestId = result.providerRequestId,
        observations = result.observations.map { "${it.code}: ${it.message}" }.toStorage(),
        errors = result.errors.map { "${it.code}: ${it.message}" }.toStorage(),
        respondedAt = result.processedAt
    )

    private companion object {
        const val MAX_ERROR_LENGTH = 1000
        const val BASE_RETRY_SECONDS = 15L
        const val MAX_RETRY_SECONDS = 15L * 60

        fun preDispatchBackoffSeconds(attempts: Int, eventId: Long): Long {
            val exponent = (attempts - 1).coerceIn(0, 6)
            val exponential = (BASE_RETRY_SECONDS shl exponent).coerceAtMost(MAX_RETRY_SECONDS)
            val deterministicJitter = eventId.mod(7).toLong()
            return (exponential + deterministicJitter).coerceAtMost(MAX_RETRY_SECONDS)
        }
    }
}
