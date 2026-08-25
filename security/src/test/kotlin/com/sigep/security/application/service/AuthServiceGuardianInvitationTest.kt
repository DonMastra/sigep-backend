package com.sigep.security.application.service

import com.sigep.security.application.dto.AcceptGuardianInvitationRequest
import com.sigep.security.application.dto.AdminCreateGuardianRequest
import com.sigep.security.application.dto.AdminGuardianActivationMode
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.GuardianInvitation
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.GuardianInvitationRepository
import com.sigep.security.domain.repository.RegistrationRequestRepository
import com.sigep.security.domain.repository.UserRepository
import com.sigep.security.infrastructure.security.JwtTokenProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthServiceGuardianInvitationTest {
    private val userRepository = mockk<UserRepository>()
    private val registrationRepository = mockk<RegistrationRequestRepository>()
    private val invitationRepository = mockk<GuardianInvitationRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtTokenProvider = mockk<JwtTokenProvider>()
    private val roleAssignmentService = mockk<UserRoleAssignmentService>()
    private lateinit var service: AuthService

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
        every { userRepository.existsByUsernameIgnoreCase(any()) } returns false
        every { userRepository.existsByEmailIgnoreCase(any()) } returns false
        every { passwordEncoder.encode(any()) } returns "encoded"
        every { roleAssignmentService.ensureAssignment(any(), UserRole.GUARDIAN, any()) } returns listOf(UserRole.GUARDIAN)
        every { roleAssignmentService.activeRoles(any<User>()) } returns listOf(UserRole.GUARDIAN)
    }

    @Test
    fun `admin invitation stores only a token hash and leaves guardian inactive`() {
        val invitationSlot = slot<GuardianInvitation>()
        every { userRepository.save(any()) } answers { firstArg<User>().copy(id = 25) }
        every { invitationRepository.save(capture(invitationSlot)) } answers { invitationSlot.captured }

        val result = service.createGuardianByAdmin(
            AdminCreateGuardianRequest(
                username = "guardian.invited",
                email = "guardian@example.com",
                firstName = "Ana",
                lastName = "Tutor",
                activationMode = AdminGuardianActivationMode.INVITE
            ),
            createdBy = 1
        )

        assertNotNull(result.invitationToken)
        assertEquals(64, invitationSlot.captured.tokenHash.length)
        assertFalse(invitationSlot.captured.tokenHash.contains(result.invitationToken!!))
        assertEquals(AccountStatus.PENDING_APPROVAL, result.user.status)
        assertFalse(result.user.active)
    }

    @Test
    fun `accepting invitation activates guardian and consumes it`() {
        val user = User(
            id = 25,
            username = "guardian.invited",
            email = "guardian@example.com",
            password = "temporary",
            firstName = "Ana",
            lastName = "Tutor",
            role = UserRole.GUARDIAN,
            status = AccountStatus.PENDING_APPROVAL,
            active = false
        )
        val invitation = GuardianInvitation(
            user = user,
            tokenHash = "a".repeat(64),
            expiresAt = LocalDateTime.now().plusHours(24),
            createdBy = 1
        )
        every { invitationRepository.findByTokenHash(any()) } returns Optional.of(invitation)
        every { userRepository.save(any()) } answers { firstArg() }
        every { invitationRepository.save(any()) } answers { firstArg() }

        val result = service.acceptGuardianInvitation(
            AcceptGuardianInvitationRequest("raw-token", "secure-password-123")
        )

        assertEquals(AccountStatus.ACTIVE, result.status)
        assertTrue(result.active)
        verify { invitationRepository.save(match { it.acceptedAt != null }) }
    }
}
