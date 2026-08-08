package com.sigep.payments.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.common.application.service.BillingChargeCommand
import com.sigep.common.application.service.BillingChargeInfo
import com.sigep.common.application.service.BillingChargeProvider
import com.sigep.common.application.service.BillingChargeSettlement
import com.sigep.common.application.service.BillingChargeSettlementObserver
import com.sigep.payments.application.dto.BillingChargePaymentDto
import com.sigep.payments.application.dto.BillingChargeDto
import com.sigep.payments.application.dto.BillingProfileDto
import com.sigep.payments.application.dto.BillingRunDto
import com.sigep.payments.application.dto.BillingRunItemDto
import com.sigep.payments.application.dto.BillingRunPreviewDto
import com.sigep.payments.application.dto.BillingRunPreviewItemDto
import com.sigep.payments.application.dto.ChargePaymentResultDto
import com.sigep.payments.application.dto.CreatePaymentRequest
import com.sigep.payments.application.dto.PrepareBillingRunRequest
import com.sigep.payments.application.dto.RegisterChargePaymentRequest
import com.sigep.payments.application.dto.RectifyFiscalDecisionRequest
import com.sigep.payments.application.dto.UpdateBillingProfileRequest
import com.sigep.payments.domain.model.BillingAccount
import com.sigep.payments.domain.model.BillingCharge
import com.sigep.payments.domain.model.BillingChargeStatus
import com.sigep.payments.domain.model.BillingChargeFiscalDecision
import com.sigep.payments.domain.model.BillingChargeFiscalDisposition
import com.sigep.payments.domain.model.FiscalClosure
import com.sigep.payments.domain.model.AutomaticDebitInstructionStatus
import com.sigep.payments.domain.model.AutomaticDebitMandateStatus
import com.sigep.payments.domain.model.AutomaticDebitScope
import com.sigep.payments.domain.model.BillingCollectionChannel
import com.sigep.payments.domain.model.BillingChargeAdjustmentSource
import com.sigep.payments.domain.model.BillingProfile
import com.sigep.payments.domain.model.BillingProfileStatus
import com.sigep.payments.domain.model.BillingRun
import com.sigep.payments.domain.model.BillingRunItem
import com.sigep.payments.domain.model.BillingSelectionMode
import com.sigep.payments.domain.model.PaymentAllocation
import com.sigep.payments.domain.model.PaymentStatus
import com.sigep.payments.domain.model.PaymentMethod
import com.sigep.payments.domain.repository.BillingAccountRepository
import com.sigep.payments.domain.repository.BillingChargeRepository
import com.sigep.payments.domain.repository.BillingChargeFiscalDecisionRepository
import com.sigep.payments.domain.repository.AutomaticDebitInstructionRepository
import com.sigep.payments.domain.repository.AutomaticDebitMandateRepository
import com.sigep.payments.domain.repository.BillingProfileRepository
import com.sigep.payments.domain.repository.BillingRunItemRepository
import com.sigep.payments.domain.repository.BillingRunRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import com.sigep.payments.domain.repository.PaymentAllocationRepository
import com.sigep.payments.domain.repository.PaymentRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId

