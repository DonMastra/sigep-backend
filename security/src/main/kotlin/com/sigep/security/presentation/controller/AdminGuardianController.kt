package com.sigep.security.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.exception.UnauthorizedException
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.dto.AdminCreateGuardianRequest
import com.sigep.security.application.dto.AdminCreateGuardianResponse
import com.sigep.security.application.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/guardians")
@Tag(name = "Admin Guardians", description = "Administrative guardian creation and invitation")
@SecurityRequirement(name = "Bearer Authentication")
@RequireAdmin
class AdminGuardianController(
    private val authService: AuthService
) {
    @PostMapping
    @Operation(summary = "Create or invite guardian", description = "Keeps public registration approval while adding an ADMIN-originated ACTIVE or INVITE path")
    fun createGuardian(
        @Valid @RequestBody request: AdminCreateGuardianRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<AdminCreateGuardianResponse>> {
        val adminId = httpRequest.getAttribute("userId") as? Long
            ?: throw UnauthorizedException("Token invalid or missing userId")
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(authService.createGuardianByAdmin(request, adminId), "Guardian created"))
    }
}
