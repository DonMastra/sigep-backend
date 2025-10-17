package com.sigep.students.application.dto

import com.sigep.students.domain.model.StudentStatus
import jakarta.validation.constraints.*
import java.time.LocalDate
import java.time.LocalDateTime

data class StudentDto(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val dateOfBirth: LocalDate,
    val address: String,
    val guardianId: Long,
    val enrollmentDate: LocalDate,
    val status: StudentStatus,
    val currentLevel: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateStudentRequest(
    @field:NotBlank(message = "First name is required")
    @field:Size(min = 2, max = 100)
    val firstName: String,

    @field:NotBlank(message = "Last name is required")
    @field:Size(min = 2, max = 100)
    val lastName: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Phone is required")
    @field:Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Invalid phone format")
    val phone: String,

    @field:NotNull(message = "Date of birth is required")
    @field:Past(message = "Date of birth must be in the past")
    val dateOfBirth: LocalDate,

    @field:NotBlank(message = "Address is required")
    val address: String,

    @field:NotNull(message = "Guardian ID is required")
    val guardianId: Long,

    @field:NotBlank(message = "Current level is required")
    val currentLevel: String
)

data class UpdateStudentRequest(
    @field:Size(min = 2, max = 100)
    val firstName: String?,

    @field:Size(min = 2, max = 100)
    val lastName: String?,

    @field:Email(message = "Invalid email format")
    val email: String?,

    @field:Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Invalid phone format")
    val phone: String?,

    val address: String?,

    val currentLevel: String?,

    val status: StudentStatus?
)

