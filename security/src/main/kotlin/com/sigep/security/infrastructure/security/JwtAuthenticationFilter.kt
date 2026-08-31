package com.sigep.security.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.sigep.common.application.dto.ErrorResponse
import com.sigep.security.application.service.UserRoleAssignmentService
import com.sigep.security.domain.model.UserRole
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val objectMapper: ObjectMapper,
    private val roleAssignmentService: UserRoleAssignmentService
) : OncePerRequestFilter() {

    companion object {
        private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val jwt = getJwtFromRequest(request)

            if (jwt != null && jwtTokenProvider.validateToken(jwt) && jwtTokenProvider.isAccessToken(jwt)) {
                val username = jwtTokenProvider.getUsernameFromToken(jwt)
                val role = jwtTokenProvider.getRoleFromToken(jwt)
                val userId = jwtTokenProvider.getUserIdFromToken(jwt)
                val mustChangePassword = jwtTokenProvider.getMustChangePasswordFromToken(jwt)

                val parsedRole = runCatching { UserRole.valueOf(role) }.getOrNull()
                if (parsedRole == null || !roleAssignmentService.isRoleUsableForSession(userId, parsedRole)) {
                    log.warn("Rejected token with inactive role {} for user id {}", role, userId)
                    filterChain.doFilter(request, response)
                    return
                }

                if (mustChangePassword && !isAllowedWhilePasswordChangeIsRequired(request)) {
                    writePasswordChangeRequired(response, request)
                    return
                }

                val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
                val authentication = UsernamePasswordAuthenticationToken(username, null, authorities)
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                // Store userId in authentication details for easy access
                request.setAttribute("userId", userId)
                request.setAttribute("userRole", role)

                SecurityContextHolder.getContext().authentication = authentication

                log.debug("Authenticated request with role {}", role)
            }
        } catch (ex: Exception) {
            log.error("Could not set user authentication in security context ({})", ex.javaClass.simpleName)
        }

        filterChain.doFilter(request, response)
    }

    private fun getJwtFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }

    private fun isAllowedWhilePasswordChangeIsRequired(request: HttpServletRequest): Boolean {
        if (request.method.equals("OPTIONS", ignoreCase = true)) return true
        if (request.requestURI.startsWith("/api/v1/auth/")) return true
        return request.method.equals("PATCH", ignoreCase = true) &&
            request.requestURI == "/api/v1/users/me/password"
    }

    private fun writePasswordChangeRequired(response: HttpServletResponse, request: HttpServletRequest) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json"
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(
            response.writer,
            ErrorResponse(
                status = HttpServletResponse.SC_FORBIDDEN,
                code = "PASSWORD_CHANGE_REQUIRED",
                message = "Debe cambiar su contrasena temporal antes de continuar",
                path = request.requestURI
            )
        )
    }
}
