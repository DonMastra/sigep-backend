package com.sigep.courses.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.domain.exception.BusinessException
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.courses.application.dto.*
import com.sigep.courses.domain.model.Attendance
import com.sigep.courses.domain.model.AttendanceStatus
import com.sigep.courses.domain.repository.AttendanceRepository
import com.sigep.courses.domain.repository.EnrollmentRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional
class AttendanceService(
    private val attendanceRepository: AttendanceRepository,
    private val enrollmentRepository: EnrollmentRepository
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

    fun getAttendanceByStudent(studentId: Long, page: Int, size: Int): PageResponse<AttendanceDto> {
        logger.info("Fetching attendance for student: {}", studentId)

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

        // Verificar si ya existe un registro de asistencia para este día
        val existingAttendance = attendanceRepository.findByEnrollmentIdAndAttendanceDate(
            request.enrollmentId,
            request.attendanceDate
        )

        if (existingAttendance.isPresent) {
            throw BusinessException("Attendance already recorded for this date. Use update instead.")
        }

        val attendance = Attendance(
            enrollment = enrollment,
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
        val resolvedCourseId = resolveBulkCourseId(request)
        logger.info("Recording bulk attendance for course {} on {}", resolvedCourseId, request.date)

        val savedAttendances = request.records.map { record ->
            val enrollment = enrollmentRepository.findById(record.enrollmentId)
                .orElseThrow { ResourceNotFoundException("Enrollment not found with id: ${record.enrollmentId}") }

            // Verificar que el enrollment pertenezca al curso
            if (enrollment.course.id != resolvedCourseId) {
                throw BusinessException("Enrollment ${record.enrollmentId} does not belong to course $resolvedCourseId")
            }

            // Verificar si ya existe, si existe actualizar, si no crear
            val existingAttendance = attendanceRepository.findByEnrollmentIdAndAttendanceDate(
                record.enrollmentId,
                request.date
            )

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

    private fun resolveBulkCourseId(request: BulkAttendanceRequest): Long {
        if (request.courseId != null) {
            return request.courseId
        }

        if (request.records.isEmpty()) {
            throw BusinessException("Attendance records cannot be empty")
        }

        val enrollments = request.records.map { record ->
            enrollmentRepository.findById(record.enrollmentId)
                .orElseThrow { ResourceNotFoundException("Enrollment not found with id: ${record.enrollmentId}") }
        }

        val courseIds = enrollments.mapNotNull { it.course.id }.distinct()
        if (courseIds.size != 1) {
            throw BusinessException("All attendance records must belong to the same course")
        }

        return courseIds.first()
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
            studentName = null, // Can be populated if needed
            totalClasses = totalClasses,
            present = present,
            absent = absent,
            late = late,
            excusedAbsence = excusedAbsence,
            sickLeave = sickLeave,
            attendanceRate = attendanceRate
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
        studentId = enrollment.studentId,
        studentName = null, // Can be populated via join if needed
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
}

