package com.sigep.students.application.dto

import com.sigep.common.application.dto.EnrollmentSummaryDto
import jakarta.validation.constraints.*
import java.time.LocalDate
import java.time.LocalDateTime

data class StudentDto(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val documentNumber: String,
    val dateOfBirth: LocalDate,
    val enrollmentDate: LocalDate,
    val guardianId: Long?,
    val currentCourseId: Long?,
    val currentCourseName: String?,
    val active: Boolean,
    val phoneNumber: String,
    val address: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * DTO detallado de Student con toda la información y historial de cursos
 */
data class StudentDetailDto(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val documentNumber: String,
    val dateOfBirth: LocalDate,
    val address: String,
    val phoneNumber: String,
    val emergencyContact: String,
    val enrollmentDate: LocalDate,
    val guardianId: Long?,
    val medicalNotes: String?,
    val currentCourseId: Long?,
    val currentCourseName: String?,
    val active: Boolean,
    val courseHistory: List<EnrollmentSummaryDto>,  // Usar DTO de common
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)


data class CreateStudentRequest(
    @field:NotBlank(message = "First name is required")
    @field:Size(min = 1, max = 100)
    val firstName: String,

    @field:NotBlank(message = "Last name is required")
    @field:Size(min = 1, max = 100)
    val lastName: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Document number is required")
    val documentNumber: String,

    @field:NotNull(message = "Date of birth is required")
    @field:Past(message = "Date of birth must be in the past")
    val dateOfBirth: LocalDate,

    @field:NotBlank(message = "Address is required")
    val address: String,

    @field:NotBlank(message = "Phone number is required")
    val phoneNumber: String,

    @field:NotBlank(message = "Emergency contact is required")
    val emergencyContact: String,

    val enrollmentDate: LocalDate? = null,

    val active: Boolean = true,

    val guardianId: Long?,

    val medicalNotes: String?
)

data class UpdateStudentRequest(
    @field:Size(min = 1, max = 100)
    val firstName: String?,

    @field:Size(min = 1, max = 100)
    val lastName: String?,

    @field:Email(message = "Invalid email format")
    val email: String?,

    val documentNumber: String?,

    @field:Past(message = "Date of birth must be in the past")
    val dateOfBirth: LocalDate?,

    val enrollmentDate: LocalDate?,

    val address: String?,

    val phoneNumber: String?,

    val emergencyContact: String?,

    val guardianId: Long?,

    val medicalNotes: String?,

    val active: Boolean?
)

