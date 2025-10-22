package com.sigep.courses.application.service

import com.sigep.common.application.dto.PageResponse
import com.sigep.common.application.dto.PaginationInfo
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.courses.application.dto.EnrollmentDto
import com.sigep.courses.application.dto.StudentEnrollmentHistoryDto
import com.sigep.courses.application.dto.UpdateEnrollmentRequest
import com.sigep.courses.domain.model.Enrollment
import com.sigep.courses.domain.model.EnrollmentStatus
import com.sigep.courses.domain.repository.EnrollmentRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional
class EnrollmentService(
    private val enrollmentRepository: EnrollmentRepository
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
            items = enrollmentsPage.content.map { it.toDto() },
            pagination = PaginationInfo(
                page = page,
                limit = size,
                total = enrollmentsPage.totalElements,
                totalPages = enrollmentsPage.totalPages
            )
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
            items = enrollmentsPage.content.map { it.toDto() },
            pagination = PaginationInfo(
                page = page,
                limit = size,
                total = enrollmentsPage.totalElements,
                totalPages = enrollmentsPage.totalPages
            )
        )
    }

    fun updateEnrollment(id: Long, request: UpdateEnrollmentRequest): EnrollmentDto {
        logger.info("Updating enrollment with id: {}", id)

        val enrollment = enrollmentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Enrollment not found with id: $id") }

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

    private fun Enrollment.toDto() = EnrollmentDto(
        id = id!!,
        studentId = studentId,
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

