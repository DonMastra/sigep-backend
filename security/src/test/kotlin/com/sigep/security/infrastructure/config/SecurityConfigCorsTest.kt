package com.sigep.security.infrastructure.config

import com.sigep.security.infrastructure.ratelimit.RateLimitFilter
import com.sigep.security.infrastructure.security.CustomAccessDeniedHandler
import com.sigep.security.infrastructure.security.CustomAuthenticationEntryPoint
import com.sigep.security.infrastructure.security.JwtAuthenticationFilter
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.cors.DefaultCorsProcessor

class SecurityConfigCorsTest {

    @Test
    fun `billing run preflight allows idempotency header`() {
        val securityConfig = SecurityConfig(
            jwtAuthenticationFilter = mockk<JwtAuthenticationFilter>(relaxed = true),
            rateLimitFilter = mockk<RateLimitFilter>(relaxed = true),
            customAuthenticationEntryPoint = mockk<CustomAuthenticationEntryPoint>(relaxed = true),
            customAccessDeniedHandler = mockk<CustomAccessDeniedHandler>(relaxed = true),
            allowedOrigins = "https://sigep.com.ar",
            allowedOriginPatterns = "https://*.vercel.app"
        )
        val request = MockHttpServletRequest(HttpMethod.OPTIONS.name(), "/api/v1/billing/runs").apply {
            addHeader(HttpHeaders.ORIGIN, "https://sigep.com.ar")
            addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
            addHeader(
                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                "authorization,content-type,idempotency-key"
            )
        }
        val response = MockHttpServletResponse()
        val configuration = requireNotNull(
            securityConfig.corsConfigurationSource().getCorsConfiguration(request)
        )

        val accepted = DefaultCorsProcessor().processRequest(configuration, request, response)

        assertTrue(accepted)
        assertEquals(200, response.status)
        assertTrue(
            response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
                ?.split(',')
                ?.any { it.trim().equals("Idempotency-Key", ignoreCase = true) } == true
        )
    }
}
