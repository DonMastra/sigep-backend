package com.sigep.security.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.security.application.dto.*
import com.sigep.security.application.service.AuthService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "API for managing authentication flow")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ApiResponse<LoginResponse>> {
        val response = authService.login(request)
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"))
    }

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<ApiResponse<UserDto>> {
        val user = authService.register(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(user, "User registered successfully"))
    }

    @PostMapping("/refresh-token")
    fun refreshToken(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<ApiResponse<LoginResponse>> {
        val response = authService.refreshToken(request)
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"))
    }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<ApiResponse<Unit>> {
        // In a stateless JWT system, logout is typically handled on the client side
        // However, you can implement token blacklisting with Redis if needed
        return ResponseEntity.ok(ApiResponse.successNoContent("Logout successful"))
    }
}
