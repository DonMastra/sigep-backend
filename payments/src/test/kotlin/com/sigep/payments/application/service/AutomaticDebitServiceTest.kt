package com.sigep.payments.application.service

import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.payments.application.dto.BillingChargeDto
import com.sigep.payments.application.dto.ChargePaymentResultDto
import com.sigep.payments.application.dto.CreateAutomaticDebitInstructionRequest
import com.sigep.payments.application.dto.CreateAutomaticDebitMandateRequest
import com.sigep.payments.application.dto.PaymentDetailDto
import com.sigep.payments.application.dto.PaymentDto
import com.sigep.payments.application.dto.RecordAutomaticDebitResultRequest
import com.sigep.payments.application.dto.RegisterChargePaymentRequest
import com.sigep.payments.application.dto.ResolveAutomaticDebitRejectionRequest
import com.sigep.payments.application.dto.SubmitAutomaticDebitInstructionRequest
import com.sigep.payments.application.dto.UpdateAutomaticDebitMandateRequest
import com.sigep.payments.application.gateway.AutomaticDebitPort
import com.sigep.payments.domain.model.AutomaticDebitInstruction
import com.sigep.payments.domain.model.AutomaticDebitInstructionStatus
import com.sigep.payments.domain.model.AutomaticDebitMandate
import com.sigep.payments.domain.model.AutomaticDebitMandateStatus
import com.sigep.payments.domain.model.AutomaticDebitProvider
import com.sigep.payments.domain.model.AutomaticDebitResolution
import com.sigep.payments.domain.model.BillingAccount
import com.sigep.payments.domain.model.BillingCharge
import com.sigep.payments.domain.model.BillingChargeStatus
import com.sigep.payments.domain.model.BillingCollectionChannel
import com.sigep.payments.domain.model.FiscalClosure
import com.sigep.payments.domain.model.FiscalInvoice
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.payments.domain.model.Payment
import com.sigep.payments.domain.model.PaymentMethod
import com.sigep.payments.domain.model.PaymentStatus
import com.sigep.payments.domain.repository.AutomaticDebitEventRepository
import com.sigep.payments.domain.repository.AutomaticDebitInstructionRepository
import com.sigep.payments.domain.repository.AutomaticDebitMandateRepository
import com.sigep.payments.domain.repository.BillingAccountRepository
import com.sigep.payments.domain.repository.BillingChargeRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import com.sigep.payments.domain.repository.PaymentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AutomaticDebitServiceTest {

    private val port = mockk<AutomaticDebitPort>()
    private val accountRepository = mockk<BillingAccountRepository>()
    private val chargeRepository = mockk<BillingChargeRepository>(relaxed = true)
    private val paymentRepository = mockk<PaymentRepository>()
    private val invoiceRepository = mockk<FiscalInvoiceRepository>()
    private val mandateRepository = mockk<AutomaticDebitMandateRepository>()
    private val instructionRepository = mockk<AutomaticDebitInstructionRepository>()
    private val eventRepository = mockk<AutomaticDebitEventRepository>(relaxed = true)
    private val billingOperationsService = mockk<BillingOperationsService>()
    private lateinit var service: AutomaticDebitService
    private lateinit var storedInstruction: AutomaticDebitInstruction

    @BeforeEach
    fun setUp() {
        service = AutomaticDebitService(
            port,
            accountRepository,
            chargeRepository,
            paymentRepository,
            invoiceRepository,
            mandateRepository,
            instructionRepository,
            eventRepository,
            billingOperationsService
        )
        every { port.provider } returns AutomaticDebitProvider.MOCK
        every { instructionRepository.findByIdempotencyKey(any()) } returns Optional.empty()
        every { instructionRepository.findByInvoiceIdOrderByCreatedAtDesc(any()) } returns emptyList()
        every { instructionRepository.existsByChargeIdAndStatusIn(any(), any()) } returns false
        every { eventRepository.existsByProviderEventId(any()) } returns false
        every { instructionRepository.save(any()) } answers {
            val candidate = firstArg<AutomaticDebitInstruction>()
            storedInstruction = if (candidate.id == null) candidate.copy(id = 30L) else candidate
            storedInstruction
        }
        every { eventRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `guardian cannot update a mandate owned by another billing account`() {
        every { mandateRepository.findById(20L) } returns Optional.of(mandate())

        assertFailsWith<ResourceNotFoundException> {
            service.updateMyMandate(
                guardianUserId = 999L,
                mandateId = 20L,
                request = UpdateAutomaticDebitMandateRequest(AutomaticDebitMandateStatus.PAUSED)
            )
        }

        verify(exactly = 0) { mandateRepository.save(any()) }
    }

    @Test
    fun `mandate rejects full account identifiers before calling the provider`() {
        assertFailsWith<ValidationException> {
            service.createMyMandate(
                guardianUserId = 20L,
                request = CreateAutomaticDebitMandateRequest(
                    maskedLabel = "CBU 2850590940090418135201",
                    consentVersion = "v1"
                )
            )
        }

        verify(exactly = 0) { port.authorize(any()) }
    }

    @Test
    fun `authorized invoice prepares only the outstanding balance without creating a payment`() {
        prepareAuthorizedInvoice()

        val result = service.createInstruction(
            "prepare-invoice-40",
            CreateAutomaticDebitInstructionRequest(40L, LocalDate.now()),
            99L
        )

        assertEquals(AutomaticDebitInstructionStatus.READY_FOR_PROCESSING, result.status)
        assertEquals(BigDecimal("60.00"), result.amount)
        assertEquals(40L, result.invoiceId)
        assertEquals(1, result.pointOfSale)
        assertEquals("321", result.voucherSuffix)
        assertNull(result.paymentId)
        verify(exactly = 0) { billingOperationsService.registerAutomaticDebitPayment(any(), any(), any(), any()) }
    }

    @Test
    fun `submitted debit is paid only after an approved processor result`() {
        prepareAuthorizedInvoice()
        service.createInstruction(
            "prepare-invoice-40",
            CreateAutomaticDebitInstructionRequest(40L, LocalDate.now()),
            99L
        )
        every { instructionRepository.findByIdForUpdate(30L) } answers { Optional.of(storedInstruction) }
        service.submitInstruction(30L, "submit-30", SubmitAutomaticDebitInstructionRequest("archivo-agosto"))
        every { instructionRepository.findByIdForUpdate(30L) } answers { Optional.of(storedInstruction) }
        val paymentRequest = slot<RegisterChargePaymentRequest>()
        every {
            billingOperationsService.registerAutomaticDebitPayment(11L, "result-approved:payment", capture(paymentRequest), 99L)
        } returns paymentResult(90L)
        every { paymentRepository.getReferenceById(90L) } returns paidPayment(90L)

        val result = service.recordResult(
            30L,
            "result-approved",
            RecordAutomaticDebitResultRequest(AutomaticDebitInstructionStatus.APPROVED),
            99L
        )

        assertEquals(AutomaticDebitInstructionStatus.APPROVED, result.status)
        assertEquals(90L, result.paymentId)
        assertEquals(FiscalClosure.KEEP_PENDING, paymentRequest.captured.fiscalClosure)
        assertEquals(PaymentMethod.AUTOMATIC_DEBIT, paymentRequest.captured.confirmation.paymentMethod)
    }

    @Test
    fun `rejected debit requires accounting resolution before it can be retried`() {
        storedInstruction = instruction(AutomaticDebitInstructionStatus.SUBMITTED)
        every { instructionRepository.findByIdForUpdate(30L) } answers { Optional.of(storedInstruction) }

        val rejected = service.recordResult(
            30L,
            "result-rejected",
            RecordAutomaticDebitResultRequest(
                AutomaticDebitInstructionStatus.REJECTED,
                failureMessage = "Saldo insuficiente"
            ),
            99L
        )
        every { instructionRepository.findByIdForUpdate(30L) } answers { Optional.of(storedInstruction) }
        val resolved = service.resolveRejection(
            30L,
            "resolution-30",
            ResolveAutomaticDebitRejectionRequest(
                AutomaticDebitResolution.REQUEST_CREDIT_NOTE,
                "Politica contable validada"
            ),
            99L
        )

        assertEquals(AutomaticDebitInstructionStatus.ACCOUNTING_RESOLUTION_REQUIRED, rejected.status)
        assertEquals(AutomaticDebitInstructionStatus.CREDIT_NOTE_REQUIRED, resolved.status)
        assertEquals(AutomaticDebitResolution.REQUEST_CREDIT_NOTE, resolved.resolution)
        verify(exactly = 0) { billingOperationsService.registerAutomaticDebitPayment(any(), any(), any(), any()) }
    }

    @Test
    fun `reversal preserves the instruction and reopens the charge through the billing aggregate`() {
        storedInstruction = instruction(
            status = AutomaticDebitInstructionStatus.APPROVED,
            payment = paidPayment(90L)
        )
        every { instructionRepository.findByIdForUpdate(30L) } returns Optional.of(storedInstruction)
        every { billingOperationsService.reverseAutomaticDebitPayment(90L) } returns mockk(relaxed = true)

        val result = service.reverseInstruction(30L, "Desconocimiento del debito", 99L)

        assertEquals(AutomaticDebitInstructionStatus.REVERSED, result.status)
        verify(exactly = 1) { billingOperationsService.reverseAutomaticDebitPayment(90L) }
    }

    @Test
    fun `cancelling a mandate returns future uninvoiced charges to regular collection`() {
        val mandate = mandate()
        val rerouted = slot<BillingCharge>()
        every { mandateRepository.findById(20L) } returns Optional.of(mandate)
        every { mandateRepository.save(any()) } answers { firstArg() }
        every { chargeRepository.findByAccountIdOrderByDueDateAsc(10L) } returns listOf(charge())
        every { invoiceRepository.findByChargeId(11L) } returns Optional.empty()
        every { instructionRepository.existsByChargeIdAndStatusIn(11L, any()) } returns false
        every { chargeRepository.save(capture(rerouted)) } answers { rerouted.captured }

        service.updateMandateByAdmin(
            20L,
            UpdateAutomaticDebitMandateRequest(AutomaticDebitMandateStatus.CANCELLED)
        )

        assertEquals(BillingCollectionChannel.REGULAR, rerouted.captured.collectionChannel)
    }

    private fun prepareAuthorizedInvoice() {
        every { invoiceRepository.findByIdForUpdate(40L) } returns Optional.of(invoice())
        every { chargeRepository.findByIdForUpdate(11L) } returns Optional.of(charge())
        every {
            mandateRepository.findByAccountIdAndIsDefaultTrueAndStatusIn(10L, setOf(AutomaticDebitMandateStatus.ACTIVE))
        } returns Optional.of(mandate())
    }

    private fun account() = BillingAccount(id = 10L, guardianUserId = 20L, displayName = "Tutor")

    private fun charge() = BillingCharge(
        id = 11L,
        account = account(),
        studentName = "Ana",
        sourceType = "TUITION_LEDGER",
        sourceId = 99L,
        concept = "MONTHLY_FEE",
        description = "Cuota",
        baseAmount = BigDecimal("100.00"),
        amount = BigDecimal("100.00"),
        paidAmount = BigDecimal("40.00"),
        dueDate = LocalDate.now().plusDays(1),
        automaticDebitEligible = true,
        collectionChannel = BillingCollectionChannel.AUTOMATIC_DEBIT,
        status = BillingChargeStatus.PARTIALLY_PAID
    )

    private fun invoice() = FiscalInvoice(
        id = 40L,
        charge = charge(),
        creationKey = "invoice-40",
        requestFingerprint = "fingerprint",
        status = FiscalInvoiceStatus.AUTHORIZED,
        issuerCuit = "30712345678",
        pointOfSale = 1,
        voucherType = 11,
        voucherNumber = 321L,
        concept = 2,
        receiverName = "Tutor",
        receiverDocumentType = 96,
        receiverDocumentNumber = "30111222",
        receiverVatConditionId = 5,
        issueDate = LocalDate.now(),
        currency = "PES",
        exchangeRate = BigDecimal.ONE,
        totalAmount = BigDecimal("100.00"),
        nonTaxedAmount = BigDecimal("100.00"),
        netAmount = BigDecimal.ZERO,
        exemptAmount = BigDecimal.ZERO,
        vatAmount = BigDecimal.ZERO,
        otherTaxesAmount = BigDecimal.ZERO
    )

    private fun mandate() = AutomaticDebitMandate(
        id = 20L,
        account = account(),
        provider = AutomaticDebitProvider.MANUAL,
        providerReference = "manual-mandate-20",
        maskedLabel = "Cuenta terminada en 1234",
        status = AutomaticDebitMandateStatus.ACTIVE,
        consentVersion = "v1",
        consentedAt = LocalDateTime.now(),
        consentedBy = 20L
    )

    private fun instruction(status: AutomaticDebitInstructionStatus, payment: Payment? = null) = AutomaticDebitInstruction(
        id = 30L,
        mandate = mandate(),
        charge = charge(),
        invoice = invoice(),
        payment = payment,
        idempotencyKey = "auto-instruction",
        amount = BigDecimal("60.00"),
        currency = "ARS",
        processingDate = LocalDate.now(),
        status = status,
        submittedAt = if (status == AutomaticDebitInstructionStatus.SUBMITTED) LocalDateTime.now() else null,
        createdBy = 99L
    )

    private fun paidPayment(id: Long) = Payment(
        id = id,
        studentId = null,
        amount = BigDecimal("60.00"),
        concept = "Cuota",
        dueDate = LocalDate.now(),
        status = PaymentStatus.PAID,
        paymentMethod = PaymentMethod.AUTOMATIC_DEBIT,
        receiptNumber = "RX-2026-$id",
        notes = null
    )

    private fun paymentResult(paymentId: Long): ChargePaymentResultDto {
        val now = LocalDateTime.now()
        val detail = PaymentDetailDto(
            payment = PaymentDto(
                id = paymentId,
                studentId = null,
                amount = BigDecimal("60.00"),
                currency = "ARS",
                concept = "Cuota",
                paymentDate = LocalDate.now(),
                dueDate = LocalDate.now(),
                status = PaymentStatus.PAID,
                paymentMethod = PaymentMethod.AUTOMATIC_DEBIT,
                receiptNumber = "RX-2026-$paymentId",
                externalReference = null,
                notes = null,
                confirmedAt = now,
                confirmedBy = 99L,
                createdAt = now,
                updatedAt = now
            ),
            receipt = null,
            invoice = null
        )
        return ChargePaymentResultDto(mockk<BillingChargeDto>(relaxed = true), detail)
    }
}
