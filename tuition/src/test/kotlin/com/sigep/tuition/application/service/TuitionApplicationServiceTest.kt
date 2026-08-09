package com.sigep.tuition.application.service

import com.sigep.common.application.exception.BusinessException
import com.sigep.common.application.service.BillingChargeProvider
import com.sigep.common.application.service.CourseEnrollmentCommandProvider
import com.sigep.common.application.service.CourseEnrollmentResult
import com.sigep.common.application.service.CourseSeatAvailability
import com.sigep.common.application.service.GuardianAccountInfo
import com.sigep.common.application.service.GuardianAccountProvider
import com.sigep.common.application.service.StudentProfileInfo
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.tuition.application.dto.CreateTuitionApplicationRequest
import com.sigep.tuition.application.dto.CreateTuitionEnrollmentChargeRequest
import com.sigep.tuition.application.dto.TuitionAcademicAssignmentRequest
import com.sigep.tuition.application.dto.TuitionPlacementRequest
import com.sigep.tuition.domain.model.*
import com.sigep.tuition.domain.repository.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TuitionApplicationServiceTest {
    private val applicationRepository = mockk<TuitionApplicationRepository>()
    private val academicYearRepository = mockk<TuitionAcademicYearRepository>()
    private val levelRepository = mockk<TuitionLevelRepository>()
    private val progressionRepository = mockk<TuitionLevelProgressionRepository>()
    private val feePlanRepository = mockk<TuitionFeePlanRepository>()
    private val enrollmentPolicyRepository = mockk<TuitionEnrollmentFeePolicyRepository>()
    private val discountRepository = mockk<TuitionDiscountRepository>()
    private val ledgerRepository = mockk<TuitionLedgerEntryRepository>()
    private val placementRepository = mockk<TuitionPlacementAssessmentRepository>()
    private val studentProvider = mockk<StudentProfileProvider>()
    private val courseProvider = mockk<CourseEnrollmentCommandProvider>()
    private val guardianProvider = mockk<GuardianAccountProvider>()
    private val billingProvider = mockk<BillingChargeProvider>(relaxed = true)
    private lateinit var service: TuitionApplicationService

    private val year = TuitionAcademicYear(
        id = 1, name = "2027", startDate = LocalDate.of(2027, 3, 1),
        firstTermStartDate = LocalDate.of(2027, 3, 1), firstTermEndDate = LocalDate.of(2027, 7, 15),
        secondTermStartDate = LocalDate.of(2027, 8, 1), secondTermEndDate = LocalDate.of(2027, 12, 15),
        endDate = LocalDate.of(2027, 12, 20), status = TuitionAcademicYearStatus.OPEN
    )
    private val level = TuitionLevel(id = 2, code = "INTERMEDIATE", name = "Intermediate", segment = TuitionSegment.TEENS, levelOrder = 2)
    private val plan = TuitionFeePlan(
        id = 3, academicYear = year, name = "Plan 2027", segment = TuitionSegment.TEENS, level = level,
        monthlyFee = BigDecimal("20000.00"), installments = 3,
        validFrom = LocalDate.now().minusDays(1), status = TuitionFeePlanStatus.ACTIVE
    )
    private val policy = TuitionEnrollmentFeePolicy(
        id = 4, name = "Matricula general", amount = BigDecimal("10000.00"), validFrom = LocalDate.now().minusDays(1),
        status = TuitionEnrollmentFeePolicyStatus.ACTIVE, defaultPolicy = true
    )

    @BeforeEach
    fun setUp() {
        service = TuitionApplicationService(
            applicationRepository, academicYearRepository, levelRepository, progressionRepository,
            feePlanRepository, enrollmentPolicyRepository, discountRepository,
            ledgerRepository, placementRepository, studentProvider, courseProvider, guardianProvider, billingProvider
        )
        every { guardianProvider.getGuardianAccount(10) } returns guardianInfo()
        every { placementRepository.findByApplicationId(any()) } returns Optional.empty()
        every { ledgerRepository.findByApplicationId(any()) } returns emptyList()
        every { studentProvider.getStudentProfile(any()) } returns studentInfo()
    }

    @Test
    fun `guardian request does not assign academic catalog values`() {
        every { applicationRepository.save(any()) } answers { firstArg<TuitionApplication>().copy(id = 100) }

        val result = service.createApplication(10, newStudentRequest())

        assertEquals(TuitionApplicationStatus.SUBMITTED, result.status)
        assertNull(result.assignedAcademicYearId)
        assertNull(result.assignedLevelId)
        assertNull(result.assignedCourseId)
        verify(exactly = 0) { academicYearRepository.findById(any()) }
        verify(exactly = 0) { courseProvider.getCourseSeatAvailability(any()) }
    }

    @Test
    fun `admin creates enrollment charge once using separate policy`() {
        var current = application(status = TuitionApplicationStatus.SUBMITTED)
        var ledger: TuitionLedgerEntry? = null
        every { applicationRepository.findById(100) } answers { Optional.of(current) }
        every { applicationRepository.save(any()) } answers { current = firstArg(); current }
        every { enrollmentPolicyRepository.findActiveCandidates(any(), any()) } returns listOf(policy)
        every { ledgerRepository.findByApplicationIdAndConcept(100, TuitionLedgerConcept.TUITION_ENROLLMENT) } answers { listOfNotNull(ledger) }
        every { ledgerRepository.save(any()) } answers { ledger = firstArg<TuitionLedgerEntry>().copy(id = 500); ledger!! }
        every { ledgerRepository.findByApplicationId(100) } answers { listOfNotNull(ledger) }

        val first = service.createEnrollmentCharge(100, CreateTuitionEnrollmentChargeRequest())
        val second = service.createEnrollmentCharge(100, CreateTuitionEnrollmentChargeRequest())

        assertEquals(TuitionApplicationStatus.PAYMENT_PENDING, first.status)
        assertEquals(BigDecimal("10000.00"), first.ledgerEntries.single().netAmount)
        assertEquals(1, second.ledgerEntries.size)
        verify(exactly = 1) { ledgerRepository.save(any()) }
    }

    @Test
    fun `academic assignment waits when selected course has no seats`() {
        val placement = TuitionPlacementAssessment(
            id = 9, application = application(status = TuitionApplicationStatus.READY_FOR_ACADEMIC_ASSIGNMENT, assigned = false),
            status = TuitionPlacementStatus.COMPLETED, recommendedLevel = level, evaluatorUserId = 1
        )
        var current = placement.application
        every { applicationRepository.findById(100) } answers { Optional.of(current) }
        every { applicationRepository.save(any()) } answers { current = firstArg(); current }
        every { placementRepository.findByApplicationId(100) } returns Optional.of(placement)
        every { ledgerRepository.existsByApplicationIdAndConceptAndStatus(100, TuitionLedgerConcept.TUITION_ENROLLMENT, TuitionLedgerStatus.PAID) } returns true
        every { academicYearRepository.findById(1) } returns Optional.of(year)
        every { levelRepository.findById(2) } returns Optional.of(level)
        every { feePlanRepository.findById(3) } returns Optional.of(plan)
        every { courseProvider.getCourseSeatAvailability(99) } returns availability(availableSeats = 0, enrollmentOpen = false)

        val result = service.assignAcademicPlacement(100, 1, assignmentRequest())

        assertEquals(TuitionApplicationStatus.WAITLISTED, result.status)
        verify(exactly = 0) { courseProvider.createActiveEnrollment(any(), any(), any()) }
    }

    @Test
    fun `late enrollment generates only installments from assignment month through plan end`() {
        val today = LocalDate.now()
        val currentYear = TuitionAcademicYear(
            id = 1,
            name = today.year.toString(),
            startDate = LocalDate.of(today.year, 1, 1),
            firstTermStartDate = LocalDate.of(today.year, 1, 1),
            firstTermEndDate = LocalDate.of(today.year, 6, 30),
            secondTermStartDate = LocalDate.of(today.year, 7, 1),
            secondTermEndDate = LocalDate.of(today.year, 12, 15),
            endDate = LocalDate.of(today.year, 12, 31),
            status = TuitionAcademicYearStatus.OPEN
        )
        val currentPlan = plan.copy(
            academicYear = currentYear,
            installments = 9,
            monthlyDueDay = 10,
            validFrom = currentYear.startDate,
            validTo = currentYear.endDate
        )
        val ready = application(status = TuitionApplicationStatus.READY_FOR_ACADEMIC_ASSIGNMENT).copy(
            academicYear = currentYear,
            assignedLevel = level,
            assignedCourseId = 99,
            feePlan = currentPlan
        )
        val placement = TuitionPlacementAssessment(
            id = 9,
            application = ready,
            status = TuitionPlacementStatus.COMPLETED,
            recommendedLevel = level,
            evaluatorUserId = 1
        )
        val savedMonthly = mutableListOf<TuitionLedgerEntry>()
        val entriesSlot = io.mockk.slot<Iterable<TuitionLedgerEntry>>()
        var current = ready
        every { applicationRepository.findById(100) } answers { Optional.of(current) }
        every { applicationRepository.save(any()) } answers { current = firstArg(); current }
        every { placementRepository.findByApplicationId(100) } returns Optional.of(placement)
        every { ledgerRepository.existsByApplicationIdAndConceptAndStatus(100, TuitionLedgerConcept.TUITION_ENROLLMENT, TuitionLedgerStatus.PAID) } returns true
        every { academicYearRepository.findById(1) } returns Optional.of(currentYear)
        every { levelRepository.findById(2) } returns Optional.of(level)
        every { feePlanRepository.findById(3) } returns Optional.of(currentPlan)
        every { courseProvider.getCourseSeatAvailability(99) } returns availability(availableSeats = 5, enrollmentOpen = true)
        every { courseProvider.createActiveEnrollment(20, 99, any()) } returns CourseEnrollmentResult(77, 20, 99, "Course", "INTERMEDIATE")
        every { studentProvider.updateCurrentLevel(20, level.code) } returns studentInfo()
        every { discountRepository.findActiveCandidates(20, any()) } returns emptyList()
        every { ledgerRepository.findByApplicationIdAndConcept(100, TuitionLedgerConcept.MONTHLY_FEE) } returns emptyList()
        every { ledgerRepository.saveAll(capture(entriesSlot)) } answers {
            savedMonthly.clear()
            savedMonthly += entriesSlot.captured.mapIndexed { index, entry -> entry.copy(id = 600L + index) }
            savedMonthly
        }
        every { ledgerRepository.findByApplicationId(100) } answers { savedMonthly }

        val result = service.assignAcademicPlacement(100, 1, assignmentRequest())

        val startMonth = YearMonth.from(today)
        val endMonth = YearMonth.of(today.year, 12)
        val expectedCount = minOf(9, (endMonth.year - startMonth.year) * 12 + endMonth.monthValue - startMonth.monthValue + 1)
        val expectedLastMonth = minOf(startMonth.plusMonths(8), endMonth)
        assertEquals(TuitionApplicationStatus.APPROVED, result.status)
        assertEquals(expectedCount, savedMonthly.size)
        assertEquals(startMonth, YearMonth.from(savedMonthly.first().dueDate))
        assertEquals(expectedLastMonth, YearMonth.from(savedMonthly.last().dueDate))
        assertEquals(maxOf(startMonth.atDay(10), today), savedMonthly.first().dueDate)
    }

    @Test
    fun `placement is recorded only after enrollment payment`() {
        var current = application(status = TuitionApplicationStatus.ENROLLED_PENDING_PLACEMENT)
        var savedPlacement: TuitionPlacementAssessment? = null
        every { applicationRepository.findById(100) } answers { Optional.of(current) }
        every { applicationRepository.save(any()) } answers { current = firstArg(); current }
        every { ledgerRepository.existsByApplicationIdAndConceptAndStatus(100, TuitionLedgerConcept.TUITION_ENROLLMENT, TuitionLedgerStatus.PAID) } returns true
        every { levelRepository.findById(2) } returns Optional.of(level)
        every { placementRepository.findByApplicationId(100) } answers { Optional.ofNullable(savedPlacement) }
        every { placementRepository.save(any()) } answers {
            savedPlacement = firstArg<TuitionPlacementAssessment>().copy(id = 9)
            savedPlacement!!
        }

        val result = service.recordPlacement(
            100,
            1,
            TuitionPlacementRequest(TuitionPlacementStatus.COMPLETED, recommendedLevelId = 2, notes = "Entrevista completa")
        )

        assertEquals(TuitionApplicationStatus.READY_FOR_ACADEMIC_ASSIGNMENT, result.status)
        assertEquals(TuitionPlacementStatus.COMPLETED, result.placement?.status)
        assertEquals(2, result.placement?.recommendedLevelId)
    }

    @Test
    fun `academic assignment is blocked after payment reversal`() {
        val app = application(status = TuitionApplicationStatus.PAYMENT_PENDING, assigned = false)
        every { applicationRepository.findById(100) } returns Optional.of(app)

        assertFailsWith<BusinessException> { service.assignAcademicPlacement(100, 1, assignmentRequest()) }
    }

    private fun newStudentRequest() = CreateTuitionApplicationRequest(
        applicationType = TuitionApplicationType.NEW_STUDENT,
        studentFirstName = "Jane", studentLastName = "Doe", studentEmail = "jane@example.com",
        studentDocumentNumber = "12345678", studentDateOfBirth = LocalDate.of(2012, 1, 1),
        studentAddress = "Main 123", studentPhoneNumber = "1111", studentEmergencyContact = "Parent"
    )

    private fun application(status: TuitionApplicationStatus, assigned: Boolean = false) = TuitionApplication(
        id = 100, guardianUserId = 10, studentId = 20,
        studentFirstName = "Jane", studentLastName = "Doe", studentEmail = "jane@example.com",
        studentDocumentNumber = "12345678", studentDateOfBirth = LocalDate.of(2012, 1, 1),
        studentAddress = "Main 123", studentPhoneNumber = "1111", studentEmergencyContact = "Parent",
        academicYear = year.takeIf { assigned }, assignedLevel = level.takeIf { assigned },
        assignedCourseId = 99L.takeIf { assigned }, feePlan = plan.takeIf { assigned },
        enrollmentFeePolicy = policy, applicationType = TuitionApplicationType.NEW_STUDENT, status = status
    )

    private fun assignmentRequest() = TuitionAcademicAssignmentRequest(1, 2, 99, 3)
    private fun availability(availableSeats: Int, enrollmentOpen: Boolean) = CourseSeatAvailability(99, "Course", "INTERMEDIATE", 10, 10 - availableSeats, availableSeats, enrollmentOpen)
    private fun guardianInfo() = GuardianAccountInfo(
        id = 10,
        username = "guardian",
        email = "g@example.com",
        firstName = "G",
        lastName = "U",
        status = "ACTIVE",
        active = true
    )
    private fun studentInfo() = StudentProfileInfo(20, 10, "Jane", "Doe", "jane@example.com", "123", LocalDate.of(2012, 1, 1), "Main", "111", "Parent", "PENDING_PLACEMENT", true)
}
