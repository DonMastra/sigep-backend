package com.sigep.tuition.application.service

import com.sigep.common.application.service.BillingChargeSettlement
import com.sigep.tuition.domain.model.TuitionAcademicYear
import com.sigep.tuition.domain.model.TuitionAcademicYearStatus
import com.sigep.tuition.domain.model.TuitionApplication
import com.sigep.tuition.domain.model.TuitionApplicationStatus
import com.sigep.tuition.domain.model.TuitionApplicationType
import com.sigep.tuition.domain.model.TuitionFeePlan
import com.sigep.tuition.domain.model.TuitionFeePlanStatus
import com.sigep.tuition.domain.model.TuitionLedgerConcept
import com.sigep.tuition.domain.model.TuitionLedgerEntry
import com.sigep.tuition.domain.model.TuitionLedgerStatus
import com.sigep.tuition.domain.model.TuitionLevel
import com.sigep.tuition.domain.model.TuitionSegment
import com.sigep.tuition.domain.repository.TuitionApplicationRepository
import com.sigep.tuition.domain.repository.TuitionLedgerEntryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals

class TuitionBillingSettlementObserverTest {

    private val ledgerRepository = mockk<TuitionLedgerEntryRepository>()
    private val applicationRepository = mockk<TuitionApplicationRepository>()
    private val observer = TuitionBillingSettlementObserver(ledgerRepository, applicationRepository)

    @Test
    fun `paid enrollment charge keeps resolved student and waits for placement`() {
        val application = application()
        val entry = TuitionLedgerEntry(
            id = 55L,
            application = application,
            concept = TuitionLedgerConcept.TUITION_ENROLLMENT,
            grossAmount = BigDecimal("10000.00"),
            netAmount = BigDecimal("10000.00"),
            dueDate = LocalDate.of(2027, 2, 20),
            status = TuitionLedgerStatus.PENDING
        )
        val savedLedger = slot<TuitionLedgerEntry>()
        val savedApplication = slot<TuitionApplication>()
        every { ledgerRepository.findById(55L) } returns Optional.of(entry)
        every { ledgerRepository.save(capture(savedLedger)) } answers { savedLedger.captured }
        every { applicationRepository.save(capture(savedApplication)) } answers { savedApplication.captured }

        observer.onChargeSettlementChanged(
            BillingChargeSettlement(
                sourceType = "TUITION_LEDGER",
                sourceId = 55L,
                paymentId = 900L,
                baseAmount = BigDecimal("10000.00"),
                lateFeeAmount = BigDecimal.ZERO,
                paidAmount = BigDecimal("10000.00"),
                outstandingAmount = BigDecimal.ZERO,
                status = "PAID"
            )
        )

        assertEquals(TuitionLedgerStatus.PAID, savedLedger.captured.status)
        assertEquals("PAYMENT-900", savedLedger.captured.billingReference)
        assertEquals(20L, savedApplication.captured.studentId)
        assertEquals(TuitionApplicationStatus.ENROLLED_PENDING_PLACEMENT, savedApplication.captured.status)
    }

    @Test
    fun `ignores charges owned by another source`() {
        observer.onChargeSettlementChanged(
            BillingChargeSettlement(
                sourceType = "OTHER",
                sourceId = 55L,
                paymentId = 900L,
                baseAmount = BigDecimal.ONE,
                lateFeeAmount = BigDecimal.ZERO,
                paidAmount = BigDecimal.ONE,
                outstandingAmount = BigDecimal.ZERO,
                status = "PAID"
            )
        )

        verify(exactly = 0) { ledgerRepository.findById(any()) }
    }

    @Test
    fun `partial enrollment payment keeps application pending`() {
        val application = application()
        val entry = TuitionLedgerEntry(
            id = 55L,
            application = application,
            concept = TuitionLedgerConcept.TUITION_ENROLLMENT,
            grossAmount = BigDecimal("10000.00"),
            netAmount = BigDecimal("10000.00"),
            dueDate = LocalDate.of(2027, 2, 20)
        )
        val savedLedger = slot<TuitionLedgerEntry>()
        every { ledgerRepository.findById(55L) } returns Optional.of(entry)
        every { ledgerRepository.save(capture(savedLedger)) } answers { savedLedger.captured }

        observer.onChargeSettlementChanged(
            BillingChargeSettlement(
                sourceType = "TUITION_LEDGER",
                sourceId = 55L,
                paymentId = 901L,
                baseAmount = BigDecimal("10000.00"),
                lateFeeAmount = BigDecimal.ZERO,
                paidAmount = BigDecimal("4000.00"),
                outstandingAmount = BigDecimal("6000.00"),
                status = "PARTIALLY_PAID"
            )
        )

        assertEquals(TuitionLedgerStatus.PARTIALLY_PAID, savedLedger.captured.status)
        assertEquals(BigDecimal("4000.00"), savedLedger.captured.paidAmount)
        verify(exactly = 0) { applicationRepository.save(any()) }
    }

