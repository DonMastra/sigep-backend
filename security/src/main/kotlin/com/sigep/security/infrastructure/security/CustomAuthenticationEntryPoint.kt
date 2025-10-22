package com.sigep.security.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.sigep.common.application.dto.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    companion object {
        private val log = LoggerFactory.getLogger(CustomAuthenticationEntryPoint::class.java)
    }

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        log.warn("Unauthorized access attempt to: {} from IP: {}",
            request.requestURI,
            request.remoteAddr
        )

        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val errorResponse = ApiResponse.error<Unit>(
            message = "Authentication required. Please provide a valid JWT token."
        )

        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}

