package com.sigep.security.application.service

import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ValidationException
import com.sigep.security.application.dto.LoginRequest
import com.sigep.security.application.dto.RefreshTokenRequest
import com.sigep.security.application.dto.RoleContextRequest
import com.sigep.security.application.dto.RoleSelectionRequest
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.model.UserRoleContextEventType
import com.sigep.security.domain.repository.GuardianInvitationRepository
import com.sigep.security.domain.repository.RegistrationRequestRepository
import com.sigep.security.domain.repository.UserRepository
import com.sigep.security.infrastructure.security.JwtTokenProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthServiceRoleContextTest {
    private val userRepository = mockk<UserRepository>()
    private val registrationRepository = mockk<RegistrationRequestRepository>()
    private val invitationRepository = mockk<GuardianInvitationRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtTokenProvider = mockk<JwtTokenProvider>()
    private val roleAssignmentService = mockk<UserRoleAssignmentService>()
    private lateinit var service: AuthService
    private val user = User(
        id = 42,
        username = "multi",
        email = "multi@sigep.test",
        password = "stored-hash",
        firstName = "Maria",
        lastName = "Prueba",
        role = UserRole.ADMIN,
        status = AccountStatus.ACTIVE,
        active = true
    )

    @BeforeEach
    fun setUp() {
        service = AuthService(
            userRepository,
            registrationRepository,
            invitationRepository,
            passwordEncoder,
            jwtTokenProvider,
            roleAssignmentService
        )
        every { userRepository.findByUsername("multi") } returns Optional.of(user)
        every { userRepository.findById(42) } returns Optional.of(user)
        every { roleAssignmentService.recordContext(any(), any(), any(), any()) } just Runs
    }

    @Test
    fun `multi-role login returns only a short-lived selection token`() {
        every { passwordEncoder.matches("valid-password", "stored-hash") } returns true
        every { roleAssignmentService.ensureLegacyAssignmentIfMissing(user) } returns
            listOf(UserRole.ADMIN, UserRole.TEACHER, UserRole.GUARDIAN)
        every { jwtTokenProvider.generateRoleSelectionToken(user) } returns "selection-token"

        val result = service.login(LoginRequest("multi", "valid-password"))

        assertTrue(result.roleSelectionRequired)
        assertEquals("selection-token", result.roleSelectionToken)
        assertEquals(listOf(UserRole.ADMIN, UserRole.TEACHER, UserRole.GUARDIAN), result.availableRoles)
        assertNull(result.token)
        assertNull(result.refreshToken)
        verify(exactly = 0) { jwtTokenProvider.generateToken(any<User>(), any<UserRole>()) }
    }

    @Test
    fun `initial selection issues tokens with one effective guardian authority`() {
        every { jwtTokenProvider.validateToken("selection-token") } returns true
        every { jwtTokenProvider.isRoleSelectionToken("selection-token") } returns true
        every { jwtTokenProvider.getUsernameFromToken("selection-token") } returns "multi"
        every { roleAssignmentService.activeRoles(user) } returns listOf(UserRole.ADMIN, UserRole.GUARDIAN)
        every { jwtTokenProvider.generateToken(user, UserRole.GUARDIAN) } returns "guardian-access"
        every { jwtTokenProvider.generateRefreshToken(user, UserRole.GUARDIAN) } returns "guardian-refresh"

        val result = service.selectRole(RoleSelectionRequest("selection-token", UserRole.GUARDIAN))

        assertEquals("guardian-access", result.token)
        assertEquals(UserRole.GUARDIAN, result.user?.activeRole)
        assertEquals(UserRole.GUARDIAN, result.user?.role)
        assertEquals(listOf(UserRole.ADMIN, UserRole.GUARDIAN), result.user?.roles)
        verify {
            roleAssignmentService.recordContext(
                42,
                null,
                UserRole.GUARDIAN,
                UserRoleContextEventType.LOGIN_SELECTION
            )
        }
    }

    @Test
    fun `elevation to administration rejects an invalid current password`() {
        every { roleAssignmentService.activeRoles(user) } returns listOf(UserRole.ADMIN, UserRole.GUARDIAN)
        every { passwordEncoder.matches("wrong-password", "stored-hash") } returns false

        val error = assertFailsWith<ValidationException> {
            service.switchRole(
                42,
                UserRole.GUARDIAN.name,
                RoleContextRequest(UserRole.ADMIN, "wrong-password")
            )
        }

        assertEquals("ADMIN_ROLE_REAUTHENTICATION_FAILED", error.code)
        verify(exactly = 0) { jwtTokenProvider.generateToken(any<User>(), any<UserRole>()) }
    }

    @Test
    fun `refresh rejects a role assignment revoked after the token was issued`() {
        every { jwtTokenProvider.validateToken("guardian-refresh") } returns true
        every { jwtTokenProvider.isRefreshToken("guardian-refresh") } returns true
        every { jwtTokenProvider.getUsernameFromToken("guardian-refresh") } returns "multi"
        every { jwtTokenProvider.getRoleFromTokenOrNull("guardian-refresh") } returns UserRole.GUARDIAN.name
        every { roleAssignmentService.activeRoles(user) } returns listOf(UserRole.ADMIN)

        val error = assertFailsWith<ForbiddenException> {
            service.refreshToken(RefreshTokenRequest("guardian-refresh"))
        }

        assertEquals("ROLE_NOT_ASSIGNED", error.code)
    }
}
