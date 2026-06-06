package com.sigep.courses.application.service

import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.dto.PageResponse
import com.sigep.common.domain.exception.ResourceNotFoundException
import com.sigep.common.domain.exception.BusinessException
import com.sigep.courses.application.dto.EnrollmentDto
import com.sigep.courses.application.dto.StudentEnrollmentHistoryDto
import com.sigep.courses.application.dto.UpdateEnrollmentRequest
import com.sigep.courses.application.dto.BulkEnrollmentRequest
import com.sigep.courses.domain.model.Enrollment
import com.sigep.courses.domain.model.EnrollmentStatus
import com.sigep.courses.domain.repository.EnrollmentRepository
import com.sigep.courses.domain.repository.CourseRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional
class EnrollmentService(
    private val enrollmentRepository: EnrollmentRepository,
    private val courseRepository: CourseRepository
) {

    private val logger = LoggerFactory.getLogger(EnrollmentService::class.java)

    fun getEnrollmentById(id: Long): EnrollmentDto {
        logger.info("Fetching enrollment with id: {}", id)
        val enrollment = enrollmentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Enrollment not found with id: $id") }
        return enrollment.toDto()
    }

    fun getStudentEnrollments(studentId: Long, page: Int, size: Int): PageResponse<EnrollmentDto> {
        logger.info("Fetching enrollments for student: {}", studentId)

        val pageable = PageRequest.of(page, size)
        val enrollmentsPage = enrollmentRepository.findByStudentId(studentId, pageable)

        return PageResponse(
            content = enrollmentsPage.content.map { it.toDto() },
            page = enrollmentsPage.number,
            size = enrollmentsPage.size,
            totalElements = enrollmentsPage.totalElements,
            totalPages = enrollmentsPage.totalPages
        )
    }

    fun getStudentEnrollmentHistory(studentId: Long): StudentEnrollmentHistoryDto {
        logger.info("Fetching enrollment history for student: {}", studentId)

        val pageable = PageRequest.of(0, 1000) // Get all enrollments
        val enrollmentsPage = enrollmentRepository.findByStudentId(studentId, pageable)
        val enrollments = enrollmentsPage.content.map { it.toDto() }

        return StudentEnrollmentHistoryDto(
            studentId = studentId,
            enrollments = enrollments,
            totalCourses = enrollments.size,
            completedCourses = enrollments.count { it.status == EnrollmentStatus.COMPLETED },
            activeCourses = enrollments.count { it.status == EnrollmentStatus.ACTIVE }
        )
    }

    fun getCourseEnrollments(courseId: Long, page: Int, size: Int): PageResponse<EnrollmentDto> {
        logger.info("Fetching enrollments for course: {}", courseId)

        val pageable = PageRequest.of(page, size)
        val enrollmentsPage = enrollmentRepository.findByCourseId(courseId, pageable)

        return PageResponse(
            content = enrollmentsPage.content.map { it.toDto() },
            page = enrollmentsPage.number,
            size = enrollmentsPage.size,
            totalElements = enrollmentsPage.totalElements,
            totalPages = enrollmentsPage.totalPages
        )
    }

    fun updateEnrollment(id: Long, request: UpdateEnrollmentRequest, actorUserId: Long?, actorRole: String?): EnrollmentDto {
        logger.info("Updating enrollment with id: {}", id)

        val enrollment = enrollmentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Enrollment not found with id: $id") }

        if (actorRole == "TEACHER" && actorUserId != enrollment.course.teacherId) {
            throw ForbiddenException("Teachers can only update enrollments from their assigned courses")
        }

        val updatedEnrollment = enrollment.copy(
            status = request.status ?: enrollment.status,
            finalGrade = request.finalGrade ?: enrollment.finalGrade,
            notes = request.notes ?: enrollment.notes,
            completionDate = if (request.status == EnrollmentStatus.COMPLETED && enrollment.completionDate == null)
                LocalDate.now() else enrollment.completionDate,
            updatedAt = LocalDateTime.now()
        )

        val savedEnrollment = enrollmentRepository.save(updatedEnrollment)
        logger.info("Enrollment updated successfully with id: {}", savedEnrollment.id)

        return savedEnrollment.toDto()
    }

    fun deleteEnrollment(id: Long) {
        logger.info("Deleting enrollment with id: {}", id)

        if (!enrollmentRepository.existsById(id)) {
            throw ResourceNotFoundException("Enrollment not found with id: $id")
        }

        enrollmentRepository.deleteById(id)
        logger.info("Enrollment deleted successfully with id: {}", id)
    }

    fun createBulkEnrollments(request: BulkEnrollmentRequest, actorUserId: Long?, actorRole: String?): List<EnrollmentDto> {
        logger.info("Creating bulk enrollments for course: {} with {} students", request.courseId, request.studentIds.size)

        // Validate course exists
        val course = courseRepository.findById(request.courseId)
            .orElseThrow { ResourceNotFoundException("Course not found with id: ${request.courseId}") }

        if (actorRole == "TEACHER" && actorUserId != course.teacherId) {
            throw ForbiddenException("Teachers can only manage enrollments from their assigned courses")
        }

        val uniqueStudentIds = request.studentIds.distinct()
        var activeEnrollmentCount = enrollmentRepository.countActiveEnrollmentsByCourse(request.courseId).toInt()

        val enrollments = mutableListOf<Enrollment>()

        for (studentId in uniqueStudentIds) {
            // Check if enrollment already exists
            val existingEnrollment = enrollmentRepository.findByStudentIdAndCourseId(studentId, request.courseId)
            if (existingEnrollment.isPresent && existingEnrollment.get().status == EnrollmentStatus.ACTIVE) {
                logger.warn("Student {} already enrolled in course {}", studentId, request.courseId)
                continue
            }

            // Check course capacity
            if (activeEnrollmentCount >= course.maxStudents) {
                throw BusinessException("Course has reached maximum capacity. Cannot enroll student $studentId")
            }

            val enrollment = Enrollment(
                studentId = studentId,
                course = course,
                enrollmentDate = LocalDate.now(),
                status = EnrollmentStatus.ACTIVE,
                finalGrade = null,
                completionDate = null,
                notes = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            enrollments.add(enrollment)
            activeEnrollmentCount++
        }

        val savedEnrollments = enrollmentRepository.saveAll(enrollments)
        logger.info("Bulk enrollments created successfully. Total: {}", savedEnrollments.size)

        return savedEnrollments.map { it.toDto() }
    }

    private fun Enrollment.toDto() = EnrollmentDto(
        id = id!!,
        studentId = studentId,
        studentName = null,
        courseId = course.id!!,
        courseName = course.name,
        enrollmentDate = enrollmentDate,
        status = status,
        finalGrade = finalGrade,
        completionDate = completionDate,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

