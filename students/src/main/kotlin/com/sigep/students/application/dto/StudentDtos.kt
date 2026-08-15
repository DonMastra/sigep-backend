package com.sigep.students.application.dto

import com.sigep.common.application.dto.EnrollmentSummaryDto
import com.sigep.students.domain.model.StudentDocumentType
import jakarta.validation.constraints.*
import java.time.LocalDate
import java.time.LocalDateTime

data class StudentDto(
    val id: Long,
    val studentNumber: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val documentType: StudentDocumentType,
    val documentCountry: String,
    val documentNumber: String?,
    val dateOfBirth: LocalDate,
    val enrollmentDate: LocalDate,
    val guardianId: Long?,
    val currentCourseId: Long?,
    val currentCourseName: String?,
    val currentCourses: List<EnrollmentSummaryDto>,
    val currentLevel: String,
    val active: Boolean,
    val photoUrl: String?,
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
    val studentNumber: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val documentType: StudentDocumentType,
    val documentCountry: String,
    val documentNumber: String?,
    val dateOfBirth: LocalDate,
    val address: String,
    val phoneNumber: String,
    val emergencyContact: String,
    val enrollmentDate: LocalDate,
    val guardianId: Long?,
    val medicalNotes: String?,
    val currentCourseId: Long?,
    val currentCourseName: String?,
    val currentCourses: List<EnrollmentSummaryDto>,
    val currentLevel: String,
    val active: Boolean,
    val photoUrl: String?,
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

    val documentType: StudentDocumentType = StudentDocumentType.DNI,

    @field:Pattern(regexp = "^[A-Za-z]{2}$", message = "Document country must be ISO alpha-2")
    val documentCountry: String = "AR",

    val documentNumber: String? = null,

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

    val guardianId: Long? = null,

    val medicalNotes: String? = null,

    val currentLevel: String = "BEGINNER"
)

data class GuardianStudentRegistrationRequest(
    @field:Size(min = 1, max = 100)
    val firstName: String? = null,

    @field:Size(min = 1, max = 100)
    val lastName: String? = null,

    @field:Email(message = "Invalid email format")
    val email: String? = null,

    val documentType: StudentDocumentType = StudentDocumentType.DNI,

    @field:Pattern(regexp = "^[A-Za-z]{2}$", message = "Document country must be ISO alpha-2")
    val documentCountry: String = "AR",

    val documentNumber: String? = null,

    @field:Past(message = "Date of birth must be in the past")
    val dateOfBirth: LocalDate? = null,

    val address: String? = null,

    val phoneNumber: String? = null,

    val emergencyContact: String? = null,

    val medicalNotes: String? = null,

    val useGuardianProfileData: Boolean = false
)

data class UpdateStudentRequest(
    @field:Size(min = 1, max = 100)
    val firstName: String? = null,

    @field:Size(min = 1, max = 100)
    val lastName: String? = null,

    @field:Email(message = "Invalid email format")
    val email: String? = null,

    val documentType: StudentDocumentType? = null,

    @field:Pattern(regexp = "^[A-Za-z]{2}$", message = "Document country must be ISO alpha-2")
    val documentCountry: String? = null,

    val documentNumber: String? = null,

    @field:Past(message = "Date of birth must be in the past")
    val dateOfBirth: LocalDate? = null,

    val enrollmentDate: LocalDate? = null,

    val address: String? = null,

    val phoneNumber: String? = null,

    val emergencyContact: String? = null,

    val guardianId: Long? = null,

    val medicalNotes: String? = null,

    val active: Boolean? = null,

    val currentLevel: String? = null
)

data class StudentIdentityMatchRequest(
    val documentType: StudentDocumentType = StudentDocumentType.DNI,
    @field:Pattern(regexp = "^[A-Za-z]{2}$", message = "Document country must be ISO alpha-2")
    val documentCountry: String = "AR",
    val documentNumber: String? = null,
    @field:Size(min = 1, max = 100) val firstName: String? = null,
    @field:Size(min = 1, max = 100) val lastName: String? = null,
    @field:Past val dateOfBirth: LocalDate? = null
)

data class StudentIdentityMatchDto(
    val outcome: StudentIdentityMatchOutcome,
    val studentId: Long? = null,
    val displayName: String? = null
)

enum class StudentIdentityMatchOutcome {
    NONE,
    OWNED,
    UNASSIGNED,
    VERIFICATION_REQUIRED
}

data class LinkStudentGuardianRequest(
    @field:NotNull val guardianId: Long,
    @field:NotBlank @field:Size(max = 500) val reason: String
)

