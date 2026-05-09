package com.sigep.security.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.exception.ValidationException
import com.sigep.security.application.annotation.RequireAdmin
import com.sigep.security.application.dto.RegistrationDecisionRequest
import com.sigep.security.application.dto.RegistrationRequestDto
import com.sigep.security.application.dto.RegistrationRequestPageDto
import com.sigep.security.application.service.AuthService
import com.sigep.security.domain.model.AccountStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/registration-requests")
@Tag(name = "Registration Requests", description = "Administracion de solicitudes de registro")
@RequireAdmin
class RegistrationRequestAdminController(
    private val authService: AuthService
) {

    private val logger = LoggerFactory.getLogger(RegistrationRequestAdminController::class.java)

    @GetMapping
    @Operation(summary = "Listar solicitudes de registro", description = "Lista solicitudes con filtro opcional por estado")
    fun listRequests(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "createdAt") sort: String,
        @RequestParam(defaultValue = "DESC") order: String
    ): ResponseEntity<ApiResponse<RegistrationRequestPageDto>> {
        val parsedStatus = parseStatus(status)
        logger.info(
            "Listing registration requests - status: {}, page: {}, size: {}, sort: {}, order: {}",
            parsedStatus,
            page,
            size,
            sort,
            order
        )

        val response = authService.getRegistrationRequests(parsedStatus, page, size, sort, order)
        return ResponseEntity.ok(ApiResponse.success(response, "OK"))
    }

    @PutMapping("/{requestId}/approve")
    @Operation(summary = "Aprobar solicitud", description = "Transicion permitida: PENDING_APPROVAL -> ACTIVE")
    fun approveRequest(
        @PathVariable requestId: String,
        @RequestBody(required = false) request: RegistrationDecisionRequest?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<RegistrationRequestDto>> {
        val adminId = httpRequest.getAttribute("userId") as Long
        logger.info("Approving registration request {} by admin {}", requestId, adminId)
        val response = authService.approveRegistrationRequest(requestId, adminId, request?.adminNotes)
        logger.info("Registration request {} approved successfully", requestId)
        return ResponseEntity.ok(ApiResponse.success(response, "Solicitud aprobada"))
    }

    @PutMapping("/{requestId}/reject")
    @Operation(summary = "Rechazar solicitud", description = "Transicion permitida: PENDING_APPROVAL -> REJECTED")
    fun rejectRequest(
        @PathVariable requestId: String,
        @RequestBody(required = false) request: RegistrationDecisionRequest?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<RegistrationRequestDto>> {
        val adminId = httpRequest.getAttribute("userId") as Long
        logger.info("Rejecting registration request {} by admin {}", requestId, adminId)
        val response = authService.rejectRegistrationRequest(requestId, adminId, request?.adminNotes)
        logger.info("Registration request {} rejected successfully", requestId)
        return ResponseEntity.ok(ApiResponse.success(response, "Solicitud rechazada"))
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
            logger.warn("Invalid registration status received in query param: {}", status)
            throw ValidationException("Invalid registration status: $status")
        }
    }
}