@Service
@Transactional
class BillingOperationsService(
    private val accountRepository: BillingAccountRepository,
    private val profileRepository: BillingProfileRepository,
    private val chargeRepository: BillingChargeRepository,
    private val fiscalDecisionRepository: BillingChargeFiscalDecisionRepository,
    private val automaticDebitInstructionRepository: AutomaticDebitInstructionRepository,
    private val automaticDebitMandateRepository: AutomaticDebitMandateRepository,
    private val allocationRepository: PaymentAllocationRepository,
    private val paymentRepository: PaymentRepository,
    private val invoiceRepository: FiscalInvoiceRepository,
    private val runRepository: BillingRunRepository,
    private val runItemRepository: BillingRunItemRepository,
    private val paymentService: PaymentApplicationService,
    private val billingService: BillingApplicationService,
    private val lateFeeService: BillingLateFeeService,
    private val settlementObservers: List<BillingChargeSettlementObserver>,
    @Value("\${billing.fiscal-exclusion.enabled:false}")
    private val fiscalExclusionEnabled: Boolean
) : BillingChargeProvider {

    override fun upsertCharge(command: BillingChargeCommand): BillingChargeInfo {
        val now = LocalDateTime.now()
        val account = accountRepository.findByGuardianUserId(command.guardianUserId).orElseGet {
            accountRepository.save(
                BillingAccount(
                    guardianUserId = command.guardianUserId,
                    displayName = command.receiverName.trim(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        ensureProfile(account, command, now)

        val existing = chargeRepository.findBySourceTypeAndSourceId(command.sourceType, command.sourceId).orElse(null)
        val charge = if (existing == null) {
            chargeRepository.save(
                BillingCharge(
                    account = account,
                    studentId = command.studentId,
                    studentName = command.studentName.trim(),
                    sourceType = command.sourceType,
                    sourceId = command.sourceId,
                    concept = command.concept,
                    description = command.description.trim(),
                    baseAmount = money(command.amount),
                    amount = money(command.amount),
                    currency = command.currency,
                    dueDate = command.dueDate,
                    serviceFrom = command.serviceFrom,
                    serviceTo = command.serviceTo,
                    lateFeePercentage = command.lateFeePercentage,
                    lateFeeEligible = command.lateFeeEligible,
                    automaticDebitEligible = command.automaticDebitEligible,
                    collectionChannel = resolveCollectionChannel(
                        accountId = requireNotNull(account.id),
                        concept = command.concept,
                        dueDate = command.dueDate,
                        eligible = command.automaticDebitEligible
                    ),
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            chargeRepository.save(
                existing.copy(
                    account = account,
                    studentId = command.studentId ?: existing.studentId,
                    studentName = command.studentName.trim(),
                    concept = command.concept,
                    description = command.description.trim(),
                    currency = command.currency,
                    serviceFrom = command.serviceFrom,
                    serviceTo = command.serviceTo,
                    updatedAt = now
                )
            )
        }
        return BillingChargeInfo(
            id = requireNotNull(charge.id),
            sourceType = charge.sourceType,
            sourceId = charge.sourceId,
            status = charge.status.name
        )
    }

    override fun cancelCharge(sourceType: String, sourceId: Long) {
        val charge = chargeRepository.findBySourceTypeAndSourceId(sourceType, sourceId).orElse(null) ?: return
        if (charge.status == BillingChargeStatus.OPEN) {
            chargeRepository.save(charge.copy(status = BillingChargeStatus.CANCELLED, updatedAt = LocalDateTime.now()))
        }
    }

    @Transactional(readOnly = true)
    fun listCharges(
        status: BillingChargeStatus?,
        studentId: Long?,
        studentQuery: String?,
        profileStatus: BillingProfileStatus?,
        fiscalDisposition: BillingChargeFiscalDisposition?,
        overdue: Boolean?,
        automaticDebitStatus: AutomaticDebitInstructionStatus?,
        collectionChannel: BillingCollectionChannel?,
        page: Int,
        size: Int
    ): PageResponse<BillingChargeDto> {
        val result = chargeRepository.findByFilters(
            status,
            studentId,
            normalizeStudentQuery(studentQuery).orEmpty(),
            profileStatus,
            fiscalDisposition,
            overdue,
            LocalDate.now(BUSINESS_ZONE),
            automaticDebitStatus,
            collectionChannel,
            PageRequest.of(
                page.coerceAtLeast(0),
                size.coerceIn(1, 100),
                Sort.by(Sort.Order.asc("dueDate"), Sort.Order.asc("studentName"))
            )
        )
        return PageResponse(
            content = result.content.map(::toDto),
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun getCharge(chargeId: Long): BillingChargeDto = chargeRepository.findById(chargeId)
        .orElseThrow { ResourceNotFoundException("Billing charge $chargeId not found") }
        .let(::toDto)

    @Transactional(readOnly = true)
    fun getProfile(accountId: Long): BillingProfileDto = profileRepository.findByAccountId(accountId)
        .orElseThrow { ResourceNotFoundException("Billing profile for account $accountId not found") }
        .toDto()

    fun updateProfile(accountId: Long, request: UpdateBillingProfileRequest, adminId: Long): BillingProfileDto {
        val current = profileRepository.findByAccountId(accountId)
            .orElseThrow { ResourceNotFoundException("Billing profile for account $accountId not found") }
        val now = LocalDateTime.now()
        val updated = current.copy(
            receiverName = request.receiverName.trim(),
            receiverAddress = request.receiverAddress.trim(),
            receiverDocumentType = request.receiverDocumentType,
            receiverDocumentNumber = request.receiverDocumentNumber.trim(),
            receiverVatConditionId = request.receiverVatConditionId,
            defaultVoucherType = request.defaultVoucherType,
            defaultFiscalConcept = request.defaultFiscalConcept,
            fiscalCurrency = request.fiscalCurrency,
            rg5866Applicable = false,
            status = BillingProfileStatus.READY,
            updatedBy = adminId,
            updatedAt = now
        )
        return profileRepository.save(updated).toDto()
    }

    fun preview(request: PrepareBillingRunRequest): BillingRunPreviewDto {
        val charges = resolveCharges(request).map { charge ->
            lateFeeService.applyIfDue(
                requireNotNull(charge.id),
                BillingChargeAdjustmentSource.BILLING_RUN
            )
        }
        val items = charges.map { charge ->
            BillingRunPreviewItemDto(charge = toDto(charge), blockers = blockers(charge))
        }
        return BillingRunPreviewDto(
            selectedCount = items.size,
            readyCount = items.count { it.blockers.isEmpty() },
            blockedCount = items.count { it.blockers.isNotEmpty() },
            totalAmount = money(charges.fold(BigDecimal.ZERO) { total, charge -> total + charge.amount }),
            items = items
        )
    }

    fun createRun(
        idempotencyKey: String,
        request: PrepareBillingRunRequest,
        adminId: Long
    ): BillingRunDto {
        validateIdempotencyKey(idempotencyKey)
        val fingerprint = runFingerprint(request)
        runRepository.findByIdempotencyKey(idempotencyKey).orElse(null)?.let {
            if (it.requestFingerprint == fingerprint) {
                return toDto(it)
            }
            throw ResourceConflictException("Idempotency-Key was already used with another billing run payload")
        }

        val charges = resolveCharges(request).map { charge ->
            lateFeeService.applyIfDue(
                requireNotNull(charge.id),
                BillingChargeAdjustmentSource.BILLING_RUN,
                adminId
            )
        }
        if (charges.isEmpty()) {
            throw ValidationException("The billing run has no selected charges")
        }
        val blockingItems = charges.map { it to blockers(it) }.filter { it.second.isNotEmpty() }
        if (blockingItems.isNotEmpty()) {
            val details = blockingItems.flatMap { (charge, errors) ->
                errors.map { error -> "Cargo ${charge.id}: $error" }
            }
            throw ValidationException("Billing run preview has blockers", validationDetails = details)
        }

        val run = runRepository.save(
            BillingRun(
                idempotencyKey = idempotencyKey,
                requestFingerprint = fingerprint,
                selectionMode = request.selectionMode,
                amountTreatment = request.amountTreatment,
                requestedBy = adminId,
                issueDate = request.issueDate,
                selectedCount = charges.size,
                createdCount = charges.size
            )
        )
        charges.forEach { charge ->
            val profile = requireProfile(charge.account.id!!)
            val invoiceDetail = billingService.createInvoiceForCharge(
                charge = charge,
                profile = profile,
                idempotencyKey = "$idempotencyKey:charge:${charge.id}".take(128),
                issueDate = request.issueDate,
                amountTreatment = request.amountTreatment
            )
            val invoice = invoiceRepository.getReferenceById(invoiceDetail.invoice.id)
            runItemRepository.save(BillingRunItem(run = run, charge = charge, invoice = invoice))
        }
        return toDto(run)
    }

    @Transactional(readOnly = true)
    fun getRun(runId: Long): BillingRunDto = runRepository.findById(runId)
        .orElseThrow { ResourceNotFoundException("Billing run $runId not found") }
        .let(::toDto)

    fun registerChargePayment(
        chargeId: Long,
        idempotencyKey: String,
        request: RegisterChargePaymentRequest,
        adminId: Long
    ): ChargePaymentResultDto = registerChargePaymentInternal(
        chargeId,
        idempotencyKey,
        request,
        adminId,
        allowAutomaticDebit = false
    )

    fun registerAutomaticDebitPayment(
        chargeId: Long,
        idempotencyKey: String,
        request: RegisterChargePaymentRequest,
        actorId: Long
    ): ChargePaymentResultDto = registerChargePaymentInternal(
        chargeId,
        idempotencyKey,
        request.copy(fiscalClosure = FiscalClosure.KEEP_PENDING, fiscalReason = null),
        actorId,
        allowAutomaticDebit = true
    )

    fun reverseAutomaticDebitPayment(paymentId: Long): BillingChargeDto {
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow { ResourceNotFoundException("Payment $paymentId not found") }
        if (payment.status != PaymentStatus.PAID || payment.paymentMethod != PaymentMethod.AUTOMATIC_DEBIT) {
            throw ResourceConflictException("Only a confirmed automatic debit payment can be reversed")
        }
        val allocation = allocationRepository.findByPaymentId(paymentId).singleOrNull()
            ?: throw ResourceConflictException("Automatic debit payment $paymentId must have exactly one allocation")
        val chargeId = requireNotNull(allocation.charge.id)
        val charge = chargeRepository.findByIdForUpdate(chargeId)
            .orElseThrow { ResourceNotFoundException("Billing charge $chargeId not found") }
        val now = LocalDateTime.now()
        paymentRepository.save(payment.copy(status = PaymentStatus.REVERSED, updatedAt = now))
        val paidAmount = money((charge.paidAmount - allocation.amount).max(BigDecimal.ZERO))
        val status = when {
            paidAmount == charge.amount -> BillingChargeStatus.PAID
            paidAmount > BigDecimal.ZERO -> BillingChargeStatus.PARTIALLY_PAID
            else -> BillingChargeStatus.OPEN
        }
        val reopened = chargeRepository.save(
            charge.copy(
                paidAmount = paidAmount,
                status = status,
                paidAt = if (status == BillingChargeStatus.PAID) charge.paidAt else null,
                updatedAt = now
            )
        )
        notifySettlement(reopened, paymentId)
        return toDto(reopened)
    }

    fun rectifyFiscalDecision(
        chargeId: Long,
        idempotencyKey: String,
        request: RectifyFiscalDecisionRequest,
        adminId: Long
    ): BillingChargeDto {
        validateIdempotencyKey(idempotencyKey)
        val charge = chargeRepository.findByIdForUpdate(chargeId)
            .orElseThrow { ResourceNotFoundException("Billing charge $chargeId not found") }
        fiscalDecisionRepository.findByIdempotencyKey(idempotencyKey).orElse(null)?.let {
            return toDto(charge)
        }
        if (charge.status != BillingChargeStatus.PAID) {
            throw ResourceConflictException("Fiscal treatment can only be rectified after the charge is fully paid")
        }
        if (request.reason.isBlank()) {
            throw ValidationException("A reason is required to rectify fiscal treatment")
        }
        if (request.decision == FiscalClosure.EXCLUDE_CHARGE) {
            if (!fiscalExclusionEnabled) {
                throw ValidationException("Fiscal exclusion is disabled in this environment")
            }
            if (invoiceRepository.findByChargeId(chargeId).isPresent) {
                throw ResourceConflictException("A charge with an invoice cannot be excluded from fiscal billing")
            }
        }
        val now = LocalDateTime.now()
        fiscalDecisionRepository.save(
            BillingChargeFiscalDecision(
                charge = charge,
                decision = request.decision,
                reason = request.reason.trim(),
                decidedBy = adminId,
                idempotencyKey = idempotencyKey,
                createdAt = now
            )
        )
        val updated = chargeRepository.save(
            charge.copy(
                fiscalDisposition = if (request.decision == FiscalClosure.EXCLUDE_CHARGE) {
                    BillingChargeFiscalDisposition.EXCLUDED
                } else {
                    BillingChargeFiscalDisposition.PENDING
                },
                updatedAt = now
            )
        )
        return toDto(updated)
    }

    private fun registerChargePaymentInternal(
        chargeId: Long,
        idempotencyKey: String,
        request: RegisterChargePaymentRequest,
        adminId: Long,
        allowAutomaticDebit: Boolean
    ): ChargePaymentResultDto {
        validateIdempotencyKey(idempotencyKey)
        val initiallyLocked = chargeRepository.findByIdForUpdate(chargeId)
            .orElseThrow { ResourceNotFoundException("Billing charge $chargeId not found") }
        paymentRepository.findByCreationKey("$idempotencyKey:create").orElse(null)?.let { payment ->
            val allocation = allocationRepository.findByPaymentId(requireNotNull(payment.id))
                .singleOrNull { it.charge.id == chargeId }
            if (allocation != null) {
                return ChargePaymentResultDto(
                    toDto(initiallyLocked),
                    paymentService.get(requireNotNull(payment.id))
                )
            }
            throw ResourceConflictException("Idempotency-Key was already used for another charge")
        }
        if (initiallyLocked.status !in setOf(BillingChargeStatus.OPEN, BillingChargeStatus.PARTIALLY_PAID)) {
            throw ResourceConflictException("Billing charge $chargeId cannot be paid from status ${initiallyLocked.status}")
        }
        if (!allowAutomaticDebit && request.confirmation.paymentMethod.name == "AUTOMATIC_DEBIT") {
            throw ValidationException("AUTOMATIC_DEBIT payments can only be created by a debit instruction")
        }
        if (!allowAutomaticDebit && automaticDebitInstructionRepository.existsByChargeIdAndStatusIn(
                chargeId,
                MANUAL_PAYMENT_BLOCKING_DEBIT_STATUSES
            )
        ) {
            throw ResourceConflictException("A debit instruction is in progress for billing charge $chargeId")
        }

        val charge = lateFeeService.applyIfDue(
            chargeId,
            if (allowAutomaticDebit) BillingChargeAdjustmentSource.AUTOMATIC_DEBIT
            else BillingChargeAdjustmentSource.PAYMENT,
            adminId
        )
        val paymentAmount = money(request.amount)
        val outstanding = money(charge.amount - charge.paidAmount)
        if (paymentAmount <= BigDecimal.ZERO) {
            throw ValidationException("Payment amount must be greater than zero")
        }
        if (paymentAmount > outstanding) {
            throw ValidationException("Payment amount cannot exceed outstanding balance $outstanding")
        }
        val settlesCharge = paymentAmount == outstanding
        if (!settlesCharge && request.fiscalClosure == FiscalClosure.EXCLUDE_CHARGE) {
            throw ValidationException("Partial payments must keep the charge pending for fiscal treatment")
        }
        val closure = if (settlesCharge) {
            request.fiscalClosure
                ?: throw ValidationException("fiscalClosure is required when the payment settles the charge")
        } else {
            FiscalClosure.KEEP_PENDING
        }
        if (closure == FiscalClosure.EXCLUDE_CHARGE) {
            if (!fiscalExclusionEnabled) {
                throw ValidationException("Fiscal exclusion is disabled in this environment")
            }
            if (request.fiscalReason.isNullOrBlank()) {
                throw ValidationException("A reason is required to exclude a charge from fiscal billing")
            }
            if (invoiceRepository.findByChargeId(chargeId).isPresent) {
                throw ResourceConflictException("A charge with an invoice cannot be excluded from fiscal billing")
            }
        }

        val created = paymentService.create(
            idempotencyKey = "$idempotencyKey:create",
            request = CreatePaymentRequest(
                studentId = charge.studentId,
                amount = paymentAmount,
                currency = charge.currency,
                concept = charge.description,
                dueDate = charge.dueDate,
                externalReference = request.externalReference,
                notes = request.notes
            ),
            initialPaymentDate = request.confirmation.paymentDate
        )
        val confirmed = paymentService.confirm(
            paymentId = created.payment.id,
            idempotencyKey = "$idempotencyKey:confirm",
            request = request.confirmation,
            adminId = adminId
        )
        val payment = paymentRepository.getReferenceById(confirmed.payment.id)
        allocationRepository.save(PaymentAllocation(payment = payment, charge = charge, amount = paymentAmount))
        val newPaidAmount = money(charge.paidAmount + paymentAmount)
        val newStatus = if (newPaidAmount == charge.amount) {
            BillingChargeStatus.PAID
        } else {
            BillingChargeStatus.PARTIALLY_PAID
        }
        val now = LocalDateTime.now()
        val paidCharge = chargeRepository.save(
            charge.copy(
                paidAmount = newPaidAmount,
                status = newStatus,
                fiscalDisposition = if (closure == FiscalClosure.EXCLUDE_CHARGE) {
                    BillingChargeFiscalDisposition.EXCLUDED
                } else {
                    charge.fiscalDisposition
                },
                paidAt = if (newStatus == BillingChargeStatus.PAID) now else null,
                updatedAt = now
            )
        )
        if (settlesCharge) {
            fiscalDecisionRepository.save(
                BillingChargeFiscalDecision(
                    charge = paidCharge,
                    decision = closure,
                    reason = request.fiscalReason?.trim()?.takeIf(String::isNotEmpty),
                    decidedBy = adminId,
                    idempotencyKey = "$idempotencyKey:fiscal",
                    createdAt = now
                )
            )
        }
        notifySettlement(paidCharge, confirmed.payment.id)
        return ChargePaymentResultDto(toDto(paidCharge), confirmed)
    }

    private fun ensureProfile(account: BillingAccount, command: BillingChargeCommand, now: LocalDateTime): BillingProfile {
        val existing = profileRepository.findByAccountId(requireNotNull(account.id)).orElse(null)
        if (existing == null) {
            return profileRepository.save(
                BillingProfile(
                    account = account,
                    receiverName = command.receiverName.trim(),
                    receiverAddress = command.receiverAddress?.trim()?.takeIf(String::isNotEmpty),
                    receiverDocumentNumber = command.receiverDocumentNumber?.filter(Char::isDigit)?.takeIf(String::isNotEmpty),
                    status = BillingProfileStatus.INCOMPLETE,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        if (existing.status == BillingProfileStatus.READY) {
            return existing
        }
        return profileRepository.save(
            existing.copy(
                receiverName = existing.receiverName.ifBlank { command.receiverName.trim() },
                receiverAddress = existing.receiverAddress ?: command.receiverAddress?.trim()?.takeIf(String::isNotEmpty),
                receiverDocumentNumber = existing.receiverDocumentNumber
                    ?: command.receiverDocumentNumber?.filter(Char::isDigit)?.takeIf(String::isNotEmpty),
                updatedAt = now
            )
        )
    }

    private fun resolveCharges(request: PrepareBillingRunRequest): List<BillingCharge> {
        val charges = when (request.selectionMode) {
            BillingSelectionMode.INDIVIDUAL -> {
                if (request.chargeIds.size != 1) {
                    throw ValidationException("INDIVIDUAL selection requires exactly one chargeId")
                }
                chargeRepository.findAllById(request.chargeIds)
            }
            BillingSelectionMode.SELECTED -> {
                if (request.chargeIds.isEmpty()) {
                    throw ValidationException("SELECTED selection requires at least one chargeId")
                }
                chargeRepository.findAllById(request.chargeIds.distinct())
            }
            BillingSelectionMode.FILTERED -> resolveFilteredCharges(request)
        }
        if (request.selectionMode != BillingSelectionMode.FILTERED && charges.size != request.chargeIds.distinct().size) {
            throw ResourceNotFoundException("One or more selected billing charges do not exist")
        }
        request.filters.collectionChannel?.let { expectedChannel ->
            if (charges.any { it.collectionChannel != expectedChannel }) {
                throw ValidationException("Selected charges must belong to the $expectedChannel collection channel")
            }
        }
        return charges.distinctBy { it.id }.sortedWith(compareBy<BillingCharge> { it.dueDate }.thenBy { it.id })
    }

    private fun resolveFilteredCharges(request: PrepareBillingRunRequest): List<BillingCharge> {
        val page = chargeRepository.findByFilters(
            request.filters.status,
            request.filters.studentId,
            normalizeStudentQuery(request.filters.studentQuery).orEmpty(),
            request.filters.profileStatus,
            request.filters.fiscalDisposition,
            request.filters.overdue,
            LocalDate.now(BUSINESS_ZONE),
            request.filters.automaticDebitStatus,
            request.filters.collectionChannel,
            PageRequest.of(0, MAX_RUN_ITEMS, Sort.by(Sort.Order.asc("dueDate"), Sort.Order.asc("id")))
        )
        if (page.totalElements > MAX_RUN_ITEMS) {
            throw ValidationException(
                "The filtered billing run exceeds the maximum of $MAX_RUN_ITEMS charges; narrow the filters"
            )
        }
        return page.content.filter { it.fiscalDisposition != BillingChargeFiscalDisposition.EXCLUDED }
    }

    private fun blockers(charge: BillingCharge): List<String> = buildList {
        if (charge.status == BillingChargeStatus.CANCELLED) add("El cargo esta cancelado")
        if (charge.fiscalDisposition == BillingChargeFiscalDisposition.EXCLUDED) {
            add("El cargo fue excluido de la facturacion fiscal")
        }
        if (charge.amount <= BigDecimal.ZERO) add("El monto debe ser positivo")
        if (invoiceRepository.findByChargeId(requireNotNull(charge.id)).isPresent) add("El cargo ya tiene una factura")
        if (
            charge.status != BillingChargeStatus.PAID &&
            charge.collectionChannel == BillingCollectionChannel.REGULAR &&
            charge.lateFeeEligible &&
            charge.lateFeePercentage > BigDecimal.ZERO &&
            charge.lateFeeAppliedAt == null
        ) {
            add("El cargo debe pagarse o alcanzar la instancia de recargo antes de facturarse")
        }
        addAll(requireProfile(requireNotNull(charge.account.id)).missingFields())
    }

    private fun requireProfile(accountId: Long): BillingProfile = profileRepository.findByAccountId(accountId)
        .orElseThrow { ResourceNotFoundException("Billing profile for account $accountId not found") }

    private fun toDto(charge: BillingCharge): BillingChargeDto {
        val chargeId = requireNotNull(charge.id)
        val profile = requireProfile(requireNotNull(charge.account.id))
        val invoice = invoiceRepository.findByChargeId(chargeId).orElse(null)
        val allocations = allocationRepository.findByChargeIdOrderByCreatedAtAsc(chargeId)
        val latestAllocation = allocations.lastOrNull()
        val automaticDebitStatus = automaticDebitInstructionRepository
            .findByChargeIdOrderByCreatedAtDesc(chargeId)
            .firstOrNull()
            ?.status
        return BillingChargeDto(
            id = chargeId,
            accountId = requireNotNull(charge.account.id),
            guardianUserId = charge.account.guardianUserId,
            studentId = charge.studentId,
            studentName = charge.studentName,
            sourceType = charge.sourceType,
            sourceId = charge.sourceId,
            concept = charge.concept,
            description = charge.description,
            baseAmount = charge.baseAmount,
            lateFeeAmount = money(charge.amount - charge.baseAmount),
            amount = charge.amount,
            paidAmount = charge.paidAmount,
            outstandingAmount = money((charge.amount - charge.paidAmount).max(BigDecimal.ZERO)),
            currency = charge.currency,
            dueDate = charge.dueDate,
            serviceFrom = charge.serviceFrom,
            serviceTo = charge.serviceTo,
            status = charge.status,
            overdue = charge.status !in setOf(BillingChargeStatus.PAID, BillingChargeStatus.CANCELLED) &&
                LocalDate.now(BUSINESS_ZONE).isAfter(charge.dueDate),
            lateFeePercentage = charge.lateFeePercentage,
            lateFeeEligible = charge.lateFeeEligible,
            automaticDebitEligible = charge.automaticDebitEligible,
            collectionChannel = charge.collectionChannel,
            automaticDebitEnrolled = automaticDebitMandateRepository
                .findByAccountIdAndIsDefaultTrueAndStatusIn(
                    requireNotNull(charge.account.id),
                    setOf(AutomaticDebitMandateStatus.ACTIVE)
                )
                .map { mandate -> !mandate.effectiveFrom.isAfter(charge.dueDate) }
                .orElse(false),
            fiscalDisposition = charge.fiscalDisposition,
            automaticDebitStatus = automaticDebitStatus,
            profile = profile.toDto(),
            invoiceId = invoice?.id,
            invoiceStatus = invoice?.status,
            paymentId = latestAllocation?.payment?.id,
            receiptNumber = latestAllocation?.payment?.receiptNumber,
            payments = allocations.map { allocation ->
                BillingChargePaymentDto(
                    paymentId = requireNotNull(allocation.payment.id),
                    amount = allocation.amount,
                    paymentDate = allocation.payment.paymentDate,
                    method = allocation.payment.paymentMethod,
                    status = allocation.payment.status,
                    receiptNumber = allocation.payment.receiptNumber
                )
            },
            createdAt = charge.createdAt,
            updatedAt = charge.updatedAt
        )
    }

    private fun notifySettlement(charge: BillingCharge, paymentId: Long?) {
        val settlement = BillingChargeSettlement(
            sourceType = charge.sourceType,
            sourceId = charge.sourceId,
            paymentId = paymentId,
            baseAmount = charge.baseAmount,
            lateFeeAmount = money(charge.amount - charge.baseAmount),
            paidAmount = charge.paidAmount,
            outstandingAmount = money((charge.amount - charge.paidAmount).max(BigDecimal.ZERO)),
            status = charge.status.name
        )
        settlementObservers.forEach { it.onChargeSettlementChanged(settlement) }
    }

    private fun BillingProfile.toDto() = BillingProfileDto(
        id = requireNotNull(id),
        accountId = requireNotNull(account.id),
        guardianUserId = account.guardianUserId,
        receiverName = receiverName,
        receiverAddress = receiverAddress,
        receiverDocumentType = receiverDocumentType,
        receiverDocumentNumber = receiverDocumentNumber,
        receiverVatConditionId = receiverVatConditionId,
        defaultVoucherType = defaultVoucherType,
        defaultFiscalConcept = defaultFiscalConcept,
        fiscalCurrency = fiscalCurrency,
        rg5866Applicable = rg5866Applicable,
        status = status,
        missingFields = missingFields(),
        updatedAt = updatedAt
    )

    private fun BillingProfile.missingFields(): List<String> = buildList {
        if (receiverName.isBlank()) add("Falta nombre o razon social")
        if (receiverAddress.isNullOrBlank()) add("Falta domicilio fiscal")
        if (receiverDocumentType == null || receiverDocumentType <= 0) add("Falta tipo de documento")
        if (receiverDocumentNumber?.matches(Regex("^[0-9]{1,20}$")) != true) add("Falta documento valido")
        if (receiverVatConditionId == null || receiverVatConditionId <= 0) add("Falta condicion IVA")
        if (defaultVoucherType == null || defaultVoucherType <= 0) add("Falta tipo de comprobante")
        if (defaultFiscalConcept !in 1..3) add("El concepto fiscal no es valido")
        if (!fiscalCurrency.matches(Regex("^[A-Z]{3}$"))) add("La moneda fiscal no es valida")
    }

    private fun toDto(run: BillingRun): BillingRunDto {
        val items = runItemRepository.findByRunIdOrderByIdAsc(requireNotNull(run.id)).map {
            BillingRunItemDto(
                chargeId = requireNotNull(it.charge.id),
                invoiceId = requireNotNull(it.invoice.id),
                invoiceStatus = it.invoice.status
            )
        }
        return BillingRunDto(
            id = requireNotNull(run.id),
            selectionMode = run.selectionMode,
            amountTreatment = run.amountTreatment,
            issueDate = run.issueDate,
            selectedCount = run.selectedCount,
            createdCount = run.createdCount,
            status = run.status,
            requestedBy = run.requestedBy,
            createdAt = run.createdAt,
            items = items
        )
    }

    private fun validateIdempotencyKey(key: String) {
        if (key.isBlank() || key.length > 120) {
            throw ValidationException("Idempotency-Key is required and must have at most 120 characters")
        }
    }

    private fun money(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_EVEN)

    private fun runFingerprint(request: PrepareBillingRunRequest): String {
        val value = listOf(
            request.selectionMode,
            request.chargeIds.distinct().sorted().joinToString(","),
            request.filters.status,
            request.filters.studentId,
            normalizeStudentQuery(request.filters.studentQuery),
            request.filters.profileStatus,
            request.filters.fiscalDisposition,
            request.filters.overdue,
            request.filters.automaticDebitStatus,
            request.filters.collectionChannel,
            request.issueDate,
            request.amountTreatment
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun resolveCollectionChannel(
        accountId: Long,
        concept: String,
        dueDate: LocalDate,
        eligible: Boolean
    ): BillingCollectionChannel {
        if (!eligible) return BillingCollectionChannel.REGULAR
        val mandate = automaticDebitMandateRepository.findByAccountIdAndIsDefaultTrueAndStatusIn(
            accountId,
            setOf(AutomaticDebitMandateStatus.ACTIVE)
        ).orElse(null) ?: return BillingCollectionChannel.REGULAR
        if (mandate.effectiveFrom.isAfter(dueDate)) return BillingCollectionChannel.REGULAR
        if (concept == "TUITION_ENROLLMENT" && mandate.scope != AutomaticDebitScope.INSTALLMENTS_AND_ENROLLMENT) {
            return BillingCollectionChannel.REGULAR
        }
        return BillingCollectionChannel.AUTOMATIC_DEBIT
    }

    private companion object {
        const val MAX_STUDENT_QUERY_LENGTH = 100
        const val MAX_RUN_ITEMS = 1000
        val BUSINESS_ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
        val MANUAL_PAYMENT_BLOCKING_DEBIT_STATUSES = setOf(
            AutomaticDebitInstructionStatus.READY_FOR_PROCESSING,
            AutomaticDebitInstructionStatus.SUBMITTED,
            AutomaticDebitInstructionStatus.UNKNOWN,
            AutomaticDebitInstructionStatus.ACCOUNTING_RESOLUTION_REQUIRED,
            AutomaticDebitInstructionStatus.CREDIT_NOTE_REQUIRED
        )
    }

    private fun normalizeStudentQuery(query: String?): String? {
        val normalized = query?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (normalized.length > MAX_STUDENT_QUERY_LENGTH) {
            throw ValidationException("Student query must have at most $MAX_STUDENT_QUERY_LENGTH characters")
        }
        return normalized
    }
}
