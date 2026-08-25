package com.sigep.courses.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.service.StudentProfileProvider
import com.sigep.common.application.service.ReservationInfoProvider
import com.sigep.common.domain.exception.BusinessException
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.courses.application.dto.*
import com.sigep.courses.domain.model.Attendance
import com.sigep.courses.domain.model.AttendanceStatus
import com.sigep.courses.domain.model.CourseSession
import com.sigep.courses.domain.model.SessionStatus
import com.sigep.courses.domain.repository.AttendanceRepository
import com.sigep.courses.domain.repository.CourseRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import com.sigep.courses.domain.repository.CourseSessionRepository
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
class AttendanceService(
    private val attendanceRepository: AttendanceRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val courseSessionRepository: CourseSessionRepository,
    private val studentProfileProvider: StudentProfileProvider,
    private val courseRepository: CourseRepository,
    private val reservationInfoProvider: ReservationInfoProvider
) {

    private val logger = LoggerFactory.getLogger(AttendanceService::class.java)

    fun getAttendanceById(id: Long): AttendanceDto {
        logger.info("Fetching attendance with id: {}", id)
        val attendance = attendanceRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Attendance record not found with id: $id") }
        return attendance.toDto()
    }

    fun getAttendanceByEnrollment(enrollmentId: Long, page: Int, size: Int): PageResponse<AttendanceDto> {
        logger.info("Fetching attendance for enrollment: {}", enrollmentId)

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "attendanceDate"))
        val attendancePage = attendanceRepository.findByEnrollmentId(enrollmentId, pageable)

        return PageResponse(
            content = attendancePage.content.map { it.toDto() },
            page = attendancePage.number,
            size = attendancePage.size,
            totalElements = attendancePage.totalElements,
            totalPages = attendancePage.totalPages
        )
    }

    fun getAttendanceByCourseAndDate(courseId: Long, date: LocalDate): List<AttendanceDto> {
        logger.info("Fetching attendance for course {} on date {}", courseId, date)
        val attendances = attendanceRepository.findByCourseIdAndDate(courseId, date)
        return attendances.map { it.toDto() }
    }

    fun getAttendanceByCourse(courseId: Long, page: Int, size: Int): PageResponse<AttendanceDto> {
        logger.info("Fetching attendance for course: {}", courseId)

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "attendanceDate"))
        val attendancePage = attendanceRepository.findByCourseId(courseId, pageable)

        return PageResponse(
            content = attendancePage.content.map { it.toDto() },
            page = attendancePage.number,
            size = attendancePage.size,
            totalElements = attendancePage.totalElements,
            totalPages = attendancePage.totalPages
        )
    }

    fun getAttendanceByStudent(
        studentId: Long,
        page: Int,
        size: Int,
        actorUserId: Long,
        actorRole: String?
    ): PageResponse<AttendanceDto> {
        logger.info("Fetching attendance for student: {}", studentId)

        when (actorRole) {
            "ADMIN" -> Unit
            "TEACHER" -> if (!enrollmentRepository.existsActiveByStudentIdAndTeacherId(studentId, actorUserId)) {
                throw ForbiddenException("Teachers can only read attendance for students in their active courses")
            }
            "GUARDIAN" -> if (!studentProfileProvider.validateGuardianOwnsStudent(actorUserId, studentId)) {
                throw ForbiddenException("Guardians can only read attendance for their own students")
            }
            else -> throw ForbiddenException("Attendance access is not allowed for this role")
        }

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "attendanceDate"))
        val attendancePage = attendanceRepository.findByStudentId(studentId, pageable)

        return PageResponse(
            content = attendancePage.content.map { it.toDto() },
            page = attendancePage.number,
            size = attendancePage.size,
            totalElements = attendancePage.totalElements,
            totalPages = attendancePage.totalPages
        )
    }

    fun recordAttendance(request: CreateAttendanceRequest, recordedBy: Long): AttendanceDto {
        logger.info("Recording attendance for enrollment: {}", request.enrollmentId)

        val enrollment = enrollmentRepository.findById(request.enrollmentId)
            .orElseThrow { ResourceNotFoundException("Enrollment not found with id: ${request.enrollmentId}") }

        val sessionId = request.courseSessionId
            ?: throw BusinessException("Course session ID is required")
        val session = courseSessionRepository.findById(sessionId)
            .orElseThrow { ResourceNotFoundException("Course session not found with id: $sessionId") }
        if (enrollment.course.id != session.course.id || request.attendanceDate != session.sessionDate) {
            throw BusinessException("Attendance enrollment and date must match the selected course session")
        }

        // Verificar si ya existe un registro de asistencia para este día
        val existingAttendance = attendanceRepository.findByEnrollmentIdAndCourseSessionId(request.enrollmentId, sessionId)

        if (existingAttendance.isPresent) {
            throw BusinessException("Attendance already recorded for this date. Use update instead.")
        }

        val attendance = Attendance(
            enrollment = enrollment,
            courseSession = session,
            attendanceDate = request.attendanceDate,
            status = request.status,
            notes = request.notes,
            recordedBy = recordedBy,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedAttendance = attendanceRepository.save(attendance)
        logger.info("Attendance recorded successfully with id: {}", savedAttendance.id)

        return savedAttendance.toDto()
    }

    fun recordBulkAttendance(request: BulkAttendanceRequest, recordedBy: Long): List<AttendanceDto> {
        val session = resolveBulkAttendanceSession(request)
        val sessionId = requireNotNull(session.id)
        val resolvedCourseId = session.course.id!!
        logger.info("Recording bulk attendance for course {} on {}", resolvedCourseId, request.date)

        val savedAttendances = request.records.map { record ->
            val enrollment = enrollmentRepository.findById(record.enrollmentId)
                .orElseThrow { ResourceNotFoundException("Enrollment not found with id: ${record.enrollmentId}") }

            // Verificar que el enrollment pertenezca al curso
            if (enrollment.course.id != resolvedCourseId) {
                throw BusinessException("Enrollment ${record.enrollmentId} does not belong to course $resolvedCourseId")
            }

            // Verificar si ya existe, si existe actualizar, si no crear
            val existingAttendance = attendanceRepository.findByEnrollmentIdAndCourseSessionId(record.enrollmentId, sessionId)

            if (existingAttendance.isPresent) {
                val updated = existingAttendance.get().copy(
                    status = record.status,
                    notes = record.notes,
                    recordedBy = recordedBy,
                    updatedAt = LocalDateTime.now()
                )
                attendanceRepository.save(updated)
            } else {
                val newAttendance = Attendance(
                    enrollment = enrollment,
                    courseSession = session,
                    attendanceDate = request.date,
                    status = record.status,
                    notes = record.notes,
                    recordedBy = recordedBy,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
                attendanceRepository.save(newAttendance)
            }
        }

        logger.info("Bulk attendance recorded successfully for {} students", savedAttendances.size)
        return savedAttendances.map { it.toDto() }
    }

    private fun resolveBulkAttendanceSession(request: BulkAttendanceRequest): CourseSession {
        request.courseSessionId?.let { sessionId ->
            val session = courseSessionRepository.findById(sessionId)
                .orElseThrow { ResourceNotFoundException("Course session not found with id: $sessionId") }
            if (request.courseId != null && request.courseId != session.course.id) {
                throw BusinessException("Attendance course must match the selected course session")
            }
            if (request.date != session.sessionDate) {
                throw BusinessException("Attendance date must match the selected course session")
            }
            if (session.status == SessionStatus.CANCELLED) {
                throw BusinessException("Attendance cannot be recorded for a cancelled course session")
            }
            return session
        }

        val courseId = request.courseId
            ?: throw BusinessException("Course ID is required when course session ID is omitted")
        val sessionsOnDate = courseSessionRepository.findByCourseIdAndSessionDate(courseId, request.date)
        val availableSessions = sessionsOnDate.filter { it.status != SessionStatus.CANCELLED }
        if (availableSessions.size == 1) return availableSessions.single()
        if (availableSessions.size > 1) {
            throw BusinessException("More than one course session exists on the selected date; choose the corresponding session")
        }
        if (sessionsOnDate.isNotEmpty()) {
            throw BusinessException("Attendance cannot be recorded because the course session on the selected date is cancelled")
        }

        val course = courseRepository.findById(courseId)
            .orElseThrow { ResourceNotFoundException("Course not found with id: $courseId") }
        if (course.startDate != null && request.date.isBefore(course.startDate)) {
            throw BusinessException("Attendance date cannot be before the course start date")
        }
        if (course.endDate != null && request.date.isAfter(course.endDate)) {
            throw BusinessException("Attendance date cannot be after the course end date")
        }

        val schedules = reservationInfoProvider.getReservationsByCourse(courseId)
        if (schedules.isEmpty()) {
            throw BusinessException("The course has no assigned schedule to create the session for the selected date")
        }
        val schedulesForDate = schedules.filter { it.dayOfWeek == request.date.dayOfWeek.name }
        if (schedulesForDate.isEmpty()) {
            throw BusinessException("The selected date does not match any assigned course schedule")
        }
        if (schedulesForDate.size > 1) {
            throw BusinessException("More than one assigned course schedule matches the selected date; create or choose the corresponding session")
        }
        val schedule = schedulesForDate.single()

        val startTime = parseScheduleTime(schedule.startTime, "start")
        val endTime = parseScheduleTime(schedule.endTime, "end")
        return courseSessionRepository.save(
            CourseSession(
                course = course,
                sessionDate = request.date,
                startTime = startTime,
                endTime = endTime,
                classroomId = schedule.classroomId,
                classroomName = schedule.classroomName,
                status = SessionStatus.COMPLETED,
                topic = "Registro de asistencia",
                notes = "Sesión creada automáticamente al registrar asistencia por fecha",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )
    }

    private fun parseScheduleTime(value: String, label: String): LocalTime =
        try {
            LocalTime.parse(value.take(5))
        } catch (_: RuntimeException) {
            throw BusinessException("The assigned course schedule has an invalid $label time")
        }

    fun updateAttendance(id: Long, request: UpdateAttendanceRequest, recordedBy: Long): AttendanceDto {
        logger.info("Updating attendance with id: {}", id)

        val attendance = attendanceRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Attendance record not found with id: $id") }

        val updatedAttendance = attendance.copy(
            status = request.status ?: attendance.status,
            notes = request.notes ?: attendance.notes,
            recordedBy = recordedBy,
            updatedAt = LocalDateTime.now()
        )

        val savedAttendance = attendanceRepository.save(updatedAttendance)
        logger.info("Attendance updated successfully with id: {}", savedAttendance.id)

        return savedAttendance.toDto()
    }

    fun deleteAttendance(id: Long) {
        logger.info("Deleting attendance with id: {}", id)

        if (!attendanceRepository.existsById(id)) {
            throw ResourceNotFoundException("Attendance record not found with id: $id")
        }

        attendanceRepository.deleteById(id)
        logger.info("Attendance deleted successfully with id: {}", id)
    }

    fun getAttendanceStatistics(enrollmentId: Long): AttendanceStatisticsDto {
        logger.info("Calculating attendance statistics for enrollment: {}", enrollmentId)

        val enrollment = enrollmentRepository.findById(enrollmentId)
            .orElseThrow { ResourceNotFoundException("Enrollment not found with id: $enrollmentId") }

        val totalClasses = attendanceRepository.findByEnrollmentId(enrollmentId, PageRequest.of(0, Int.MAX_VALUE))
            .totalElements
        val present = attendanceRepository.countByEnrollmentIdAndStatus(enrollmentId, AttendanceStatus.PRESENT)
        val absent = attendanceRepository.countByEnrollmentIdAndStatus(enrollmentId, AttendanceStatus.ABSENT)
        val late = attendanceRepository.countByEnrollmentIdAndStatus(enrollmentId, AttendanceStatus.LATE)
        val excusedAbsence = attendanceRepository.countByEnrollmentIdAndStatus(enrollmentId, AttendanceStatus.EXCUSED_ABSENCE)
        val sickLeave = attendanceRepository.countByEnrollmentIdAndStatus(enrollmentId, AttendanceStatus.SICK_LEAVE)

        val attendanceRate = if (totalClasses > 0) {
            ((present + late).toDouble() / totalClasses.toDouble()) * 100
        } else 0.0

        return AttendanceStatisticsDto(
            enrollmentId = enrollmentId,
            studentId = enrollment.studentId,
            studentName = studentProfileProvider.getStudentProfile(enrollment.studentId)?.let { "${it.firstName} ${it.lastName}".trim() },
            totalClasses = totalClasses,
            present = present,
            absent = absent,
            late = late,
            excusedAbsence = excusedAbsence,
            sickLeave = sickLeave,
            attendanceRate = attendanceRate
        )
    }

    @Transactional(readOnly = true)
    fun getCourseAttendanceStatistics(courseId: Long): CourseAttendanceStatisticsDto {
        logger.info("Calculating cumulative attendance statistics for course: {}", courseId)

        val enrollments = enrollmentRepository.findAllByCourseIdOrderByStudentIdAsc(courseId)
        val attendances = attendanceRepository.findAllByCourseId(courseId)
        val attendancesByEnrollment = attendances.groupBy { it.enrollment.id }
        val profiles = studentProfileProvider.getStudentProfiles(enrollments.map { it.studentId })

        val studentStatistics = enrollments.map { enrollment ->
            val records = attendancesByEnrollment[enrollment.id].orEmpty()
            buildAttendanceStatistics(
                enrollmentId = requireNotNull(enrollment.id),
                studentId = enrollment.studentId,
                studentName = profiles[enrollment.studentId]?.let { "${it.firstName} ${it.lastName}".trim() },
                records = records
            )
        }

        val present = attendances.count { it.status == AttendanceStatus.PRESENT }.toLong()
        val absent = attendances.count { it.status == AttendanceStatus.ABSENT }.toLong()
        val late = attendances.count { it.status == AttendanceStatus.LATE }.toLong()
        val excusedAbsence = attendances.count { it.status == AttendanceStatus.EXCUSED_ABSENCE }.toLong()
        val sickLeave = attendances.count { it.status == AttendanceStatus.SICK_LEAVE }.toLong()
        val totalRecords = attendances.size.toLong()

        return CourseAttendanceStatisticsDto(
            courseId = courseId,
            totalRecords = totalRecords,
            present = present,
            absent = absent,
            late = late,
            excusedAbsence = excusedAbsence,
            sickLeave = sickLeave,
            attendanceRate = attendanceRate(present, late, totalRecords),
            students = studentStatistics
        )
    }

    fun getCourseAttendanceReport(courseId: Long, date: LocalDate): CourseAttendanceReportDto {
        logger.info("Generating attendance report for course {} on {}", courseId, date)

        val attendances = attendanceRepository.findByCourseIdAndDate(courseId, date)
        val totalEnrolled = enrollmentRepository.findByCourseId(courseId, PageRequest.of(0, Int.MAX_VALUE)).totalElements.toInt()
        val totalPresent = attendanceRepository.countPresentByCourseIdAndDate(courseId, date).toInt()
        val totalAbsent = attendances.count { it.status == AttendanceStatus.ABSENT }

        val attendanceRate = if (totalEnrolled > 0) {
            (totalPresent.toDouble() / totalEnrolled.toDouble()) * 100
        } else 0.0

        // Get course name from first attendance or fetch course
        val courseName = if (attendances.isNotEmpty()) {
            attendances.first().enrollment.course.name
        } else {
            "Unknown Course"
        }

        return CourseAttendanceReportDto(
            courseId = courseId,
            courseName = courseName,
            date = date,
            totalEnrolled = totalEnrolled,
            totalPresent = totalPresent,
            totalAbsent = totalAbsent,
            attendanceRate = attendanceRate,
            attendances = attendances.map { it.toDto() }
        )
    }

    fun getAttendanceByDateRange(enrollmentId: Long, startDate: LocalDate, endDate: LocalDate): List<AttendanceDto> {
        logger.info("Fetching attendance for enrollment {} between {} and {}", enrollmentId, startDate, endDate)

        val attendances = attendanceRepository.findByEnrollmentIdAndDateRange(enrollmentId, startDate, endDate)
        return attendances.map { it.toDto() }
    }

    private fun Attendance.toDto() = AttendanceDto(
        id = id!!,
        enrollmentId = enrollment.id!!,
        courseSessionId = courseSession?.id,
        studentId = enrollment.studentId,
        studentName = studentProfileProvider.getStudentProfile(enrollment.studentId)?.let { "${it.firstName} ${it.lastName}".trim() },
        courseId = enrollment.course.id!!,
        courseName = enrollment.course.name,
        attendanceDate = attendanceDate,
        status = status,
        notes = notes,
        recordedBy = recordedBy,
        recordedByName = null, // Can be populated if needed
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun buildAttendanceStatistics(
        enrollmentId: Long,
        studentId: Long,
        studentName: String?,
        records: List<Attendance>
    ): AttendanceStatisticsDto {
        val present = records.count { it.status == AttendanceStatus.PRESENT }.toLong()
        val absent = records.count { it.status == AttendanceStatus.ABSENT }.toLong()
        val late = records.count { it.status == AttendanceStatus.LATE }.toLong()
        val excusedAbsence = records.count { it.status == AttendanceStatus.EXCUSED_ABSENCE }.toLong()
        val sickLeave = records.count { it.status == AttendanceStatus.SICK_LEAVE }.toLong()
        val totalClasses = records.size.toLong()
        return AttendanceStatisticsDto(
            enrollmentId = enrollmentId,
            studentId = studentId,
            studentName = studentName,
            totalClasses = totalClasses,
            present = present,
            absent = absent,
            late = late,
            excusedAbsence = excusedAbsence,
            sickLeave = sickLeave,
            attendanceRate = attendanceRate(present, late, totalClasses)
        )
    }

    private fun attendanceRate(present: Long, late: Long, total: Long): Double =
        if (total > 0) ((present + late).toDouble() / total.toDouble()) * 100 else 0.0
}

