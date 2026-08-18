package com.sigep.security.application.service

import com.sigep.common.application.exception.ValidationException
import com.sigep.security.application.dto.ChangePasswordRequest
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.GuardianInvitationRepository
import com.sigep.security.domain.repository.RegistrationRequestRepository
import com.sigep.security.domain.repository.UserRepository
import com.sigep.security.infrastructure.security.JwtTokenProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AuthServicePasswordChangeTest {
    private val userRepository = mockk<UserRepository>()
    private val registrationRepository = mockk<RegistrationRequestRepository>()
    private val invitationRepository = mockk<GuardianInvitationRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtTokenProvider = mockk<JwtTokenProvider>()
    private lateinit var service: AuthService

    @BeforeEach
    fun setUp() {
        service = AuthService(userRepository, registrationRepository, invitationRepository, passwordEncoder, jwtTokenProvider)
    }

    @Test
    fun `changing a temporary password clears the mandatory change flag`() {
        val user = adminUser()
        every { userRepository.findById(7) } returns Optional.of(user)
        every { passwordEncoder.matches("temporary-password", "stored-hash") } returns true
        every { passwordEncoder.matches("new-secure-password", "stored-hash") } returns false
        every { passwordEncoder.encode("new-secure-password") } returns "new-hash"
        every { userRepository.save(any()) } answers { firstArg() }

        val result = service.changePassword(
            7,
            ChangePasswordRequest("temporary-password", "new-secure-password")
        )

        assertFalse(result.mustChangePassword)
        verify {
            userRepository.save(match {
                it.password == "new-hash" && !it.mustChangePassword && it.passwordChangedAt != null
            })
        }
    }

    @Test
    fun `changing a password rejects an invalid current password`() {
        every { userRepository.findById(7) } returns Optional.of(adminUser())
        every { passwordEncoder.matches("wrong-password", "stored-hash") } returns false

        val error = assertFailsWith<ValidationException> {
            service.changePassword(7, ChangePasswordRequest("wrong-password", "new-secure-password"))
        }

        assertEquals("CURRENT_PASSWORD_INVALID", error.code)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `changing a password rejects reuse of the current password`() {
        every { userRepository.findById(7) } returns Optional.of(adminUser())
        every { passwordEncoder.matches("temporary-password", "stored-hash") } returns true

        val error = assertFailsWith<ValidationException> {
            service.changePassword(7, ChangePasswordRequest("temporary-password", "temporary-password"))
        }

        assertEquals("PASSWORD_UNCHANGED", error.code)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    private fun adminUser() = User(
        id = 7,
        username = "rmainero",
        email = "regina@example.com",
        password = "stored-hash",
        firstName = "Regina",
        lastName = "Mainero",
        role = UserRole.ADMIN,
        status = AccountStatus.ACTIVE,
        active = true,
        mustChangePassword = true
    )
}
