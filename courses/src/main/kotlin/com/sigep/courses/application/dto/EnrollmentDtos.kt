package com.sigep.courses.application.dto

import com.sigep.courses.domain.model.EnrollmentStatus
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class EnrollmentDto(
    val id: Long,
    val studentId: Long,
    val studentName: String? = null,
    val courseId: Long,
    val courseName: String,
    val enrollmentDate: LocalDate,
    val status: EnrollmentStatus,
    val finalGrade: BigDecimal?,
    val completionDate: LocalDate?,
    val notes: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class UpdateEnrollmentRequest(
    val status: EnrollmentStatus?,

    @field:DecimalMin("0.00")
    @field:DecimalMax("100.00")
    val finalGrade: BigDecimal?,

    val notes: String?
)

data class StudentEnrollmentHistoryDto(
    val studentId: Long,
    val enrollments: List<EnrollmentDto>,
    val totalCourses: Int,
    val completedCourses: Int,
    val activeCourses: Int
)

data class BulkEnrollmentRequest(
    @field:NotNull(message = "Course ID is required")
    val courseId: Long,

    @field:NotEmpty(message = "Student IDs cannot be empty")
    val studentIds: List<Long>
)

