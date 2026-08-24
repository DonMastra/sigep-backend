package com.sigep.courses.application.service

import com.sigep.common.application.service.StudentProfileInfo
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.common.application.service.ReservationInfoProvider
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
    fun `course statistics aggregate persisted attendance by enrollment`() {
        val course = Course(
            id = 21,
            code = "2026-ADULTS-ELEMENTARY",
            name = "Adults - Elementary",
            description = "Course",
            level = CourseLevel.ELEMENTARY,
            duration = 60,
            maxStudents = 20,
            price = BigDecimal.ZERO
        )
        val enrollment = Enrollment(id = 14, studentId = 15, course = course)
        val records = listOf(
            Attendance(id = 1, enrollment = enrollment, attendanceDate = LocalDate.of(2026, 8, 20), status = AttendanceStatus.PRESENT, recordedBy = 1),
            Attendance(id = 2, enrollment = enrollment, attendanceDate = LocalDate.of(2026, 8, 22), status = AttendanceStatus.LATE, recordedBy = 1)
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
        every { enrollmentRepository.findAllByCourseIdOrderByStudentIdAsc(21) } returns listOf(enrollment)
        every { attendanceRepository.findAllByCourseId(21) } returns records
        every { studentProfileProvider.getStudentProfiles(listOf(15)) } returns mapOf(15L to profile)

        val result = service.getCourseAttendanceStatistics(21)

        assertEquals(2, result.totalRecords)
        assertEquals(1, result.present)
        assertEquals(1, result.late)
        assertEquals(100.0, result.attendanceRate)
        assertEquals(2, result.students.single().totalClasses)
        assertEquals("Ana Alumna", result.students.single().studentName)
    }
}