    @Test
    fun `payment reversal before assignment reopens the application`() {
        val application = application(
            status = TuitionApplicationStatus.READY_FOR_ACADEMIC_ASSIGNMENT,
            studentId = 20L
        )
        val entry = TuitionLedgerEntry(
            id = 55L,
            application = application,
            concept = TuitionLedgerConcept.TUITION_ENROLLMENT,
            grossAmount = BigDecimal("10000.00"),
            netAmount = BigDecimal("10000.00"),
            paidAmount = BigDecimal("10000.00"),
            dueDate = LocalDate.of(2027, 2, 20),
            status = TuitionLedgerStatus.PAID
        )
        val savedApplication = slot<TuitionApplication>()
        every { ledgerRepository.findById(55L) } returns Optional.of(entry)
        every { ledgerRepository.save(any()) } answers { firstArg() }
        every { applicationRepository.save(capture(savedApplication)) } answers { savedApplication.captured }

        observer.onChargeSettlementChanged(
            BillingChargeSettlement(
                sourceType = "TUITION_LEDGER",
                sourceId = 55L,
                paymentId = 900L,
                baseAmount = BigDecimal("10000.00"),
                lateFeeAmount = BigDecimal.ZERO,
                paidAmount = BigDecimal.ZERO,
                outstandingAmount = BigDecimal("10000.00"),
                status = "REVERSED"
            )
        )

        assertEquals(TuitionApplicationStatus.PAYMENT_PENDING, savedApplication.captured.status)
        assertEquals(20L, savedApplication.captured.studentId)
    }

    @Test
    fun `payment reversal after assignment preserves enrollment history`() {
        val application = application(status = TuitionApplicationStatus.APPROVED, studentId = 20L)
            .copy(enrollmentId = 300L)
        val entry = TuitionLedgerEntry(
            id = 55L,
            application = application,
            concept = TuitionLedgerConcept.TUITION_ENROLLMENT,
            grossAmount = BigDecimal("10000.00"),
            netAmount = BigDecimal("10000.00"),
            paidAmount = BigDecimal("10000.00"),
            dueDate = LocalDate.of(2027, 2, 20),
            status = TuitionLedgerStatus.PAID
        )
        val savedApplication = slot<TuitionApplication>()
        every { ledgerRepository.findById(55L) } returns Optional.of(entry)
        every { ledgerRepository.save(any()) } answers { firstArg() }
        every { applicationRepository.save(capture(savedApplication)) } answers { savedApplication.captured }

        observer.onChargeSettlementChanged(
            BillingChargeSettlement(
                sourceType = "TUITION_LEDGER",
                sourceId = 55L,
                paymentId = 900L,
                baseAmount = BigDecimal("10000.00"),
                lateFeeAmount = BigDecimal.ZERO,
                paidAmount = BigDecimal.ZERO,
                outstandingAmount = BigDecimal("10000.00"),
                status = "REVERSED"
            )
        )

        assertEquals(TuitionApplicationStatus.APPROVED, savedApplication.captured.status)
        assertEquals(300L, savedApplication.captured.enrollmentId)
    }

    private fun application(
        status: TuitionApplicationStatus = TuitionApplicationStatus.PAYMENT_PENDING,
        studentId: Long? = 20L
    ): TuitionApplication {
        val year = TuitionAcademicYear(
            id = 1L,
            name = "2027",
            startDate = LocalDate.of(2027, 3, 1),
            firstTermStartDate = LocalDate.of(2027, 3, 1),
            firstTermEndDate = LocalDate.of(2027, 7, 15),
            secondTermStartDate = LocalDate.of(2027, 8, 1),
            secondTermEndDate = LocalDate.of(2027, 12, 15),
            endDate = LocalDate.of(2027, 12, 20),
            status = TuitionAcademicYearStatus.OPEN
        )
        val level = TuitionLevel(
            id = 2L,
            code = "INTERMEDIATE",
            name = "Intermediate",
            segment = TuitionSegment.TEENS,
            levelOrder = 2
        )
        val plan = TuitionFeePlan(
            id = 3L,
            academicYear = year,
            name = "Plan",
            segment = TuitionSegment.TEENS,
            level = level,
            monthlyFee = BigDecimal("20000.00"),
            installments = 10,
            validFrom = LocalDate.of(2027, 1, 1),
            status = TuitionFeePlanStatus.ACTIVE
        )
        return TuitionApplication(
            id = 123L,
            guardianUserId = 10L,
            studentId = studentId,
            studentFirstName = "Jane",
            studentLastName = "Doe",
            studentEmail = "jane@example.com",
            studentDocumentNumber = "12345678",
            studentDateOfBirth = LocalDate.of(2012, 1, 1),
            studentAddress = "Main 123",
            studentPhoneNumber = "1111",
            studentEmergencyContact = "Parent",
            academicYear = year,
            assignedLevel = level,
            assignedCourseId = 99L,
            applicationType = TuitionApplicationType.NEW_STUDENT,
            status = status,
            feePlan = plan
        )
    }

}
