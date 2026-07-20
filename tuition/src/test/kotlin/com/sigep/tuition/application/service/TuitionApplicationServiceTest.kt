package com.sigep.tuition.application.service

import com.sigep.common.application.service.CourseEnrollmentCommandProvider
import com.sigep.common.application.service.CourseEnrollmentResult
import com.sigep.common.application.service.CourseSeatAvailability
import com.sigep.common.application.service.GuardianAccountInfo
import com.sigep.common.application.service.GuardianAccountProvider
import com.sigep.common.application.service.StudentProfileInfo
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.common.application.exception.ValidationException
import com.sigep.tuition.application.dto.CreateTuitionApplicationRequest
import com.sigep.tuition.application.dto.TuitionDecisionRequest
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
import com.sigep.tuition.domain.model.TuitionSeatReservation
import com.sigep.tuition.domain.model.TuitionSeatReservationStatus
import com.sigep.tuition.domain.model.TuitionSegment
import com.sigep.tuition.domain.repository.TuitionAcademicYearRepository
import com.sigep.tuition.domain.repository.TuitionApplicationRepository
import com.sigep.tuition.domain.repository.TuitionDiscountRepository
import com.sigep.tuition.domain.repository.TuitionFeePlanRepository
import com.sigep.tuition.domain.repository.TuitionLedgerEntryRepository
import com.sigep.tuition.domain.repository.TuitionLevelProgressionRepository
import com.sigep.tuition.domain.repository.TuitionLevelRepository
import com.sigep.tuition.domain.repository.TuitionSeatReservationRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TuitionApplicationServiceTest {

    private lateinit var applicationRepository: TuitionApplicationRepository
    private lateinit var academicYearRepository: TuitionAcademicYearRepository
    private lateinit var levelRepository: TuitionLevelRepository
    private lateinit var progressionRepository: TuitionLevelProgressionRepository
    private lateinit var feePlanRepository: TuitionFeePlanRepository
    private lateinit var discountRepository: TuitionDiscountRepository
    private lateinit var seatReservationRepository: TuitionSeatReservationRepository
    private lateinit var ledgerEntryRepository: TuitionLedgerEntryRepository
    private lateinit var studentProfileProvider: StudentProfileProvider
    private lateinit var courseEnrollmentCommandProvider: CourseEnrollmentCommandProvider
    private lateinit var guardianAccountProvider: GuardianAccountProvider
    private lateinit var service: TuitionApplicationService

    private val academicYear = TuitionAcademicYear(
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
    private val level = TuitionLevel(
        id = 2L,
        code = "INTERMEDIATE",
        name = "Intermediate",
        segment = TuitionSegment.TEENS,
        levelOrder = 2
    )
    private val feePlan = TuitionFeePlan(
        id = 3L,
        academicYear = academicYear,
        name = "Teens 2027",
        segment = TuitionSegment.TEENS,
        level = level,
        enrollmentFee = BigDecimal("10000.00"),
        monthlyFee = BigDecimal("20000.00"),
        installments = 3,
        validFrom = LocalDate.now().minusDays(1),
        status = TuitionFeePlanStatus.ACTIVE
    )

    @BeforeEach
    fun setUp() {
        applicationRepository = mockk()
        academicYearRepository = mockk()
        levelRepository = mockk()
        progressionRepository = mockk()
        feePlanRepository = mockk()
        discountRepository = mockk()
        seatReservationRepository = mockk()
        ledgerEntryRepository = mockk()
        studentProfileProvider = mockk()
        courseEnrollmentCommandProvider = mockk()
        guardianAccountProvider = mockk()

        service = TuitionApplicationService(
            applicationRepository,
            academicYearRepository,
            levelRepository,
            progressionRepository,
            feePlanRepository,
            discountRepository,
            seatReservationRepository,
            ledgerEntryRepository,
            studentProfileProvider,
            courseEnrollmentCommandProvider,
            guardianAccountProvider
        )
    }

    @Test
    fun `regular promotion without completed enrollment is rejected`() {
        mockCommonCreationDependencies()
        every { studentProfileProvider.validateGuardianOwnsStudent(10L, 20L) } returns true
        every { courseEnrollmentCommandProvider.getLatestCompletedEnrollment(20L) } returns null

        assertFailsWith<ValidationException> {
            service.createApplication(
                guardianUserId = 10L,
                request = CreateTuitionApplicationRequest(
                    academicYearId = 1L,
                    requestedLevelId = 2L,
                    requestedCourseId = 99L,
                    applicationType = TuitionApplicationType.REGULAR_PROMOTION,
                    studentId = 20L
                )
            )
        }

    }

    @Test
    fun `creates application when legacy A1 level has no explicit course mapping`() {
        mockCommonCreationDependencies()
        val legacyLevel = level.copy(code = "A1", courseLevel = null)
        every { levelRepository.findById(2L) } returns Optional.of(legacyLevel)
        every { courseEnrollmentCommandProvider.getCourseSeatAvailability(99L) } returns seatAvailability().copy(courseLevel = "BEGINNER")
        every { feePlanRepository.findActiveCandidates(1L, TuitionFeePlanStatus.ACTIVE, any()) } returns listOf(feePlan.copy(level = legacyLevel))
        every { applicationRepository.save(any()) } answers { firstArg<TuitionApplication>().copy(id = 124L) }
        every { seatReservationRepository.findByApplicationId(124L) } returns Optional.empty()
        every { ledgerEntryRepository.findByApplicationId(124L) } returns emptyList()

        val response = service.createApplication(
            guardianUserId = 10L,
            request = CreateTuitionApplicationRequest(
                academicYearId = 1L,
                requestedLevelId = 2L,
                requestedCourseId = 99L,
                applicationType = TuitionApplicationType.NEW_STUDENT,
                studentFirstName = "Jane",
                studentLastName = "Doe",
                studentEmail = "jane@example.com",
                studentDocumentNumber = "12345678",
                studentDateOfBirth = LocalDate.of(2012, 1, 1),
                studentAddress = "Main 123",
                studentPhoneNumber = "1111-2222",
                studentEmergencyContact = "Parent"
            )
        )

        assertEquals(TuitionApplicationStatus.SUBMITTED, response.status)
        assertEquals(2L, response.requestedLevelId)
    }

    @Test
    fun `reserve seat creates active reservation and initial mock ledger`() {
        val application = application(status = TuitionApplicationStatus.SUBMITTED)
        var savedReservation: TuitionSeatReservation? = null
        var savedLedger: TuitionLedgerEntry? = null

        every { applicationRepository.findById(123L) } returns Optional.of(application)
        every { courseEnrollmentCommandProvider.getCourseSeatAvailability(99L) } returns seatAvailability()
        every { seatReservationRepository.findByApplicationId(123L) } returnsMany listOf(
            Optional.empty(),
            Optional.ofNullable(savedReservation)
        )
        every { seatReservationRepository.countActiveReservedSeats(99L, any(), any()) } returns 0L
        every { applicationRepository.save(any()) } answers { firstArg<TuitionApplication>() }
        every { seatReservationRepository.save(any()) } answers {
            savedReservation = firstArg<TuitionSeatReservation>().copy(id = 44L)
            savedReservation!!
        }
        every { ledgerEntryRepository.findByApplicationIdAndConcept(123L, TuitionLedgerConcept.TUITION_ENROLLMENT) } returns emptyList()
        every { discountRepository.findActiveCandidates(any(), any()) } returns emptyList()
        every { ledgerEntryRepository.save(any()) } answers {
            savedLedger = firstArg<TuitionLedgerEntry>().copy(id = 55L)
            savedLedger!!
        }
        every { ledgerEntryRepository.findByApplicationId(123L) } answers { listOfNotNull(savedLedger) }
        every { seatReservationRepository.findByApplicationId(123L) } answers { Optional.ofNullable(savedReservation) }

        val response = service.reserveSeat(123L, 10L)

        assertEquals(TuitionApplicationStatus.PAYMENT_PENDING, response.status)
        assertEquals(TuitionSeatReservationStatus.ACTIVE, response.seatReservation!!.status)
        assertEquals(1, response.ledgerEntries.size)
        assertEquals(TuitionLedgerConcept.TUITION_ENROLLMENT, response.ledgerEntries.first().concept)
        assertEquals(TuitionLedgerStatus.MOCK_PENDING, response.ledgerEntries.first().status)
        assertEquals(BigDecimal("10000.00"), response.ledgerEntries.first().netAmount)
    }

    @Test
    fun `approve application creates student enrollment confirms reservation and monthly ledger`() {
        val application = application(status = TuitionApplicationStatus.READY_FOR_ADMIN_APPROVAL, studentId = null)
        val reservation = TuitionSeatReservation(
            id = 44L,
            application = application,
            courseId = 99L,
            expiresAt = LocalDateTime.now().plusHours(1),
            status = TuitionSeatReservationStatus.ACTIVE
        )
        val paidEnrollmentFee = TuitionLedgerEntry(
            id = 55L,
            application = application,
            concept = TuitionLedgerConcept.TUITION_ENROLLMENT,
            grossAmount = BigDecimal("10000.00"),
            netAmount = BigDecimal("10000.00"),
            dueDate = LocalDate.now(),
            status = TuitionLedgerStatus.MOCK_PAID,
            mockReference = "MOCK-TUITION-123-55"
        )
        var confirmedReservation: TuitionSeatReservation? = null
        var savedMonthlyEntries: List<TuitionLedgerEntry> = emptyList()

        every { applicationRepository.findById(123L) } returns Optional.of(application)
        every { seatReservationRepository.findByApplicationId(123L) } returns Optional.of(reservation)
        every {
            ledgerEntryRepository.existsByApplicationIdAndConceptAndStatus(
                123L,
                TuitionLedgerConcept.TUITION_ENROLLMENT,
                TuitionLedgerStatus.MOCK_PAID
            )
        } returns true
        every { guardianAccountProvider.activateGuardianForTuition(10L, 1L, "ok") } returns guardianInfo()
        every { studentProfileProvider.createStudentForTuition(10L, any()) } returns studentInfo(77L)
        every { studentProfileProvider.updateCurrentLevel(77L, "INTERMEDIATE") } returns studentInfo(77L)
        every { courseEnrollmentCommandProvider.createActiveEnrollment(77L, 99L, any()) } returns CourseEnrollmentResult(
            enrollmentId = 88L,
            studentId = 77L,
            courseId = 99L,
            courseName = "Teens A",
            courseLevel = "INTERMEDIATE"
        )
        every { applicationRepository.save(any()) } answers { firstArg<TuitionApplication>() }
        every { ledgerEntryRepository.findByApplicationId(123L) } answers { listOf(paidEnrollmentFee) + savedMonthlyEntries }
        every { ledgerEntryRepository.save(any()) } answers { firstArg<TuitionLedgerEntry>() }
        every { ledgerEntryRepository.findByApplicationIdAndConcept(123L, TuitionLedgerConcept.MONTHLY_FEE) } returns emptyList()
        every { discountRepository.findActiveCandidates(77L, any()) } returns emptyList()
        every { ledgerEntryRepository.saveAll(any<List<TuitionLedgerEntry>>()) } answers {
            savedMonthlyEntries = firstArg<List<TuitionLedgerEntry>>().mapIndexed { index, entry -> entry.copy(id = 100L + index) }
            savedMonthlyEntries
        }
        every { seatReservationRepository.save(any()) } answers {
            confirmedReservation = firstArg()
            confirmedReservation!!
        }
        every { seatReservationRepository.findByApplicationId(123L) } answers { Optional.of(confirmedReservation ?: reservation) }

        val response = service.approveApplication(123L, 1L, TuitionDecisionRequest(adminNotes = "ok"))

        assertEquals(TuitionApplicationStatus.APPROVED, response.status)
        assertEquals(77L, response.studentId)
        assertEquals(88L, response.enrollmentId)
        assertEquals(TuitionSeatReservationStatus.CONFIRMED, response.seatReservation!!.status)
        assertEquals(4, response.ledgerEntries.size)
        assertEquals(listOf("2027-01-20", "2027-02-20", "2027-03-20"), response.ledgerEntries
            .filter { it.concept == TuitionLedgerConcept.MONTHLY_FEE }
            .map { it.dueDate.toString() })
    }

    private fun mockCommonCreationDependencies() {
        every { guardianAccountProvider.getGuardianAccount(10L) } returns guardianInfo()
        every { academicYearRepository.findById(1L) } returns Optional.of(academicYear)
        every { levelRepository.findById(2L) } returns Optional.of(level)
        every { courseEnrollmentCommandProvider.getCourseSeatAvailability(99L) } returns seatAvailability()
        every { feePlanRepository.findActiveCandidates(1L, TuitionFeePlanStatus.ACTIVE, any()) } returns listOf(feePlan)
    }

    private fun application(
        status: TuitionApplicationStatus,
        studentId: Long? = 20L
    ) = TuitionApplication(
        id = 123L,
        guardianUserId = 10L,
        studentId = studentId,
        studentFirstName = if (studentId == null) "Jane" else null,
        studentLastName = if (studentId == null) "Doe" else null,
        studentEmail = if (studentId == null) "jane@example.com" else null,
        studentDocumentNumber = if (studentId == null) "12345678" else null,
        studentDateOfBirth = if (studentId == null) LocalDate.of(2012, 1, 1) else null,
        studentAddress = if (studentId == null) "Main 123" else null,
        studentPhoneNumber = if (studentId == null) "1111-2222" else null,
        studentEmergencyContact = if (studentId == null) "Parent" else null,
        academicYear = academicYear,
        requestedLevel = level,
        requestedCourseId = 99L,
        applicationType = if (studentId == null) TuitionApplicationType.NEW_STUDENT else TuitionApplicationType.REGULAR_PROMOTION,
        status = status,
        feePlan = feePlan
    )

    private fun seatAvailability() = CourseSeatAvailability(
        courseId = 99L,
        courseName = "Teens A",
        courseLevel = "INTERMEDIATE",
        maxStudents = 10,
        activeEnrollments = 5,
        availableSeats = 5,
        enrollmentOpen = true
    )

    private fun guardianInfo() = GuardianAccountInfo(
        id = 10L,
        username = "guardian",
        email = "guardian@example.com",
        firstName = "Guardian",
        lastName = "User",
        status = "ACTIVE",
        active = true
    )

    private fun studentInfo(studentId: Long) = StudentProfileInfo(
        id = studentId,
        guardianId = 10L,
        firstName = "Jane",
        lastName = "Doe",
        email = "jane@example.com",
        documentNumber = "12345678",
        dateOfBirth = LocalDate.of(2012, 1, 1),
        address = "Main 123",
        phoneNumber = "1111-2222",
        emergencyContact = "Parent",
        currentLevel = "INTERMEDIATE",
        active = true
    )
}
