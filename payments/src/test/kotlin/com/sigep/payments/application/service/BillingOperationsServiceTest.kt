package com.sigep.payments.application.service

import com.sigep.common.application.service.BillingChargeSettlementObserver
import com.sigep.payments.application.dto.BillingChargeFilterRequest
import com.sigep.payments.application.dto.PrepareBillingRunRequest
import com.sigep.payments.application.dto.UpdateBillingProfileRequest
import com.sigep.payments.application.dto.ConfirmPaymentRequest
import com.sigep.payments.application.dto.PaymentDetailDto
import com.sigep.payments.application.dto.PaymentDto
import com.sigep.payments.application.dto.RegisterChargePaymentRequest
import com.sigep.common.application.exception.ValidationException
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.payments.domain.model.BillingAccount
import com.sigep.payments.domain.model.BillingCharge
import com.sigep.payments.domain.model.BillingChargeStatus
import com.sigep.payments.domain.model.BillingChargeFiscalDecision
import com.sigep.payments.domain.model.BillingChargeFiscalDisposition
import com.sigep.payments.domain.model.BillingCollectionChannel
import com.sigep.payments.domain.model.BillingSelectionMode
import com.sigep.payments.domain.model.FiscalAmountTreatment
import com.sigep.payments.domain.model.BillingProfile
import com.sigep.payments.domain.model.BillingProfileStatus
import com.sigep.payments.domain.model.FiscalClosure
import com.sigep.payments.domain.model.Payment
import com.sigep.payments.domain.model.PaymentAllocation
import com.sigep.payments.domain.model.PaymentMethod
import com.sigep.payments.domain.model.PaymentStatus
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class BillingOperationsServiceTest {

    private val accountRepository = mockk<BillingAccountRepository>(relaxed = true)
    private val profileRepository = mockk<BillingProfileRepository>()
    private val chargeRepository = mockk<BillingChargeRepository>(relaxed = true)
    private val fiscalDecisionRepository = mockk<BillingChargeFiscalDecisionRepository>(relaxed = true)
    private val automaticDebitInstructionRepository = mockk<AutomaticDebitInstructionRepository>(relaxed = true)
    private val automaticDebitMandateRepository = mockk<AutomaticDebitMandateRepository>(relaxed = true)
    private val allocationRepository = mockk<PaymentAllocationRepository>(relaxed = true)
    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val invoiceRepository = mockk<FiscalInvoiceRepository>(relaxed = true)
    private val runRepository = mockk<BillingRunRepository>(relaxed = true)
    private val runItemRepository = mockk<BillingRunItemRepository>(relaxed = true)
    private val paymentService = mockk<PaymentApplicationService>(relaxed = true)
    private val billingService = mockk<BillingApplicationService>(relaxed = true)
    private val lateFeeService = mockk<BillingLateFeeService>(relaxed = true)
    private val service = BillingOperationsService(
        accountRepository,
        profileRepository,
        chargeRepository,
        fiscalDecisionRepository,
        automaticDebitInstructionRepository,
        automaticDebitMandateRepository,
        allocationRepository,
        paymentRepository,
        invoiceRepository,
        runRepository,
        runItemRepository,
        paymentService,
        billingService,
        lateFeeService,
        emptyList<BillingChargeSettlementObserver>(),
        true
    )

    init {
        every {
            automaticDebitMandateRepository.findByAccountIdAndIsDefaultTrueAndStatusIn(any(), any())
        } returns Optional.empty()
    }

    @Test
    fun `charge listing trims student search and keeps server pagination`() {
        every {
            chargeRepository.findByFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns PageImpl(emptyList(), PageRequest.of(0, 25), 0)

        val result = service.listCharges(
            status = BillingChargeStatus.OPEN,
            studentId = null,
            studentQuery = "  Ana Perez  ",
            profileStatus = BillingProfileStatus.READY,
            fiscalDisposition = null,
            overdue = null,
            automaticDebitStatus = null,
            collectionChannel = null,
            page = 0,
            size = 25
        )

        assertEquals(0, result.totalElements)
        verify(exactly = 1) {
            chargeRepository.findByFilters(
                BillingChargeStatus.OPEN,
                null,
                "Ana Perez",
                BillingProfileStatus.READY,
                null,
                null,
                any(),
                null,
                null,
                match { it.pageNumber == 0 && it.pageSize == 25 }
            )
        }
    }

    @Test
    fun `charge listing binds empty text when student search is absent`() {
        every {
            chargeRepository.findByFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns PageImpl(emptyList(), PageRequest.of(0, 25), 0)

        service.listCharges(
            status = BillingChargeStatus.OPEN,
            studentId = null,
            studentQuery = null,
            profileStatus = null,
            fiscalDisposition = null,
            overdue = null,
            automaticDebitStatus = null,
            collectionChannel = null,
            page = 0,
            size = 25
        )

        verify(exactly = 1) {
            chargeRepository.findByFilters(
                BillingChargeStatus.OPEN,
                null,
                "",
                null,
                null,
                null,
                any(),
                null,
                null,
                match { it.pageNumber == 0 && it.pageSize == 25 }
            )
        }
    }

    @Test
    fun `filtered billing preview keeps the student search`() {
        every {
            chargeRepository.findByFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns PageImpl(emptyList(), PageRequest.of(0, 1000), 0)

        val result = service.preview(
            PrepareBillingRunRequest(
                selectionMode = BillingSelectionMode.FILTERED,
                filters = BillingChargeFilterRequest(
                    status = BillingChargeStatus.OPEN,
                    studentQuery = "Ana Perez"
                ),
                issueDate = LocalDate.of(2026, 8, 5),
                amountTreatment = FiscalAmountTreatment.NON_TAXED
            )
        )

        assertEquals(0, result.selectedCount)
        verify(exactly = 1) {
            chargeRepository.findByFilters(
                BillingChargeStatus.OPEN,
                null,
                "Ana Perez",
                null,
                null,
                null,
                any(),
                null,
                BillingCollectionChannel.REGULAR,
                match { it.pageSize == 1000 }
            )
        }
    }

    @Test
    fun `regular billing run cannot include an automatic debit charge`() {
        val automaticDebitCharge = simpleCharge().copy(
            collectionChannel = BillingCollectionChannel.AUTOMATIC_DEBIT
        )
        every { chargeRepository.findAllById(listOf(1L)) } returns listOf(automaticDebitCharge)

        assertFailsWith<ValidationException> {
            service.preview(
                PrepareBillingRunRequest(
                    selectionMode = BillingSelectionMode.INDIVIDUAL,
                    chargeIds = listOf(1L),
                    issueDate = LocalDate.of(2026, 8, 7),
                    amountTreatment = FiscalAmountTreatment.NON_TAXED
                )
            )
        }

        verify(exactly = 0) { lateFeeService.applyIfDue(any(), any()) }
    }

    @Test
    fun `validated profile becomes reusable while RG 5866 remains disabled`() {
        val account = BillingAccount(id = 10L, guardianUserId = 20L, displayName = "Tutor")
        val current = BillingProfile(
            id = 30L,
            account = account,
            receiverName = "Tutor",
            receiverDocumentNumber = "30111222",
            status = BillingProfileStatus.INCOMPLETE
        )
        val saved = slot<BillingProfile>()
        every { profileRepository.findByAccountId(10L) } returns Optional.of(current)
        every { profileRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.updateProfile(
            accountId = 10L,
            request = UpdateBillingProfileRequest(
                receiverName = "Tutor Responsable",
                receiverAddress = "Calle 123, Buenos Aires",
                receiverDocumentType = 96,
                receiverDocumentNumber = "30111222",
                receiverVatConditionId = 5,
                defaultVoucherType = 11,
                defaultFiscalConcept = 2,
                fiscalCurrency = "PES"
            ),
            adminId = 1L
        )

        assertEquals(BillingProfileStatus.READY, result.status)
        assertEquals(1L, saved.captured.updatedBy)
        assertFalse(result.rg5866Applicable)
        assertEquals(emptyList(), result.missingFields)
    }

    @Test
    fun `two partial payments settle one charge and keep two allocations`() {
        val account = BillingAccount(id = 10L, guardianUserId = 20L, displayName = "Tutor")
        val profile = BillingProfile(
            id = 30L,
            account = account,
            receiverName = "Tutor",
            status = BillingProfileStatus.INCOMPLETE
        )
        var currentCharge = BillingCharge(
            id = 1L,
            account = account,
            studentName = "Ana",
            sourceType = "TUITION_LEDGER",
            sourceId = 99L,
            concept = "MONTHLY_FEE",
            description = "Cuota",
            baseAmount = java.math.BigDecimal("100.00"),
            amount = java.math.BigDecimal("100.00"),
            dueDate = LocalDate.now().plusDays(1)
        )
        val payments = mutableMapOf<Long, Payment>()
        val allocations = mutableListOf<PaymentAllocation>()
        var nextPaymentId = 100L
        every { chargeRepository.findByIdForUpdate(1L) } answers { Optional.of(currentCharge) }
        every { paymentRepository.findByCreationKey(any()) } returns Optional.empty()
        every { automaticDebitInstructionRepository.existsByChargeIdAndStatusIn(any(), any()) } returns false
        every { lateFeeService.applyIfDue(1L, any(), any()) } answers { currentCharge }
        every { paymentService.create(any(), any(), any()) } answers {
            val request = secondArg<com.sigep.payments.application.dto.CreatePaymentRequest>()
            val id = nextPaymentId++
            payments[id] = payment(id, request.amount)
            detail(id, request.amount, PaymentStatus.PENDING)
        }
        every { paymentService.confirm(any(), any(), any(), any()) } answers {
            val id = firstArg<Long>()
            val paid = requireNotNull(payments[id]).copy(status = PaymentStatus.PAID, paymentMethod = PaymentMethod.CASH)
            payments[id] = paid
            detail(id, paid.amount, PaymentStatus.PAID)
        }
        every { paymentRepository.getReferenceById(any()) } answers { requireNotNull(payments[firstArg()]) }
        every { allocationRepository.save(any()) } answers {
            firstArg<PaymentAllocation>().also(allocations::add)
        }
        every { chargeRepository.save(any()) } answers {
            firstArg<BillingCharge>().also { currentCharge = it }
        }
        every { profileRepository.findByAccountId(10L) } returns Optional.of(profile)
        every { invoiceRepository.findByChargeId(1L) } returns Optional.empty()
        every { allocationRepository.findByChargeIdOrderByCreatedAtAsc(1L) } answers { allocations.toList() }
        every { automaticDebitInstructionRepository.findByChargeIdOrderByCreatedAtDesc(1L) } returns emptyList()
        every { fiscalDecisionRepository.save(any()) } answers { firstArg() }

        val first = service.registerChargePayment(1L, "partial-one", paymentRequest("40.00"), 7L)
        val second = service.registerChargePayment(1L, "partial-two", paymentRequest("60.00"), 7L)

        assertEquals(BillingChargeStatus.PARTIALLY_PAID, first.charge.status)
        assertEquals(java.math.BigDecimal("40.00"), first.charge.paidAmount)
        assertEquals(BillingChargeStatus.PAID, second.charge.status)
        assertEquals(java.math.BigDecimal.ZERO.setScale(2), second.charge.outstandingAmount)
        assertEquals(2, allocations.size)
    }

    @Test
    fun `payment cannot exceed outstanding balance`() {
        val charge = simpleCharge()
        every { chargeRepository.findByIdForUpdate(1L) } returns Optional.of(charge)
        every { paymentRepository.findByCreationKey(any()) } returns Optional.empty()
        every { automaticDebitInstructionRepository.existsByChargeIdAndStatusIn(any(), any()) } returns false
        every { lateFeeService.applyIfDue(1L, any(), any()) } returns charge

        assertFailsWith<ValidationException> {
            service.registerChargePayment(1L, "overpayment", paymentRequest("100.01"), 7L)
        }
        verify(exactly = 0) { paymentService.create(any(), any(), any()) }
    }

    @Test
    fun `manual payment is blocked while an automatic debit outcome is unresolved`() {
        val charge = simpleCharge()
        every { chargeRepository.findByIdForUpdate(1L) } returns Optional.of(charge)
        every { paymentRepository.findByCreationKey(any()) } returns Optional.empty()
        every { automaticDebitInstructionRepository.existsByChargeIdAndStatusIn(1L, any()) } returns true

        assertFailsWith<ResourceConflictException> {
            service.registerChargePayment(1L, "manual-during-debit", paymentRequest("10.00"), 7L)
        }

        verify(exactly = 0) { lateFeeService.applyIfDue(any(), any(), any()) }
        verify(exactly = 0) { paymentService.create(any(), any(), any()) }
    }

    @Test
    fun `settling with exclude charge records an audited decision without creating an invoice`() {
        val profile = BillingProfile(
            id = 30L,
            account = simpleCharge().account,
            receiverName = "Tutor",
            status = BillingProfileStatus.INCOMPLETE
        )
        var currentCharge = simpleCharge()
        val allocations = mutableListOf<PaymentAllocation>()
        val decision = slot<BillingChargeFiscalDecision>()
        every { chargeRepository.findByIdForUpdate(1L) } answers { Optional.of(currentCharge) }
        every { paymentRepository.findByCreationKey(any()) } returns Optional.empty()
        every { automaticDebitInstructionRepository.existsByChargeIdAndStatusIn(any(), any()) } returns false
        every { lateFeeService.applyIfDue(1L, any(), any()) } answers { currentCharge }
        every { invoiceRepository.findByChargeId(1L) } returns Optional.empty()
        every { paymentService.create(any(), any(), any()) } returns detail(100L, java.math.BigDecimal("100.00"), PaymentStatus.PENDING)
        every { paymentService.confirm(100L, any(), any(), 7L) } returns detail(100L, java.math.BigDecimal("100.00"), PaymentStatus.PAID)
        every { paymentRepository.getReferenceById(100L) } returns payment(100L, java.math.BigDecimal("100.00"))
        every { allocationRepository.save(any()) } answers { firstArg<PaymentAllocation>().also(allocations::add) }
        every { chargeRepository.save(any()) } answers { firstArg<BillingCharge>().also { currentCharge = it } }
        every { fiscalDecisionRepository.save(capture(decision)) } answers { decision.captured }
        every { profileRepository.findByAccountId(10L) } returns Optional.of(profile)
        every { allocationRepository.findByChargeIdOrderByCreatedAtAsc(1L) } answers { allocations }
        every { automaticDebitInstructionRepository.findByChargeIdOrderByCreatedAtDesc(1L) } returns emptyList()

        val result = service.registerChargePayment(
            1L,
            "exclude-final",
            RegisterChargePaymentRequest(
                amount = java.math.BigDecimal("100.00"),
                confirmation = ConfirmPaymentRequest(LocalDate.now(), PaymentMethod.CASH, "Tutor"),
                fiscalClosure = FiscalClosure.EXCLUDE_CHARGE,
                fiscalReason = "Convenio institucional validado"
            ),
            7L
        )

        assertEquals(BillingChargeStatus.PAID, result.charge.status)
        assertEquals(BillingChargeFiscalDisposition.EXCLUDED, result.charge.fiscalDisposition)
        assertEquals(FiscalClosure.EXCLUDE_CHARGE, decision.captured.decision)
        assertEquals("Convenio institucional validado", decision.captured.reason)
        verify(exactly = 0) { invoiceRepository.save(any()) }
        verify(exactly = 0) { runRepository.save(any()) }
    }

    @Test
    fun `charge with an existing invoice cannot be fiscally excluded`() {
        val charge = simpleCharge()
        every { chargeRepository.findByIdForUpdate(1L) } returns Optional.of(charge)
        every { paymentRepository.findByCreationKey(any()) } returns Optional.empty()
        every { automaticDebitInstructionRepository.existsByChargeIdAndStatusIn(any(), any()) } returns false
        every { lateFeeService.applyIfDue(1L, any(), any()) } returns charge
        every { invoiceRepository.findByChargeId(1L) } returns Optional.of(mockk(relaxed = true))

        assertFailsWith<ResourceConflictException> {
            service.registerChargePayment(
                1L,
                "exclude-invoiced",
                RegisterChargePaymentRequest(
                    amount = java.math.BigDecimal("100.00"),
                    confirmation = ConfirmPaymentRequest(LocalDate.now(), PaymentMethod.CASH, "Tutor"),
                    fiscalClosure = FiscalClosure.EXCLUDE_CHARGE,
                    fiscalReason = "No permitido"
                ),
                7L
            )
        }

        verify(exactly = 0) { paymentService.create(any(), any(), any()) }
    }

    private fun paymentRequest(amount: String) = RegisterChargePaymentRequest(
        amount = java.math.BigDecimal(amount),
        confirmation = ConfirmPaymentRequest(LocalDate.now(), PaymentMethod.CASH, "Tutor"),
        fiscalClosure = FiscalClosure.KEEP_PENDING
    )

    private fun simpleCharge() = BillingCharge(
        id = 1L,
        account = BillingAccount(id = 10L, guardianUserId = 20L, displayName = "Tutor"),
        studentName = "Ana",
        sourceType = "TUITION_LEDGER",
        sourceId = 99L,
        concept = "MONTHLY_FEE",
        description = "Cuota",
        baseAmount = java.math.BigDecimal("100.00"),
        amount = java.math.BigDecimal("100.00"),
        dueDate = LocalDate.now().plusDays(1)
    )

    private fun payment(id: Long, amount: java.math.BigDecimal) = Payment(
        id = id,
        studentId = null,
        amount = amount,
        concept = "Cuota",
        dueDate = LocalDate.now(),
        paymentMethod = null,
        receiptNumber = null,
        notes = null
    )

    private fun detail(id: Long, amount: java.math.BigDecimal, status: PaymentStatus) = PaymentDetailDto(
        payment = PaymentDto(
            id = id,
            studentId = null,
            amount = amount,
            currency = "ARS",
            concept = "Cuota",
            paymentDate = LocalDate.now(),
            dueDate = LocalDate.now(),
            status = status,
            paymentMethod = if (status == PaymentStatus.PAID) PaymentMethod.CASH else null,
            receiptNumber = if (status == PaymentStatus.PAID) "X-$id" else null,
            externalReference = null,
            notes = null,
            confirmedAt = null,
            confirmedBy = null,
            createdAt = java.time.LocalDateTime.now(),
            updatedAt = java.time.LocalDateTime.now()
        ),
        receipt = null,
        invoice = null
    )
}
