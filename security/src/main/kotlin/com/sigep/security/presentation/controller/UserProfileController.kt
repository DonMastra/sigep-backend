package com.sigep.security.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.security.application.annotation.RequireStaffOrGuardian
import com.sigep.security.application.dto.UserProfileDto
import com.sigep.security.application.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "API for authenticated user profile")
@SecurityRequirement(name = "Bearer Authentication")
class UserProfileController(
    private val authService: AuthService
) {

    @GetMapping("/me")
    @RequireStaffOrGuardian
    @Operation(summary = "Get authenticated user profile")
    fun getMyProfile(httpRequest: HttpServletRequest): ResponseEntity<ApiResponse<UserProfileDto>> {
        val userId = httpRequest.getAttribute("userId") as? Long
            ?: throw UnauthorizedException("Token inválido o sin userId")

        val profile = authService.getMyProfile(userId)
        return ResponseEntity.ok(ApiResponse.success(profile))
    }
}

