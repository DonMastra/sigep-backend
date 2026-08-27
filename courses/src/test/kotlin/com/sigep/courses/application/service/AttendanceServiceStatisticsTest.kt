package com.sigep.courses.application.service

import com.sigep.common.application.service.StudentProfileInfo
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.common.application.service.ReservationInfoProvider
import com.sigep.common.application.service.ReservationInfo
import com.sigep.courses.domain.model.Attendance
import com.sigep.courses.domain.model.AttendanceStatus
import com.sigep.courses.domain.model.Course
import com.sigep.courses.domain.model.CourseLevel
import com.sigep.courses.domain.model.Enrollment
import com.sigep.courses.domain.repository.AttendanceRepository
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.CourseSessionRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

class AttendanceServiceStatisticsTest {
    private val attendanceRepository = mockk<AttendanceRepository>()
    private val enrollmentRepository = mockk<EnrollmentRepository>()
    private val courseSessionRepository = mockk<CourseSessionRepository>()
    private val studentProfileProvider = mockk<StudentProfileProvider>()
    private val courseRepository = mockk<CourseRepository>()
    private val reservationInfoProvider = mockk<ReservationInfoProvider>()
    private val service = AttendanceService(
        attendanceRepository,
        enrollmentRepository,
        courseSessionRepository,
        studentProfileProvider,
        courseRepository,
        reservationInfoProvider
    )

    @Test
    fun `course statistics count every assigned weekly schedule and keep unregistered classes separate`() {
        val course = Course(
            id = 21,
            code = "2026-ADULTS-STARTER",
            name = "Adults - Starter",
            description = "Course",
            level = CourseLevel.BEGINNER,
            duration = 60,
            maxStudents = 20,
            price = BigDecimal.ZERO,
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 12, 18)
        )
        val enrollment = Enrollment(
            id = 14,
            studentId = 15,
            course = course,
            enrollmentDate = LocalDate.of(2026, 4, 1)
        )
        val records = listOf(
            Attendance(
                id = 1,
                enrollment = enrollment,
                attendanceDate = LocalDate.of(2026, 4, 1),
                status = AttendanceStatus.PRESENT,
                recordedBy = 1
            )
        )
        val profile = StudentProfileInfo(
            id = 15,
            guardianId = null,
            firstName = "Ana",
            lastName = "Alumna",
            email = "ana@example.com",
            documentType = "DNI",
            documentCountry = "AR",
            documentNumber = "12345678",
            dateOfBirth = LocalDate.of(2000, 1, 1),
            address = "Address",
            phoneNumber = "123",
            emergencyContact = "Contact",
            currentLevel = "ELEMENTARY",
            active = true
        )
        every { courseRepository.findById(21) } returns Optional.of(course)
        every { enrollmentRepository.findAllByCourseIdOrderByStudentIdAsc(21) } returns listOf(enrollment)
        every { attendanceRepository.findAllByCourseId(21) } returns records
        every { studentProfileProvider.getStudentProfiles(listOf(15)) } returns mapOf(15L to profile)
        every { reservationInfoProvider.getReservationsByCourse(21) } returns listOf(
            schedule(1, "MONDAY"),
            schedule(2, "WEDNESDAY")
        )

        val result = service.getCourseAttendanceStatistics(21, LocalDate.of(2026, 8, 26))

