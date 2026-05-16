package com.sigep.security.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.exception.ValidationException
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.dto.AdminUserPageDto
import com.sigep.security.application.service.AuthService
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.UserRole
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "Catalogo de usuarios para gestion administrativa")
@RequireAdmin
class AdminUserController(
    private val authService: AuthService
) {

    private val logger = LoggerFactory.getLogger(AdminUserController::class.java)

    @GetMapping
    @Operation(summary = "Listar usuarios", description = "Lista usuarios con filtros opcionales por rol, estado y activo")
    fun listUsers(
        @RequestParam(required = false) role: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "username") sort: String,
        @RequestParam(defaultValue = "ASC") order: String
    ): ResponseEntity<ApiResponse<AdminUserPageDto>> {
        val parsedRole = parseRole(role)
        val parsedStatus = parseStatus(status)

        logger.info(
            "Listing users - role: {}, status: {}, active: {}, page: {}, size: {}, sort: {}, order: {}",
            parsedRole,
            parsedStatus,
            active,
            page,
            size,
            sort,
            order
        )

        val response = authService.getUsersForAdmin(parsedRole, parsedStatus, active, page, size, sort, order)
        return ResponseEntity.ok(ApiResponse.success(response, "OK"))
    }

    private fun parseRole(role: String?): UserRole? {
        if (
            role.isNullOrBlank() ||
            role.equals("ALL", ignoreCase = true) ||
            role.equals("null", ignoreCase = true) ||
            role.equals("undefined", ignoreCase = true)
        ) {
            return null
        }

        return try {
            UserRole.valueOf(role.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            logger.warn("Invalid user role received in query param: {}", role)
            throw ValidationException("Invalid user role: $role")
        }
    }

    private fun parseStatus(status: String?): AccountStatus? {
        if (
            status.isNullOrBlank() ||
            status.equals("ALL", ignoreCase = true) ||
            status.equals("null", ignoreCase = true) ||
            status.equals("undefined", ignoreCase = true)
        ) {
            return null
        }

        return try {
            AccountStatus.valueOf(status.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            logger.warn("Invalid user status received in query param: {}", status)
            throw ValidationException("Invalid user status: $status")
        }
    }
}

