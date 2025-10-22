package com.sigep.security.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.sigep.common.application.dto.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class CustomAccessDeniedHandler(
    private val objectMapper: ObjectMapper
) : AccessDeniedHandler {

    companion object {
        private val log = LoggerFactory.getLogger(CustomAccessDeniedHandler::class.java)
    }

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        val username = authentication?.name ?: "anonymous"

        log.warn(
            "Access denied for user: {} attempting to access: {} from IP: {}",
            username,
            request.requestURI,
            request.remoteAddr
        )

        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val errorResponse = ApiResponse.error<Unit>(
            message = "Access denied. You don't have permission to access this resource."
        )

        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}

