package com.sigep.security.application.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.sigep.security.domain.model.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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
    val role: UserRole
)

data class UserDto(
    val id: Long,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: UserRole,
    val active: Boolean
)

data class RefreshTokenRequest @JsonCreator constructor(
    @JsonProperty("refreshToken")
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)

