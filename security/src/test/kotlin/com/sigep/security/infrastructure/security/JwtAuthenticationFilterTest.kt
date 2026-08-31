package com.sigep.security.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.sigep.security.application.service.UserRoleAssignmentService
import com.sigep.security.domain.model.UserRole
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwtAuthenticationFilterTest {
    private val tokenProvider = mockk<JwtTokenProvider>()
    private val roleAssignmentService = mockk<UserRoleAssignmentService>()
    private val filter = JwtAuthenticationFilter(
        tokenProvider,
        ObjectMapper().findAndRegisterModules(),
        roleAssignmentService
    )

    @BeforeEach
    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

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

    @Test
    fun `a token stops authenticating immediately after its active role is revoked`() {
        configureForcedPasswordToken(mustChangePassword = false, roleActive = false)
        val request = authenticatedRequest("GET", "/api/v1/students")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(null, request.getAttribute("userId"))
        assertEquals(null, SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `an account with admin assigned but guardian active receives no admin authority`() {
        configureForcedPasswordToken(
            mustChangePassword = false,
            role = UserRole.GUARDIAN
        )
        val request = authenticatedRequest("GET", "/api/v1/admin/users")

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        val authorities = SecurityContextHolder.getContext().authentication.authorities.map { it.authority }
        assertEquals(listOf("ROLE_GUARDIAN"), authorities)
        assertTrue("ROLE_ADMIN" !in authorities)
    }

    private fun configureForcedPasswordToken(
        mustChangePassword: Boolean = true,
        roleActive: Boolean = true,
        role: UserRole = UserRole.ADMIN
    ) {
        every { tokenProvider.validateToken("token") } returns true
        every { tokenProvider.isAccessToken("token") } returns true
        every { tokenProvider.getUsernameFromToken("token") } returns "rmainero"
        every { tokenProvider.getRoleFromToken("token") } returns role.name
        every { tokenProvider.getUserIdFromToken("token") } returns 7
        every { tokenProvider.getMustChangePasswordFromToken("token") } returns mustChangePassword
        every { roleAssignmentService.isRoleUsableForSession(7, role) } returns roleActive
    }

    private fun authenticatedRequest(method: String, path: String) =
        MockHttpServletRequest(method, path).apply {
            addHeader("Authorization", "Bearer token")
        }
}
