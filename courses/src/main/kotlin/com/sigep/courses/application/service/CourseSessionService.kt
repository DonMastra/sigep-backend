package com.sigep.courses.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.domain.exception.BusinessException
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.courses.application.dto.*
import com.sigep.courses.domain.model.*
import com.sigep.courses.domain.repository.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
@Transactional
class CourseSessionService(
    private val sessionRepository: CourseSessionRepository,
    private val sessionExceptionRepository: SessionExceptionRepository,
    private val courseRepository: CourseRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val attendanceRepository: AttendanceRepository
) {

    private val logger = LoggerFactory.getLogger(CourseSessionService::class.java)

    fun getSessionById(id: Long): CourseSessionDto {
        logger.info("Fetching session with id: {}", id)
        val session = sessionRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Session not found with id: $id") }
        return session.toDto()
    }

    fun getSessionsByCourse(courseId: Long, page: Int, size: Int): PageResponse<CourseSessionDto> {
        logger.info("Fetching sessions for course: {}", courseId)

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sessionDate", "startTime"))
        val sessionsPage = sessionRepository.findByCourseId(courseId, pageable)

        return PageResponse(
            content = sessionsPage.content.map { it.toDto() },
            page = sessionsPage.number,
            size = sessionsPage.size,
            totalElements = sessionsPage.totalElements,
            totalPages = sessionsPage.totalPages
        )
    }

    fun getSessionsByDateRange(courseId: Long, startDate: LocalDate, endDate: LocalDate): List<CourseSessionDto> {
        logger.info("Fetching sessions for course {} between {} and {}", courseId, startDate, endDate)
        val sessions = sessionRepository.findByCourseIdAndSessionDateBetween(courseId, startDate, endDate)
        return sessions.map { it.toDto() }
    }

    fun createSession(request: CreateSessionRequest): CourseSessionDto {
        logger.info("Creating session for course: {}", request.courseId)

        val course = courseRepository.findById(request.courseId)
            .orElseThrow { ResourceNotFoundException("Course not found with id: ${request.courseId}") }

        // Validate time range
        if (request.endTime.isBefore(request.startTime) || request.endTime == request.startTime) {
            throw BusinessException("End time must be after start time")
        }

        // Check for conflicts
        checkConflicts(
            courseId = request.courseId,
            date = request.sessionDate,
            startTime = request.startTime,
            endTime = request.endTime,
            classroomId = request.classroomId,
            teacherId = course.teacherId
        )

        val session = CourseSession(
            course = course,
            sessionDate = request.sessionDate,
            startTime = request.startTime,
            endTime = request.endTime,
            classroomId = request.classroomId,
            classroomName = request.classroomName,
            status = SessionStatus.SCHEDULED,
            topic = request.topic,
            notes = request.notes,
            isRecurring = request.isRecurring,
            recurrenceRule = request.recurrenceRule,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedSession = sessionRepository.save(session)
        logger.info("Session created successfully with id: {}", savedSession.id)

        return savedSession.toDto()
    }

    fun generateRecurringSessions(request: GenerateRecurringSessionsRequest): List<CourseSessionDto> {
        logger.info("Generating recurring sessions for course: {}", request.courseId)

        val course = courseRepository.findById(request.courseId)
            .orElseThrow { ResourceNotFoundException("Course not found with id: ${request.courseId}") }

        if (request.endDate.isBefore(request.startDate)) {
            throw BusinessException("End date must be after start date")
        }

        val sessions = mutableListOf<CourseSession>()
        var currentDate = request.startDate

        while (!currentDate.isAfter(request.endDate)) {
            if (request.daysOfWeek.contains(currentDate.dayOfWeek)) {
                // Check for conflicts before creating
                val hasConflict = try {
                    checkConflicts(
                        courseId = request.courseId,
                        date = currentDate,
                        startTime = request.startTime,
                        endTime = request.endTime,
                        classroomId = request.classroomId,
                        teacherId = course.teacherId
                    )
                    false
                } catch (e: BusinessException) {
                    logger.warn("Skipping session on {} due to conflict: {}", currentDate, e.message)
                    true
                }

                if (!hasConflict) {
                    val session = CourseSession(
                        course = course,
                        sessionDate = currentDate,
                        startTime = request.startTime,
                        endTime = request.endTime,
                        classroomId = request.classroomId,
                        classroomName = request.classroomName,
                        status = SessionStatus.SCHEDULED,
                        topic = request.topic,
                        isRecurring = true,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                    sessions.add(session)
                }
            }
            currentDate = currentDate.plusDays(1)
        }

        val savedSessions = sessionRepository.saveAll(sessions)
        logger.info("Generated {} recurring sessions", savedSessions.size)

        return savedSessions.map { it.toDto() }
    }

    fun updateSession(id: Long, request: UpdateSessionRequest): CourseSessionDto {
        logger.info("Updating session with id: {}", id)

        val session = sessionRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Session not found with id: $id") }

        val newDate = request.sessionDate ?: session.sessionDate
        val newStartTime = request.startTime ?: session.startTime
        val newEndTime = request.endTime ?: session.endTime
        val newClassroomId = request.classroomId ?: session.classroomId

        // Validate if time changed
        if (newEndTime.isBefore(newStartTime) || newEndTime == newStartTime) {
            throw BusinessException("End time must be after start time")
        }

        // Check conflicts if date/time/classroom changed
        if (newDate != session.sessionDate || newStartTime != session.startTime ||
            newEndTime != session.endTime || newClassroomId != session.classroomId) {

            checkConflicts(
                courseId = session.course.id!!,
                date = newDate,
                startTime = newStartTime,
                endTime = newEndTime,
                classroomId = newClassroomId,
                teacherId = session.course.teacherId,
                excludeSessionId = id
            )
        }

        val updatedSession = session.copy(
            sessionDate = newDate,
            startTime = newStartTime,
            endTime = newEndTime,
            classroomId = newClassroomId,
            classroomName = request.classroomName ?: session.classroomName,
            status = request.status ?: session.status,
            topic = request.topic ?: session.topic,
            notes = request.notes ?: session.notes,
            updatedAt = LocalDateTime.now()
        )

        val savedSession = sessionRepository.save(updatedSession)
        logger.info("Session updated successfully with id: {}", savedSession.id)

        return savedSession.toDto()
    }

    fun deleteSession(id: Long) {
        logger.info("Deleting session with id: {}", id)

        val session = sessionRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Session not found with id: $id") }

        // Check if there are attendance records
        val attendanceRecords = attendanceRepository.findByCourseIdAndDate(session.course.id!!, session.sessionDate)
        if (attendanceRecords.isNotEmpty()) {
            throw BusinessException("Cannot delete session with existing attendance records. Cancel it instead.")
        }

        sessionRepository.deleteById(id)
        logger.info("Session deleted successfully with id: {}", id)
    }

    fun createException(request: CreateSessionExceptionRequest): SessionExceptionDto {
        logger.info("Creating exception for session: {}", request.sessionId)

        val session = sessionRepository.findById(request.sessionId)
            .orElseThrow { ResourceNotFoundException("Session not found with id: ${request.sessionId}") }

        // Check if exception already exists for this date
        val existing = sessionExceptionRepository.findBySessionIdAndExceptionDate(request.sessionId, request.exceptionDate)
        if (existing != null) {
            throw BusinessException("Exception already exists for this session on ${request.exceptionDate}")
        }

        val exception = SessionException(
            session = session,
            exceptionDate = request.exceptionDate,
            exceptionType = request.exceptionType,
            newStartTime = request.newStartTime,
            newEndTime = request.newEndTime,
            newClassroomId = request.newClassroomId,
            reason = request.reason,
            createdAt = LocalDateTime.now()
        )

        val savedException = sessionExceptionRepository.save(exception)
        logger.info("Session exception created successfully")

        return savedException.toDto()
    }

    fun checkConflicts(
        courseId: Long,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        classroomId: Long?,
        teacherId: Long,
        studentId: Long? = null,
        excludeSessionId: Long? = null
    ) {
        // Check teacher conflicts
        val teacherConflicts = sessionRepository.findTeacherConflicts(teacherId, date, startTime, endTime)
            .filter { excludeSessionId == null || it.id != excludeSessionId }

        if (teacherConflicts.isNotEmpty()) {
            throw BusinessException("Teacher has conflicting session at ${teacherConflicts.first().startTime}")
        }

        // Check classroom conflicts
        if (classroomId != null) {
            val classroomConflicts = sessionRepository.findClassroomConflicts(classroomId, date, startTime, endTime)
                .filter { excludeSessionId == null || it.id != excludeSessionId }

            if (classroomConflicts.isNotEmpty()) {
                throw BusinessException("Classroom is occupied at ${classroomConflicts.first().startTime}")
            }
        }

        // Check student conflicts if provided
        if (studentId != null) {
            val studentConflicts = sessionRepository.findStudentConflicts(studentId, date, startTime, endTime)
                .filter { excludeSessionId == null || it.id != excludeSessionId }

            if (studentConflicts.isNotEmpty()) {
                throw BusinessException("Student has conflicting session at ${studentConflicts.first().startTime}")
            }
        }
    }

    fun checkConflictsForRequest(request: ConflictCheckRequest): ConflictDto {
        logger.info("Checking conflicts for request")

        val conflicts = mutableListOf<CourseSession>()
        var conflictType = ""

        // Check teacher conflicts
        if (request.teacherId != null) {
            val teacherConflicts = sessionRepository.findTeacherConflicts(
                request.teacherId, request.date, request.startTime, request.endTime
            )
            if (teacherConflicts.isNotEmpty()) {
                conflicts.addAll(teacherConflicts)
                conflictType = "TEACHER"
            }
        }

        // Check classroom conflicts
        if (request.classroomId != null) {
            val classroomConflicts = sessionRepository.findClassroomConflicts(
                request.classroomId, request.date, request.startTime, request.endTime
            )
            if (classroomConflicts.isNotEmpty()) {
                conflicts.addAll(classroomConflicts)
                conflictType = if (conflictType.isEmpty()) "CLASSROOM" else "$conflictType, CLASSROOM"
            }
        }

        // Check student conflicts
        if (request.studentId != null) {
            val studentConflicts = sessionRepository.findStudentConflicts(
                request.studentId, request.date, request.startTime, request.endTime
            )
            if (studentConflicts.isNotEmpty()) {
                conflicts.addAll(studentConflicts)
                conflictType = if (conflictType.isEmpty()) "STUDENT" else "$conflictType, STUDENT"
            }
        }

        val hasConflict = conflicts.isNotEmpty()
        val message = if (hasConflict) {
            "Found ${conflicts.size} conflict(s) for $conflictType"
        } else {
            "No conflicts found"
        }

        return ConflictDto(
            hasConflict = hasConflict,
            conflictType = conflictType,
            conflicts = conflicts.distinct().map { it.toDto() },
            message = message
        )
    }

    fun getSessionAttendanceSummary(sessionId: Long): SessionAttendanceSummaryDto {
        logger.info("Getting attendance summary for session: {}", sessionId)

        val session = sessionRepository.findById(sessionId)
            .orElseThrow { ResourceNotFoundException("Session not found with id: $sessionId") }

        val attendances = attendanceRepository.findByCourseIdAndDate(session.course.id!!, session.sessionDate)
        val totalEnrolled = enrollmentRepository.countActiveEnrollmentsByCourse(session.course.id!!).toInt()

        val present = attendances.count { it.status == AttendanceStatus.PRESENT }
        val absent = attendances.count { it.status == AttendanceStatus.ABSENT }
        val late = attendances.count { it.status == AttendanceStatus.LATE }

        val attendanceRate = if (totalEnrolled > 0) {
            ((present + late).toDouble() / totalEnrolled.toDouble()) * 100
        } else 0.0

        return SessionAttendanceSummaryDto(
            sessionId = sessionId,
            sessionDate = session.sessionDate,
            courseName = session.course.name,
            totalEnrolled = totalEnrolled,
            present = present,
            absent = absent,
            late = late,
            attendanceRate = attendanceRate
        )
    }

    fun getCalendar(courseId: Long?, startDate: LocalDate, endDate: LocalDate): List<SessionCalendarDto> {
        logger.info("Getting calendar from {} to {}", startDate, endDate)

        val sessions = if (courseId != null) {
            sessionRepository.findByCourseIdAndSessionDateBetween(courseId, startDate, endDate)
        } else {
            sessionRepository.findAll().filter {
                !it.sessionDate.isBefore(startDate) && !it.sessionDate.isAfter(endDate)
            }
        }

        val groupedByDate = sessions.groupBy { it.sessionDate }

        return groupedByDate.map { (date, dateSessions) ->
            SessionCalendarDto(
                date = date,
                sessions = dateSessions.map { it.toDto() },
                totalSessions = dateSessions.size,
                completedSessions = dateSessions.count { it.status == SessionStatus.COMPLETED },
                cancelledSessions = dateSessions.count { it.status == SessionStatus.CANCELLED }
            )
        }.sortedBy { it.date }
    }

    private fun CourseSession.toDto(): CourseSessionDto {
        val attendanceCount = attendanceRepository.countPresentByCourseIdAndDate(course.id!!, sessionDate).toInt()
        val expectedAttendance = enrollmentRepository.countActiveEnrollmentsByCourse(course.id!!).toInt()

        return CourseSessionDto(
            id = id!!,
            courseId = course.id,
            courseName = course.name,
            sessionDate = sessionDate,
            startTime = startTime,
            endTime = endTime,
            classroomId = classroomId,
            classroomName = classroomName,
            status = status,
            topic = topic,
            notes = notes,
            isRecurring = isRecurring,
            recurrenceRule = recurrenceRule,
            attendanceCount = attendanceCount,
            expectedAttendance = expectedAttendance,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun SessionException.toDto() = SessionExceptionDto(
        id = id!!,
        sessionId = session?.id,
        exceptionDate = exceptionDate,
        exceptionType = exceptionType,
        newStartTime = newStartTime,
        newEndTime = newEndTime,
        newClassroomId = newClassroomId,
        reason = reason,
        createdAt = createdAt
    )
}

