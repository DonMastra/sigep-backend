package com.sigep.courses.application.service

import com.sigep.common.application.service.TeacherInfoProvider
import com.sigep.courses.application.dto.ConflictCheckRequest
import com.sigep.courses.domain.model.CourseSession
import com.sigep.courses.domain.repository.AttendanceRepository
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.CourseSessionRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import com.sigep.courses.domain.repository.SessionExceptionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class CourseSessionServiceConflictTest {
    private val sessionRepository = mockk<CourseSessionRepository>()
    private val sessionExceptionRepository = mockk<SessionExceptionRepository>()
    private val courseRepository = mockk<CourseRepository>()
    private val enrollmentRepository = mockk<EnrollmentRepository>()
    private val attendanceRepository = mockk<AttendanceRepository>()
    private val teacherInfoProvider = mockk<TeacherInfoProvider>()
    private val service = CourseSessionService(
        sessionRepository,
        sessionExceptionRepository,
        courseRepository,
        enrollmentRepository,
        attendanceRepository,
        teacherInfoProvider
    )

    @Test
    fun `excludes the edited session from teacher and classroom conflicts`() {
        val date = LocalDate.of(2026, 9, 7)
        val start = LocalTime.of(10, 0)
        val end = LocalTime.of(11, 30)
        val currentSession = mockk<CourseSession>()
        every { currentSession.id } returns 7
        every { sessionRepository.findTeacherConflicts(4, date, start, end) } returns listOf(currentSession)
        every { sessionRepository.findClassroomConflicts(2, date, start, end) } returns listOf(currentSession)

        val result = service.checkConflictsForRequest(
            ConflictCheckRequest(
                teacherId = 4,
                classroomId = 2,
                date = date,
                startTime = start,
                endTime = end,
                excludeSessionId = 7
            ),
            actorUserId = 1,
            actorRole = "ADMIN"
        )

        assertFalse(result.hasConflict)
        assertTrue(result.conflicts.isEmpty())
    }
}
