package com.sigep.payments.application.service

import com.sigep.common.application.service.BillingChargeSettlementObserver
import com.sigep.payments.domain.model.BillingAccount
import com.sigep.payments.domain.model.BillingCharge
import com.sigep.payments.domain.model.BillingChargeAdjustment
import com.sigep.payments.domain.model.BillingChargeAdjustmentSource
import com.sigep.payments.domain.model.BillingChargeAdjustmentStatus
import com.sigep.payments.domain.model.BillingChargeAdjustmentType
import com.sigep.payments.domain.model.BillingChargeStatus
import com.sigep.payments.domain.repository.BillingChargeAdjustmentRepository
import com.sigep.payments.domain.repository.BillingChargeRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals

class BillingLateFeeServiceTest {
    private val chargeRepository = mockk<BillingChargeRepository>()
    private val adjustmentRepository = mockk<BillingChargeAdjustmentRepository>()
    private val invoiceRepository = mockk<FiscalInvoiceRepository>()
    private val service = BillingLateFeeService(
        chargeRepository,
        adjustmentRepository,
        invoiceRepository,
        emptyList<BillingChargeSettlementObserver>()
    )

    @Test
    fun `does not apply late fee on due date`() {
        val charge = charge(dueDate = LocalDate.now())
        every { chargeRepository.findByIdForUpdate(1L) } returns Optional.of(charge)

        val result = service.applyIfDue(1L, BillingChargeAdjustmentSource.PAYMENT, 9L)

        assertEquals(BigDecimal("100.00"), result.amount)
        verify(exactly = 0) { adjustmentRepository.save(any()) }
        verify(exactly = 0) { chargeRepository.save(any()) }
    }

    @Test
    fun `applies one time fee over principal pending at expiration with half even money`() {
        val charge = charge(
            dueDate = LocalDate.now().minusDays(1),
            paidAmount = BigDecimal("40.00")
        )
        val savedAdjustment = slot<BillingChargeAdjustment>()
        every { chargeRepository.findByIdForUpdate(1L) } returns Optional.of(charge)
        every { invoiceRepository.findByChargeId(1L) } returns Optional.empty()
        every {
            adjustmentRepository.findByChargeIdAndTypeAndStatus(
                1L,
                BillingChargeAdjustmentType.LATE_FEE,
                BillingChargeAdjustmentStatus.ACTIVE
            )
        } returns Optional.empty()
        every { adjustmentRepository.save(capture(savedAdjustment)) } answers { savedAdjustment.captured }
        every { chargeRepository.save(any()) } answers { firstArg() }

        val result = service.applyIfDue(1L, BillingChargeAdjustmentSource.SCHEDULER)

        assertEquals(BigDecimal("60.00"), savedAdjustment.captured.baseAmountSnapshot)
        assertEquals(BigDecimal("6.00"), savedAdjustment.captured.amount)
        assertEquals(BigDecimal("106.00"), result.amount)
    }

    @Test
    fun `rerun does not duplicate an already applied fee`() {
        val charge = charge(
            dueDate = LocalDate.now().minusDays(5),
            lateFeeAppliedAt = LocalDateTime.now().minusDays(4)
        )
        every { chargeRepository.findByIdForUpdate(1L) } returns Optional.of(charge)

        service.applyIfDue(1L, BillingChargeAdjustmentSource.SCHEDULER)

        verify(exactly = 0) { adjustmentRepository.save(any()) }
        verify(exactly = 0) { chargeRepository.save(any()) }
    }

    private fun charge(
        dueDate: LocalDate,
        paidAmount: BigDecimal = BigDecimal.ZERO,
        lateFeeAppliedAt: LocalDateTime? = null
    ) = BillingCharge(
        id = 1L,
        account = BillingAccount(id = 2L, guardianUserId = 3L, displayName = "Tutor"),
        studentName = "Estudiante",
        sourceType = "TUITION_LEDGER",
        sourceId = 4L,
        concept = "MONTHLY_FEE",
        description = "Cuota",
        baseAmount = BigDecimal("100.00"),
        amount = BigDecimal("100.00"),
        paidAmount = paidAmount,
        dueDate = dueDate,
        status = if (paidAmount > BigDecimal.ZERO) BillingChargeStatus.PARTIALLY_PAID else BillingChargeStatus.OPEN,
        lateFeePercentage = BigDecimal("10.00"),
        lateFeeEligible = true,
        lateFeeAppliedAt = lateFeeAppliedAt
    )
}
