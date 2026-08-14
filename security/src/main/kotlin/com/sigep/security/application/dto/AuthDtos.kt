package com.sigep.security.application.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalDateTime

data class LoginRequest @JsonCreator constructor(
    @JsonProperty("username")
    @field:NotBlank(message = "Username is required")
    val username: String,

    @JsonProperty("password")
    @field:NotBlank(message = "Password is required")
    val password: String
)

data class LoginResponse(
    val token: String,
    val refreshToken: String,
    val user: UserDto
)

data class RegisterRequest @JsonCreator constructor(
    @JsonProperty("username")
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50)
    val username: String,

    @JsonProperty("email")
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @JsonProperty("password")
    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, max = 100)
    val password: String,

    @JsonProperty("firstName")
    @field:NotBlank(message = "First name is required")
    val firstName: String,

    @JsonProperty("lastName")
    @field:NotBlank(message = "Last name is required")
    val lastName: String,

    @JsonProperty("role")
    val role: UserRole,

    @JsonProperty("phoneNumber")
    val phoneNumber: String? = null,

    @JsonProperty("address")
    val address: String? = null,

    @JsonProperty("dateOfBirth")
    val dateOfBirth: LocalDate? = null,

    @JsonProperty("documentNumber")
    val documentNumber: String? = null,

    @JsonProperty("emergencyContact")
    val emergencyContact: String? = null
)

data class UserDto(
    val id: Long,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: UserRole,
    val status: AccountStatus,
    val active: Boolean
)

data class AdminUserPageDto(
    val items: List<UserDto>,
    val page: Int,
    val size: Int,
    val total: Long
)

data class UserProfileDto(
    val id: Long,
    val username: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phoneNumber: String?,
    val address: String?,
    val dateOfBirth: LocalDate?,
    val documentNumber: String?,
    val emergencyContact: String?,
    val role: UserRole,
    val status: AccountStatus,
    val active: Boolean
)

data class RegistrationStatusResponseDto(
    val username: String,
    val status: AccountStatus,
    val adminNotes: String?,
    val reviewedAt: LocalDateTime?
)

data class RegistrationRequestDto(
    val id: String,
    val userId: Long,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val requestedRole: UserRole,
    val status: AccountStatus,
    val createdAt: LocalDateTime,
    val reviewedAt: LocalDateTime?,
    val reviewedBy: Long?,
    val adminNotes: String?
)

data class RegistrationRequestPageDto(
    val items: List<RegistrationRequestDto>,
    val page: Int,
    val size: Int,
    val total: Long
)

data class RegistrationDecisionRequest @JsonCreator constructor(
    @JsonProperty("adminNotes")
    val adminNotes: String? = null
)

data class RefreshTokenRequest @JsonCreator constructor(
    @JsonProperty("refreshToken")
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)

enum class AdminGuardianActivationMode { ACTIVE, INVITE }

data class AdminCreateGuardianRequest(
    @field:NotBlank @field:Size(min = 3, max = 50)
    val username: String,
    @field:NotBlank @field:Email
    val email: String,
    @field:NotBlank
    val firstName: String,
    @field:NotBlank
    val lastName: String,
    val activationMode: AdminGuardianActivationMode,
    @field:Size(min = 12, max = 100)
    val initialPassword: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val dateOfBirth: LocalDate? = null,
    val documentNumber: String? = null,
    val emergencyContact: String? = null
)

data class AdminCreateGuardianResponse(
    val user: UserDto,
    val activationMode: AdminGuardianActivationMode,
    val invitationToken: String? = null,
    val invitationExpiresAt: LocalDateTime? = null
)

data class AcceptGuardianInvitationRequest(
    @field:NotBlank
    val token: String,
    @field:NotBlank @field:Size(min = 12, max = 100)
    val password: String
)

