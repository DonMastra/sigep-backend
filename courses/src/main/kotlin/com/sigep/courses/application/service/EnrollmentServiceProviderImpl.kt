package com.sigep.courses.application.service

import com.sigep.common.application.dto.EnrollmentSummaryDto
import com.sigep.common.application.service.EnrollmentServiceProvider
import com.sigep.courses.domain.repository.EnrollmentRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

/**
 * Implementación del EnrollmentServiceProvider
 * Permite a otros módulos obtener información de enrollments sin dependencias circulares
 */
@Service
class EnrollmentServiceProviderImpl(
    private val enrollmentRepository: EnrollmentRepository
) : EnrollmentServiceProvider {

    override fun getEnrollmentsByStudent(studentId: Long): List<EnrollmentSummaryDto> {
        val pageable = PageRequest.of(0, 1000)
        val enrollments = enrollmentRepository.findByStudentId(studentId, pageable)

        return enrollments.content.map { enrollment ->
            EnrollmentSummaryDto(
                id = enrollment.id!!,
                studentId = enrollment.studentId,
                courseId = enrollment.course.id!!,
                courseName = enrollment.course.name,
                courseLevel = enrollment.course.level.name,
                enrollmentDate = enrollment.enrollmentDate,
                status = enrollment.status.name,
                finalGrade = enrollment.finalGrade,
                completionDate = enrollment.completionDate
            )
        }
    }

    override fun getCurrentEnrollmentByStudent(studentId: Long): EnrollmentSummaryDto? {
        val pageable = PageRequest.of(0, 1)
        val enrollments = enrollmentRepository.findByStudentIdAndStatus(
            studentId,
            com.sigep.courses.domain.model.EnrollmentStatus.ACTIVE,
            pageable
        )

        return enrollments.content.firstOrNull()?.let { enrollment ->
            EnrollmentSummaryDto(
                id = enrollment.id!!,
                studentId = enrollment.studentId,
                courseId = enrollment.course.id!!,
                courseName = enrollment.course.name,
                courseLevel = enrollment.course.level.name,
                enrollmentDate = enrollment.enrollmentDate,
                status = enrollment.status.name,
                finalGrade = enrollment.finalGrade,
                completionDate = enrollment.completionDate
            )
        }
    }

    override fun getEnrollmentsByStudentAndStatus(studentId: Long, status: String): List<EnrollmentSummaryDto> {
        val pageable = PageRequest.of(0, 1000)
        val enrollmentStatus = try {
            com.sigep.courses.domain.model.EnrollmentStatus.valueOf(status)
        } catch (e: IllegalArgumentException) {
            return emptyList()
        }

        val enrollments = enrollmentRepository.findByStudentIdAndStatus(studentId, enrollmentStatus, pageable)

        return enrollments.content.map { enrollment ->
            EnrollmentSummaryDto(
                id = enrollment.id!!,
                studentId = enrollment.studentId,
                courseId = enrollment.course.id!!,
                courseName = enrollment.course.name,
                courseLevel = enrollment.course.level.name,
                enrollmentDate = enrollment.enrollmentDate,
                status = enrollment.status.name,
                finalGrade = enrollment.finalGrade,
                completionDate = enrollment.completionDate
            )
        }
    }
}

