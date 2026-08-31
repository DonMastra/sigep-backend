package com.sigep.security.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.exception.ValidationException
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.dto.AdminUserPageDto
import com.sigep.security.application.dto.UserRoleAssignmentsDto
import com.sigep.security.application.service.AuthService
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.UserRole
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*

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

    @GetMapping("/{userId}/roles")
    @Operation(summary = "Consultar roles asignados")
    fun getRoles(@PathVariable userId: Long): ResponseEntity<ApiResponse<UserRoleAssignmentsDto>> =
        ResponseEntity.ok(ApiResponse.success(authService.getUserRoleAssignments(userId)))

    @PutMapping("/{userId}/roles/{role}")
    @Operation(summary = "Asignar rol", description = "La asignacion es idempotente y conserva auditoria del administrador")
    fun grantRole(
        @PathVariable userId: Long,
        @PathVariable role: String,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<UserRoleAssignmentsDto>> {
        val actorUserId = requireActorUserId(httpRequest)
        return ResponseEntity.ok(
            ApiResponse.success(authService.grantUserRole(userId, requireRole(role), actorUserId), "Role assigned")
        )
    }

    @DeleteMapping("/{userId}/roles/{role}")
    @Operation(summary = "Revocar rol", description = "Nunca permite dejar una cuenta sin roles activos")
    fun revokeRole(
        @PathVariable userId: Long,
        @PathVariable role: String,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<UserRoleAssignmentsDto>> {
        val actorUserId = requireActorUserId(httpRequest)
        return ResponseEntity.ok(
            ApiResponse.success(authService.revokeUserRole(userId, requireRole(role), actorUserId), "Role revoked")
        )
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

    private fun requireRole(role: String): UserRole = parseRole(role)
        ?: throw ValidationException("Role is required")

    private fun requireActorUserId(request: HttpServletRequest): Long =
        request.getAttribute("userId") as? Long
            ?: throw com.sigep.common.application.exception.UnauthorizedException("Token invalido o sin userId")

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

