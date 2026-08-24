package com.sigep.courses.application.service

import com.sigep.common.application.service.ReservationInfo
import com.sigep.common.application.service.ReservationInfoProvider
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.common.domain.exception.BusinessException
import com.sigep.courses.application.dto.BulkAttendanceRequest
import com.sigep.courses.application.dto.StudentAttendanceRecord
import com.sigep.courses.domain.model.Attendance
import com.sigep.courses.domain.model.AttendanceStatus
import com.sigep.courses.domain.model.Course
import com.sigep.courses.domain.model.CourseLevel
import com.sigep.courses.domain.model.CourseSession
import com.sigep.courses.domain.model.Enrollment
import com.sigep.courses.domain.model.SessionStatus
import com.sigep.courses.domain.repository.AttendanceRepository
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.CourseSessionRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional

class AttendanceServiceDateResolutionTest {
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

    private val course = Course(
        id = 21,
        code = "2026-ADULTS-ELEMENTARY",
        name = "Adults - Elementary",
        description = "Course",
        level = CourseLevel.ELEMENTARY,
        duration = 60,
        maxStudents = 20,
        price = BigDecimal.ZERO,
        startDate = LocalDate.of(2026, 8, 1),
        endDate = LocalDate.of(2026, 12, 20)
    )
    private val enrollment = Enrollment(id = 14, studentId = 15, course = course)
    private val date = LocalDate.of(2026, 8, 24)

    @Test
    fun `bulk attendance by date creates the missing internal session from the assigned schedule`() {
        val sessionSlot = slot<CourseSession>()
        val attendanceSlot = slot<Attendance>()
        every { courseSessionRepository.findByCourseIdAndSessionDate(21, date) } returns emptyList()
        every { courseRepository.findById(21) } returns Optional.of(course)
        every { reservationInfoProvider.getReservationByCourse(21) } returns ReservationInfo(
            reservationId = 9,
            status = "ASSIGNED",
            slotId = 7,
            dayOfWeek = "MONDAY",
            startTime = "18:00",
            endTime = "20:00",
            classroomId = 3,
            classroomName = "Aula 3",
            building = null,
            floor = null,
            classroomCapacity = 20
        )
        every { courseSessionRepository.save(capture(sessionSlot)) } answers { sessionSlot.captured.copy(id = 42) }
        every { enrollmentRepository.findById(14) } returns Optional.of(enrollment)
        every { attendanceRepository.findByEnrollmentIdAndCourseSessionId(14, 42) } returns Optional.empty()
        every { attendanceRepository.save(capture(attendanceSlot)) } answers { attendanceSlot.captured.copy(id = 100) }
        every { studentProfileProvider.getStudentProfile(15) } returns null

        val result = service.recordBulkAttendance(request(), recordedBy = 1)

        assertEquals(42, result.single().courseSessionId)
        assertEquals(date, result.single().attendanceDate)
        assertEquals(LocalTime.of(18, 0), sessionSlot.captured.startTime)
        assertEquals(LocalTime.of(20, 0), sessionSlot.captured.endTime)
        assertEquals(SessionStatus.COMPLETED, sessionSlot.captured.status)
        verify(exactly = 1) { courseSessionRepository.save(any()) }
    }

    @Test
    fun `bulk attendance by date reuses the only existing session`() {
        val session = CourseSession(
            id = 42,
            course = course,
            sessionDate = date,
            startTime = LocalTime.of(18, 0),
            endTime = LocalTime.of(20, 0)
        )
        every { courseSessionRepository.findByCourseIdAndSessionDate(21, date) } returns listOf(session)
        every { enrollmentRepository.findById(14) } returns Optional.of(enrollment)
        every { attendanceRepository.findByEnrollmentIdAndCourseSessionId(14, 42) } returns Optional.empty()
        every { attendanceRepository.save(any()) } answers { firstArg<Attendance>().copy(id = 100) }
        every { studentProfileProvider.getStudentProfile(15) } returns null

        val result = service.recordBulkAttendance(request(), recordedBy = 1)

        assertEquals(42, result.single().courseSessionId)
        verify(exactly = 0) { courseSessionRepository.save(any()) }
        verify(exactly = 0) { reservationInfoProvider.getReservationByCourse(any()) }
    }

    @Test
    fun `bulk attendance by date requires a choice when the course has multiple sessions that day`() {
        val sessions = listOf(
            CourseSession(id = 41, course = course, sessionDate = date, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0)),
            CourseSession(id = 42, course = course, sessionDate = date, startTime = LocalTime.of(18, 0), endTime = LocalTime.of(20, 0))
        )
        every { courseSessionRepository.findByCourseIdAndSessionDate(21, date) } returns sessions

        val error = assertThrows(BusinessException::class.java) {
            service.recordBulkAttendance(request(), recordedBy = 1)
        }

        assertEquals(
            "More than one course session exists on the selected date; choose the corresponding session",
            error.message
        )
    }

    private fun request() = BulkAttendanceRequest(
        courseId = 21,
        date = date,
        records = listOf(StudentAttendanceRecord(14, AttendanceStatus.PRESENT))
    )
}
