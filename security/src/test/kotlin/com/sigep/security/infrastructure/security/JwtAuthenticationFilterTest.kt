package com.sigep.security.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwtAuthenticationFilterTest {
    private val tokenProvider = mockk<JwtTokenProvider>()
    private val filter = JwtAuthenticationFilter(tokenProvider, ObjectMapper().findAndRegisterModules())

    @Test
    fun `mandatory password change blocks other protected endpoints`() {
        configureForcedPasswordToken()
        val request = authenticatedRequest("GET", "/api/v1/students")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(403, response.status)
        assertTrue(response.contentAsString.contains("PASSWORD_CHANGE_REQUIRED"))
    }

    @Test
    fun `mandatory password change allows the password endpoint`() {
        configureForcedPasswordToken()
        val request = authenticatedRequest("PATCH", "/api/v1/users/me/password")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(200, response.status)
        assertEquals(7L, request.getAttribute("userId"))
    }

    private fun configureForcedPasswordToken() {
        every { tokenProvider.validateToken("token") } returns true
        every { tokenProvider.getUsernameFromToken("token") } returns "rmainero"
        every { tokenProvider.getRoleFromToken("token") } returns "ADMIN"
        every { tokenProvider.getUserIdFromToken("token") } returns 7
        every { tokenProvider.getMustChangePasswordFromToken("token") } returns true
    }

    private fun authenticatedRequest(method: String, path: String) =
        MockHttpServletRequest(method, path).apply {
            addHeader("Authorization", "Bearer token")
        }
}
