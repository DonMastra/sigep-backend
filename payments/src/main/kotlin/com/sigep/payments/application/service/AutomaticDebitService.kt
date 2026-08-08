package com.sigep.payments.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.payments.application.dto.AutomaticDebitInstructionDto
import com.sigep.payments.application.dto.AutomaticDebitMandateDto
import com.sigep.payments.application.dto.ConfirmPaymentRequest
import com.sigep.payments.application.dto.CreateAdminAutomaticDebitMandateRequest
import com.sigep.payments.application.dto.CreateAutomaticDebitInstructionRequest
import com.sigep.payments.application.dto.CreateAutomaticDebitMandateRequest
import com.sigep.payments.application.dto.RecordAutomaticDebitResultRequest
import com.sigep.payments.application.dto.RegisterChargePaymentRequest
import com.sigep.payments.application.dto.ResolveAutomaticDebitRejectionRequest
import com.sigep.payments.application.dto.SubmitAutomaticDebitInstructionRequest
import com.sigep.payments.application.dto.UpdateAutomaticDebitMandateRequest
import com.sigep.payments.application.gateway.AutomaticDebitAuthorizationCommand
import com.sigep.payments.application.gateway.AutomaticDebitPort
import com.sigep.payments.domain.model.AutomaticDebitEvent
import com.sigep.payments.domain.model.AutomaticDebitEventType
import com.sigep.payments.domain.model.AutomaticDebitInstruction
import com.sigep.payments.domain.model.AutomaticDebitInstructionStatus
import com.sigep.payments.domain.model.AutomaticDebitMandate
import com.sigep.payments.domain.model.AutomaticDebitMandateStatus
import com.sigep.payments.domain.model.AutomaticDebitProvider
import com.sigep.payments.domain.model.AutomaticDebitResolution
import com.sigep.payments.domain.model.AutomaticDebitScope
import com.sigep.payments.domain.model.BillingChargeStatus
import com.sigep.payments.domain.model.BillingCollectionChannel
import com.sigep.payments.domain.model.FiscalClosure
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.payments.domain.model.PaymentMethod
import com.sigep.payments.domain.repository.AutomaticDebitEventRepository
import com.sigep.payments.domain.repository.AutomaticDebitInstructionRepository
import com.sigep.payments.domain.repository.AutomaticDebitMandateRepository
import com.sigep.payments.domain.repository.BillingAccountRepository
import com.sigep.payments.domain.repository.BillingChargeRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import com.sigep.payments.domain.repository.PaymentRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
@Transactional
class AutomaticDebitService(
    private val port: AutomaticDebitPort,
    private val accountRepository: BillingAccountRepository,
    private val chargeRepository: BillingChargeRepository,
    private val paymentRepository: PaymentRepository,
    private val invoiceRepository: FiscalInvoiceRepository,
    private val mandateRepository: AutomaticDebitMandateRepository,
    private val instructionRepository: AutomaticDebitInstructionRepository,
    private val eventRepository: AutomaticDebitEventRepository,
    private val billingOperationsService: BillingOperationsService
) {

    @Transactional(readOnly = true)
    fun getMyMandates(guardianUserId: Long): List<AutomaticDebitMandateDto> {
        val account = accountRepository.findByGuardianUserId(guardianUserId).orElse(null) ?: return emptyList()
        return mandateRepository.findByAccountIdOrderByCreatedAtDesc(requireNotNull(account.id)).map(::toDto)
    }

    fun createMyMandate(
        guardianUserId: Long,
        request: CreateAutomaticDebitMandateRequest
    ): AutomaticDebitMandateDto {
        validateMaskedLabel(request.maskedLabel)
        val account = accountRepository.findByGuardianUserIdForUpdate(guardianUserId)
            .orElseThrow { ResourceNotFoundException("Billing account for guardian $guardianUserId not found") }
        ensureNoLiveDefaultMandate(requireNotNull(account.id))
        val provider = port.provider
            ?: throw ValidationException("Automatic debit self-service is disabled in this environment")
        val authorization = port.authorize(
            AutomaticDebitAuthorizationCommand(
                accountId = requireNotNull(account.id),
                maskedLabel = request.maskedLabel.trim(),
                consentVersion = request.consentVersion.trim()
            )
        )
        val mandate = saveMandate(
            accountId = requireNotNull(account.id),
            provider = provider,
            providerReference = authorization.providerReference,
            maskedLabel = request.maskedLabel,
            processorName = request.processorName,
            instrumentType = request.instrumentType,
            scope = request.scope,
            effectiveFrom = request.effectiveFrom,
            consentVersion = request.consentVersion,
            actorId = guardianUserId
        )
        assignEligibleCharges(mandate)
        return toDto(mandate)
    }

    fun createAdminMandate(
        request: CreateAdminAutomaticDebitMandateRequest,
        adminId: Long
    ): AutomaticDebitMandateDto {
        validateMaskedLabel(request.maskedLabel)
        accountRepository.findByIdForUpdate(request.accountId)
            .orElseThrow { ResourceNotFoundException("Billing account ${request.accountId} not found") }
        ensureNoLiveDefaultMandate(request.accountId)
        val mandate = saveMandate(
            accountId = request.accountId,
            provider = AutomaticDebitProvider.MANUAL,
            providerReference = "manual-${request.accountId}-${UUID.randomUUID()}",
            maskedLabel = request.maskedLabel,
            processorName = request.processorName,
            instrumentType = request.instrumentType,
            scope = request.scope,
            effectiveFrom = request.effectiveFrom,
            consentVersion = request.consentVersion,
            actorId = adminId
        )
        assignEligibleCharges(mandate)
        return toDto(mandate)
    }

    fun updateMyMandate(
        guardianUserId: Long,
        mandateId: Long,
        request: UpdateAutomaticDebitMandateRequest
    ): AutomaticDebitMandateDto {
        val mandate = requireMandate(mandateId)
        if (mandate.account.guardianUserId != guardianUserId) {
            throw ResourceNotFoundException("Automatic debit mandate $mandateId not found")
        }
        return updateMandate(mandate, request.status)
    }

    fun updateMandateByAdmin(
        mandateId: Long,
        request: UpdateAutomaticDebitMandateRequest
    ): AutomaticDebitMandateDto = updateMandate(requireMandate(mandateId), request.status)

    @Transactional(readOnly = true)
    fun listMandates(page: Int, size: Int): PageResponse<AutomaticDebitMandateDto> {
        val result = mandateRepository.findAllByOrderByCreatedAtDesc(
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
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
    fun listInstructions(page: Int, size: Int): PageResponse<AutomaticDebitInstructionDto> {
        val result = instructionRepository.findAll(
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), Sort.by(Sort.Direction.DESC, "createdAt"))
        )
        return PageResponse(
            content = result.content.map(::toDto),
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    fun createInstruction(
        idempotencyKey: String,
        request: CreateAutomaticDebitInstructionRequest,
        adminId: Long
    ): AutomaticDebitInstructionDto {
        validateIdempotencyKey(idempotencyKey)
        instructionRepository.findByIdempotencyKey(idempotencyKey).orElse(null)?.let { return toDto(it) }
        val invoice = invoiceRepository.findByIdForUpdate(request.invoiceId)
            .orElseThrow { ResourceNotFoundException("Fiscal invoice ${request.invoiceId} not found") }
        if (invoice.status !in AUTHORIZED_INVOICE_STATUSES || invoice.voucherNumber == null) {
            throw ResourceConflictException("The fiscal invoice must be authorized before preparing the debit")
        }
        val invoiceCharge = invoice.charge
            ?: throw ResourceConflictException("The fiscal invoice is not associated with a billing charge")
        val chargeId = requireNotNull(invoiceCharge.id)
        val charge = chargeRepository.findByIdForUpdate(chargeId)
            .orElseThrow { ResourceNotFoundException("Billing charge $chargeId not found") }
        if (!charge.automaticDebitEligible || charge.collectionChannel != BillingCollectionChannel.AUTOMATIC_DEBIT) {
            throw ValidationException("Billing charge $chargeId is not routed to automatic debit")
        }
        if (charge.status !in COLLECTIBLE_CHARGE_STATUSES) {
            throw ResourceConflictException("Billing charge $chargeId has no collectible balance")
        }
        if (request.processingDate.isBefore(invoice.issueDate)) {
            throw ValidationException("The processing date cannot precede the invoice issue date")
        }
        instructionRepository.findByInvoiceIdOrderByCreatedAtDesc(request.invoiceId)
            .firstOrNull { it.status in ACTIVE_INSTRUCTION_STATUSES }
            ?.let { return toDto(it) }
        if (instructionRepository.existsByChargeIdAndStatusIn(chargeId, ACTIVE_INSTRUCTION_STATUSES)) {
            throw ResourceConflictException("An automatic debit workflow is already active for charge $chargeId")
        }
        val mandate = mandateRepository.findByAccountIdAndIsDefaultTrueAndStatusIn(
            requireNotNull(charge.account.id),
            setOf(AutomaticDebitMandateStatus.ACTIVE)
        ).orElseThrow { ResourceConflictException("The billing account has no active automatic debit mandate") }
        if (mandate.effectiveFrom.isAfter(request.processingDate)) {
            throw ResourceConflictException("The automatic debit mandate is not effective on the processing date")
        }
        val amount = money(charge.amount - charge.paidAmount)
        if (amount <= BigDecimal.ZERO) throw ResourceConflictException("Billing charge $chargeId has no outstanding balance")
        val now = LocalDateTime.now(BUSINESS_ZONE)
        val instruction = instructionRepository.save(
            AutomaticDebitInstruction(
                mandate = mandate,
                charge = charge,
                invoice = invoice,
                idempotencyKey = idempotencyKey,
                amount = amount,
                currency = charge.currency,
                processingDate = request.processingDate,
                status = AutomaticDebitInstructionStatus.READY_FOR_PROCESSING,
                createdBy = adminId,
                createdAt = now,
                updatedAt = now
            )
        )
        saveEvent(instruction, "$idempotencyKey:prepared", AutomaticDebitEventType.PREPARED, "Debit data prepared from authorized invoice")
        return toDto(instruction)
    }

    fun submitInstruction(
        instructionId: Long,
        eventId: String,
        request: SubmitAutomaticDebitInstructionRequest
    ): AutomaticDebitInstructionDto {
        validateEventId(eventId)
        val instruction = requireInstructionForUpdate(instructionId)
        if (eventRepository.existsByProviderEventId(eventId)) return toDto(instruction)
        if (instruction.status != AutomaticDebitInstructionStatus.READY_FOR_PROCESSING) {
            throw ResourceConflictException("Only a prepared debit can be marked as submitted")
        }
        val now = LocalDateTime.now(BUSINESS_ZONE)
        val submitted = instructionRepository.save(
            instruction.copy(
                status = AutomaticDebitInstructionStatus.SUBMITTED,
                submissionReference = request.submissionReference.trim(),
                submittedAt = now,
                updatedAt = now
            )
        )
        saveEvent(submitted, eventId, AutomaticDebitEventType.SUBMITTED, "Submitted manually to processor")
        return toDto(submitted)
    }

    fun recordResult(
        instructionId: Long,
        eventId: String,
        request: RecordAutomaticDebitResultRequest,
        adminId: Long
    ): AutomaticDebitInstructionDto {
        validateEventId(eventId)
        if (request.outcome !in RESULT_OUTCOMES) {
            throw ValidationException("A processor result can only be APPROVED, REJECTED or UNKNOWN")
        }
        var instruction = requireInstructionForUpdate(instructionId)
        if (eventRepository.existsByProviderEventId(eventId)) return toDto(instruction)
        if (instruction.status !in setOf(AutomaticDebitInstructionStatus.SUBMITTED, AutomaticDebitInstructionStatus.UNKNOWN)) {
            throw ResourceConflictException("Only a submitted or unknown debit can receive a processor result")
        }
        val paymentId = if (request.outcome == AutomaticDebitInstructionStatus.APPROVED) {
            val outstanding = money(instruction.charge.amount - instruction.charge.paidAmount)
            if (outstanding != instruction.amount) {
                throw ResourceConflictException("The charge balance changed after the debit was prepared")
            }
            billingOperationsService.registerAutomaticDebitPayment(
                chargeId = requireNotNull(instruction.charge.id),
                idempotencyKey = "$eventId:payment".take(120),
                request = RegisterChargePaymentRequest(
                    amount = instruction.amount,
                    confirmation = ConfirmPaymentRequest(
                        paymentDate = instruction.processingDate,
                        paymentMethod = PaymentMethod.AUTOMATIC_DEBIT,
                        payerName = instruction.charge.account.displayName
                    ),
                    fiscalClosure = FiscalClosure.KEEP_PENDING,
                    externalReference = instruction.submissionReference,
                    notes = "Debito automatico conciliado manualmente"
                ),
                actorId = adminId
            ).payment.payment.id
        } else null
        val now = LocalDateTime.now(BUSINESS_ZONE)
        val persistedStatus = when (request.outcome) {
            AutomaticDebitInstructionStatus.REJECTED -> AutomaticDebitInstructionStatus.ACCOUNTING_RESOLUTION_REQUIRED
            else -> request.outcome
        }
        instruction = instructionRepository.save(
            instruction.copy(
                payment = paymentId?.let(paymentRepository::getReferenceById),
                status = persistedStatus,
                failureCode = request.failureCode?.trim(),
                failureMessage = request.failureMessage?.trim(),
                resolvedAt = if (request.outcome == AutomaticDebitInstructionStatus.APPROVED) now else null,
                updatedAt = now
            )
        )
        saveEvent(
            instruction,
            eventId,
            AutomaticDebitEventType.valueOf(request.outcome.name),
            request.failureMessage?.trim() ?: "Processor result ${request.outcome}"
        )
        return toDto(instruction)
    }

    fun resolveRejection(
        instructionId: Long,
        eventId: String,
        request: ResolveAutomaticDebitRejectionRequest,
        adminId: Long
    ): AutomaticDebitInstructionDto {
        validateEventId(eventId)
        val instruction = requireInstructionForUpdate(instructionId)
        if (eventRepository.existsByProviderEventId(eventId)) return toDto(instruction)
        if (instruction.status != AutomaticDebitInstructionStatus.ACCOUNTING_RESOLUTION_REQUIRED) {
            throw ResourceConflictException("Only a rejected debit awaiting accounting resolution can be resolved")
        }
        val now = LocalDateTime.now(BUSINESS_ZONE)
        val status = when (request.resolution) {
            AutomaticDebitResolution.KEEP_INVOICE -> AutomaticDebitInstructionStatus.REJECTED
            AutomaticDebitResolution.REQUEST_CREDIT_NOTE -> AutomaticDebitInstructionStatus.CREDIT_NOTE_REQUIRED
        }
        val eventType = when (request.resolution) {
            AutomaticDebitResolution.KEEP_INVOICE -> AutomaticDebitEventType.RESOLUTION_KEEP_INVOICE
            AutomaticDebitResolution.REQUEST_CREDIT_NOTE -> AutomaticDebitEventType.RESOLUTION_CREDIT_NOTE_REQUIRED
        }
        val resolved = instructionRepository.save(
            instruction.copy(
                status = status,
                resolution = request.resolution,
                resolutionReason = request.reason.trim(),
                resolvedBy = adminId,
                resolvedAt = now,
                updatedAt = now
            )
        )
        saveEvent(resolved, eventId, eventType, request.reason.trim())
        return toDto(resolved)
    }

    fun cancelInstruction(instructionId: Long, eventId: String, reason: String): AutomaticDebitInstructionDto {
        validateEventId(eventId)
        if (reason.isBlank()) throw ValidationException("A reason is required to cancel the prepared debit")
        val instruction = requireInstructionForUpdate(instructionId)
        if (eventRepository.existsByProviderEventId(eventId)) return toDto(instruction)
        if (instruction.status != AutomaticDebitInstructionStatus.READY_FOR_PROCESSING) {
            throw ResourceConflictException("Only a debit that has not been submitted can be cancelled")
        }
        val now = LocalDateTime.now(BUSINESS_ZONE)
        val cancelled = instructionRepository.save(
            instruction.copy(status = AutomaticDebitInstructionStatus.CANCELLED, resolvedAt = now, updatedAt = now)
        )
        saveEvent(cancelled, eventId, AutomaticDebitEventType.CANCELLED, reason.trim())
        return toDto(cancelled)
    }

    fun reverseInstruction(instructionId: Long, reason: String, adminId: Long): AutomaticDebitInstructionDto {
        if (reason.isBlank()) throw ValidationException("A reason is required to reverse an automatic debit")
        val instruction = requireInstructionForUpdate(instructionId)
        if (instruction.status != AutomaticDebitInstructionStatus.APPROVED || instruction.payment?.id == null) {
            throw ResourceConflictException("Only an approved automatic debit can be reversed")
        }
        billingOperationsService.reverseAutomaticDebitPayment(requireNotNull(instruction.payment.id))
        val now = LocalDateTime.now(BUSINESS_ZONE)
        val reversed = instructionRepository.save(
            instruction.copy(status = AutomaticDebitInstructionStatus.REVERSED, resolvedAt = now, updatedAt = now)
        )
        saveEvent(
            reversed,
            "instruction-$instructionId-reversed-$adminId-${now.nano}",
            AutomaticDebitEventType.REVERSED,
            reason.trim()
        )
        return toDto(reversed)
    }

    private fun saveMandate(
        accountId: Long,
        provider: AutomaticDebitProvider,
        providerReference: String,
        maskedLabel: String,
        processorName: String,
        instrumentType: com.sigep.payments.domain.model.AutomaticDebitInstrumentType,
        scope: AutomaticDebitScope,
        effectiveFrom: java.time.LocalDate,
        consentVersion: String,
        actorId: Long
    ): AutomaticDebitMandate {
        val account = accountRepository.getReferenceById(accountId)
        val now = LocalDateTime.now(BUSINESS_ZONE)
        return mandateRepository.save(
            AutomaticDebitMandate(
                account = account,
                provider = provider,
                providerReference = providerReference,
                maskedLabel = maskedLabel.trim(),
                processorName = processorName.trim(),
                instrumentType = instrumentType,
                scope = scope,
                effectiveFrom = effectiveFrom,
                status = AutomaticDebitMandateStatus.ACTIVE,
                consentVersion = consentVersion.trim(),
                consentedAt = now,
                consentedBy = actorId,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun updateMandate(
        mandate: AutomaticDebitMandate,
        status: AutomaticDebitMandateStatus
    ): AutomaticDebitMandateDto {
        if (mandate.status == AutomaticDebitMandateStatus.CANCELLED) {
            throw ResourceConflictException("A cancelled mandate cannot be changed")
        }
        if (status !in setOf(
                AutomaticDebitMandateStatus.ACTIVE,
                AutomaticDebitMandateStatus.PAUSED,
                AutomaticDebitMandateStatus.CANCELLED
            )
        ) {
            throw ValidationException("Mandate status can only be ACTIVE, PAUSED or CANCELLED")
        }
        val now = LocalDateTime.now(BUSINESS_ZONE)
        val updated = mandateRepository.save(
            mandate.copy(
                status = status,
                isDefault = status != AutomaticDebitMandateStatus.CANCELLED,
                cancelledAt = if (status == AutomaticDebitMandateStatus.CANCELLED) now else null,
                updatedAt = now
            )
        )
        when (status) {
            AutomaticDebitMandateStatus.ACTIVE -> assignEligibleCharges(updated)
            AutomaticDebitMandateStatus.CANCELLED -> releaseFutureUninvoicedCharges(updated)
            else -> Unit
        }
        return toDto(updated)
    }

    private fun assignEligibleCharges(mandate: AutomaticDebitMandate) {
        val accountId = requireNotNull(mandate.account.id)
        chargeRepository.findByAccountIdOrderByDueDateAsc(accountId)
            .asSequence()
            .filter { it.automaticDebitEligible }
            .filter { !it.dueDate.isBefore(mandate.effectiveFrom) }
            .filter { it.status in COLLECTIBLE_CHARGE_STATUSES }
            .filter { it.concept != "TUITION_ENROLLMENT" || mandate.scope == AutomaticDebitScope.INSTALLMENTS_AND_ENROLLMENT }
            .filter { invoiceRepository.findByChargeId(requireNotNull(it.id)).isEmpty }
            .forEach { charge ->
                if (charge.collectionChannel != BillingCollectionChannel.AUTOMATIC_DEBIT) {
                    chargeRepository.save(
                        charge.copy(collectionChannel = BillingCollectionChannel.AUTOMATIC_DEBIT, updatedAt = LocalDateTime.now(BUSINESS_ZONE))
                    )
                }
            }
    }

    private fun releaseFutureUninvoicedCharges(mandate: AutomaticDebitMandate) {
        val accountId = requireNotNull(mandate.account.id)
        chargeRepository.findByAccountIdOrderByDueDateAsc(accountId)
            .asSequence()
            .filter { it.collectionChannel == BillingCollectionChannel.AUTOMATIC_DEBIT }
            .filter { !it.dueDate.isBefore(mandate.effectiveFrom) }
            .filter { it.status in COLLECTIBLE_CHARGE_STATUSES }
            .filter { invoiceRepository.findByChargeId(requireNotNull(it.id)).isEmpty }
            .filterNot {
                instructionRepository.existsByChargeIdAndStatusIn(
                    requireNotNull(it.id),
                    ACTIVE_INSTRUCTION_STATUSES
                )
            }
            .forEach { charge ->
                chargeRepository.save(
                    charge.copy(
                        collectionChannel = BillingCollectionChannel.REGULAR,
                        updatedAt = LocalDateTime.now(BUSINESS_ZONE)
                    )
                )
            }
    }

    private fun ensureNoLiveDefaultMandate(accountId: Long) {
        mandateRepository.findByAccountIdAndIsDefaultTrueAndStatusIn(accountId, LIVE_MANDATE_STATUSES)
            .orElse(null)
            ?.let { throw ResourceConflictException("The billing account already has a default automatic debit mandate") }
    }

    private fun requireMandate(mandateId: Long): AutomaticDebitMandate = mandateRepository.findById(mandateId)
        .orElseThrow { ResourceNotFoundException("Automatic debit mandate $mandateId not found") }

    private fun requireInstructionForUpdate(instructionId: Long): AutomaticDebitInstruction =
        instructionRepository.findByIdForUpdate(instructionId)
            .orElseThrow { ResourceNotFoundException("Automatic debit instruction $instructionId not found") }

    private fun saveEvent(
        instruction: AutomaticDebitInstruction,
        eventId: String,
        type: AutomaticDebitEventType,
        detail: String
    ) {
        val now = LocalDateTime.now(BUSINESS_ZONE)
        eventRepository.save(
            AutomaticDebitEvent(
                instruction = instruction,
                providerEventId = eventId.take(200),
                type = type,
                sanitizedDetail = detail.take(1000),
                occurredAt = now,
                createdAt = now
            )
        )
    }

    private fun validateMaskedLabel(value: String) {
        if (value.isBlank()) throw ValidationException("A masked account label is required")
        if (value.count(Char::isDigit) > 4) {
            throw ValidationException("Only a masked account label with at most the last four digits is allowed")
        }
    }

    private fun validateIdempotencyKey(value: String) {
        if (value.isBlank() || value.length > 120) {
            throw ValidationException("Idempotency-Key is required and must have at most 120 characters")
        }
    }

    private fun validateEventId(value: String) {
        if (value.isBlank() || value.length > 200) {
            throw ValidationException("Idempotency-Key is required and must have at most 200 characters")
        }
    }

    private fun toDto(mandate: AutomaticDebitMandate) = AutomaticDebitMandateDto(
        id = requireNotNull(mandate.id),
        accountId = requireNotNull(mandate.account.id),
        provider = mandate.provider.name,
        maskedLabel = mandate.maskedLabel,
        processorName = mandate.processorName,
        instrumentType = mandate.instrumentType,
        scope = mandate.scope,
        effectiveFrom = mandate.effectiveFrom,
        status = mandate.status,
        isDefault = mandate.isDefault,
        consentVersion = mandate.consentVersion,
        consentedAt = mandate.consentedAt,
        cancelledAt = mandate.cancelledAt,
        simulated = mandate.provider == AutomaticDebitProvider.MOCK
    )

    private fun toDto(instruction: AutomaticDebitInstruction): AutomaticDebitInstructionDto {
        val voucherNumber = requireNotNull(instruction.invoice.voucherNumber)
        return AutomaticDebitInstructionDto(
            id = requireNotNull(instruction.id),
            mandateId = requireNotNull(instruction.mandate.id),
            chargeId = requireNotNull(instruction.charge.id),
            invoiceId = requireNotNull(instruction.invoice.id),
            paymentId = instruction.payment?.id,
            accountId = requireNotNull(instruction.charge.account.id),
            studentName = instruction.charge.studentName,
            receiverName = instruction.invoice.receiverName,
            pointOfSale = requireNotNull(instruction.invoice.pointOfSale),
            voucherNumber = voucherNumber,
            voucherSuffix = voucherNumber.toString().takeLast(3).padStart(3, '0'),
            processorName = instruction.mandate.processorName,
            instrumentType = instruction.mandate.instrumentType,
            maskedLabel = instruction.mandate.maskedLabel,
            amount = instruction.amount,
            currency = instruction.currency,
            processingDate = instruction.processingDate,
            status = instruction.status,
            submissionReference = instruction.submissionReference,
            failureCode = instruction.failureCode,
            failureMessage = instruction.failureMessage,
            resolution = instruction.resolution,
            resolutionReason = instruction.resolutionReason,
            submittedAt = instruction.submittedAt,
            resolvedAt = instruction.resolvedAt,
            createdAt = instruction.createdAt,
            updatedAt = instruction.updatedAt,
            simulated = instruction.mandate.provider == AutomaticDebitProvider.MOCK
        )
    }

    private fun money(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_EVEN)

    private companion object {
        val BUSINESS_ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
        val AUTHORIZED_INVOICE_STATUSES = setOf(
            FiscalInvoiceStatus.AUTHORIZED,
            FiscalInvoiceStatus.AUTHORIZED_WITH_OBSERVATIONS
        )
        val COLLECTIBLE_CHARGE_STATUSES = setOf(BillingChargeStatus.OPEN, BillingChargeStatus.PARTIALLY_PAID)
        val LIVE_MANDATE_STATUSES = setOf(
            AutomaticDebitMandateStatus.PENDING_AUTHORIZATION,
            AutomaticDebitMandateStatus.ACTIVE,
            AutomaticDebitMandateStatus.PAUSED
        )
        val ACTIVE_INSTRUCTION_STATUSES = setOf(
            AutomaticDebitInstructionStatus.READY_FOR_PROCESSING,
            AutomaticDebitInstructionStatus.SUBMITTED,
            AutomaticDebitInstructionStatus.UNKNOWN,
            AutomaticDebitInstructionStatus.ACCOUNTING_RESOLUTION_REQUIRED,
            AutomaticDebitInstructionStatus.CREDIT_NOTE_REQUIRED
        )
        val RESULT_OUTCOMES = setOf(
            AutomaticDebitInstructionStatus.APPROVED,
            AutomaticDebitInstructionStatus.REJECTED,
            AutomaticDebitInstructionStatus.UNKNOWN
        )
    }
}
