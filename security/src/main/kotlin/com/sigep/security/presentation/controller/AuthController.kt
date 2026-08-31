package com.sigep.security.presentation.controller

import com.sigep.common.application.dto.ApiResponse
import com.sigep.common.application.exception.ForbiddenException
import com.sigep.security.application.dto.*
import com.sigep.security.application.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "API for managing authentication flow")
class AuthController(
    private val authService: AuthService,
    @Value("\${app.registration.public-enabled:true}")
    private val publicRegistrationEnabled: Boolean = true
) {

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion", description = "Solo permite login para cuentas ACTIVE")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ApiResponse<LoginResponse>> {
        val response = authService.login(request)
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"))
    }

    @PostMapping("/role-selections")
    @Operation(summary = "Seleccionar espacio inicial", description = "Canjea un token de seleccion por una sesion limitada a un rol asignado")
    fun selectRole(@Valid @RequestBody request: RoleSelectionRequest): ResponseEntity<ApiResponse<LoginResponse>> =
        ResponseEntity.ok(ApiResponse.success(authService.selectRole(request), "Role selected successfully"))

    @PutMapping("/role-context")
    @Operation(summary = "Cambiar espacio activo", description = "Rota los tokens y activa un unico rol; elevar a ADMIN exige la clave actual")
    fun switchRole(
        @Valid @RequestBody request: RoleContextRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val userId = httpRequest.getAttribute("userId") as? Long
            ?: throw com.sigep.common.application.exception.UnauthorizedException("Token invalido o sin userId")
        val currentRole = httpRequest.getAttribute("userRole") as? String
        return ResponseEntity.ok(
            ApiResponse.success(authService.switchRole(userId, currentRole, request), "Role context changed successfully")
        )
    }

    @PostMapping("/register")
    @Operation(summary = "Registro publico", description = "Crea la cuenta en estado PENDING_APPROVAL")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<ApiResponse<UserDto>> {
        if (!publicRegistrationEnabled) {
            throw ForbiddenException(
                message = "Public registration is disabled in this environment",
                code = "PUBLIC_REGISTRATION_DISABLED"
            )
        }
        val user = authService.register(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(user, "Registro creado. Pendiente de aprobacion administrativa."))
    }

    @PostMapping("/guardian-invitations/accept")
    @Operation(summary = "Aceptar invitacion de tutor", description = "Activa una cuenta GUARDIAN invitada por ADMIN y define su clave")
    fun acceptGuardianInvitation(
        @Valid @RequestBody request: AcceptGuardianInvitationRequest
    ): ResponseEntity<ApiResponse<UserDto>> =
        ResponseEntity.ok(ApiResponse.success(authService.acceptGuardianInvitation(request), "Guardian invitation accepted"))

    @GetMapping("/registration-status")
    @Operation(summary = "Consultar estado de registro", description = "Retorna el estado de cuenta para flujo de login")
    fun registrationStatus(@RequestParam username: String): ResponseEntity<ApiResponse<RegistrationStatusResponseDto>> {
        val response = authService.getRegistrationStatus(username)
        return ResponseEntity.ok(ApiResponse.success(response, "OK"))
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