        assertEquals(75, result.scheduledClassesTotal)
        assertEquals(43, result.scheduledClassesToDate)
        assertEquals(43, result.expectedAttendanceRecordsToDate)
        assertEquals(1, result.totalRecords)
        assertEquals(42, result.unregisteredRecords)
        assertEquals(1, result.present)
        assertEquals(100.0, result.attendanceRate)
        assertEquals((1.0 / 43.0) * 100.0, result.confirmedPresenceRate, 0.0001)
        assertEquals((1.0 / 43.0) * 100.0, result.dataCoverageRate, 0.0001)
        assertEquals(43, result.students.single().scheduledClassesToDate)
        assertEquals(1, result.students.single().registeredClasses)
        assertEquals(42, result.students.single().unregisteredClasses)
        assertEquals(100.0, result.students.single().attendanceRate)
        assertEquals((1.0 / 43.0) * 100.0, result.students.single().confirmedPresenceRate, 0.0001)
        assertEquals("Ana Alumna", result.students.single().studentName)
    }

    @Test
    fun `course statistics count Monday Wednesday and Friday as three weekly schedules`() {
        val course = Course(
            id = 23,
            code = "2026-THREE-DAYS",
            name = "Three Days",
            description = "Course",
            level = CourseLevel.BEGINNER,
            duration = 60,
            maxStudents = 20,
            price = BigDecimal.ZERO,
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 4, 10)
        )
        val enrollment = Enrollment(
            id = 18,
            studentId = 19,
            course = course,
            enrollmentDate = LocalDate.of(2026, 4, 1)
        )

        every { courseRepository.findById(23) } returns Optional.of(course)
        every { enrollmentRepository.findAllByCourseIdOrderByStudentIdAsc(23) } returns listOf(enrollment)
        every { attendanceRepository.findAllByCourseId(23) } returns emptyList()
        every { studentProfileProvider.getStudentProfiles(listOf(19)) } returns emptyMap()
        every { reservationInfoProvider.getReservationsByCourse(23) } returns listOf(
            schedule(6, "MONDAY"),
            schedule(7, "WEDNESDAY"),
            schedule(8, "FRIDAY")
        )

        val result = service.getCourseAttendanceStatistics(23, LocalDate.of(2026, 4, 10))

        assertEquals(5, result.scheduledClassesToDate)
        assertEquals(5, result.expectedAttendanceRecordsToDate)
        assertEquals(5, result.students.single().unregisteredClasses)
    }

    @Test
    fun `course statistics count distinct slots on the same weekday as separate classes`() {
        val course = Course(
            id = 22,
            code = "2026-DOUBLE-MONDAY",
            name = "Double Monday",
            description = "Course",
            level = CourseLevel.BEGINNER,
            duration = 60,
            maxStudents = 20,
            price = BigDecimal.ZERO,
            startDate = LocalDate.of(2026, 4, 6),
            endDate = LocalDate.of(2026, 4, 6)
        )
        val enrollment = Enrollment(
            id = 16,
            studentId = 17,
            course = course,
            enrollmentDate = LocalDate.of(2026, 4, 6)
        )

        every { courseRepository.findById(22) } returns Optional.of(course)
        every { enrollmentRepository.findAllByCourseIdOrderByStudentIdAsc(22) } returns listOf(enrollment)
        every { attendanceRepository.findAllByCourseId(22) } returns emptyList()
        every { studentProfileProvider.getStudentProfiles(listOf(17)) } returns emptyMap()
        every { reservationInfoProvider.getReservationsByCourse(22) } returns listOf(
            schedule(4, "MONDAY", slotId = 40),
            schedule(5, "MONDAY", slotId = 41)
        )

        val result = service.getCourseAttendanceStatistics(22, LocalDate.of(2026, 4, 6))

        assertEquals(2, result.scheduledClassesToDate)
        assertEquals(2, result.expectedAttendanceRecordsToDate)
        assertEquals(2, result.unregisteredRecords)
        assertEquals(2, result.students.single().unregisteredClasses)
    }

    private fun schedule(
        reservationId: Long,
        dayOfWeek: String,
        slotId: Long = reservationId
    ) = ReservationInfo(
        reservationId = reservationId,
        status = "ASSIGNED",
        slotId = slotId,
        dayOfWeek = dayOfWeek,
        startTime = "18:00",
        endTime = "19:00",
        classroomId = 1,
        classroomName = "Aula 1",
        building = null,
        floor = null,
        classroomCapacity = 20
    )
}
