package com.sigep.security.presentation.controller

import com.sigep.common.application.exception.ForbiddenException
import com.sigep.security.application.dto.RegisterRequest
import com.sigep.security.application.service.AuthService
import com.sigep.security.domain.model.UserRole
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class AuthControllerTest {
    private val authService = mockk<AuthService>(relaxed = true)

    @Test
    fun `rejects public registration when disabled for the environment`() {
        val controller = AuthController(authService, publicRegistrationEnabled = false)

        val exception = assertThrows<ForbiddenException> {
            controller.register(
                RegisterRequest(
                    username = "training.guardian",
                    email = "training@example.invalid",
                    password = "temporary-password",
                    firstName = "Training",
                    lastName = "Guardian",
                    role = UserRole.GUARDIAN
                )
            )
        }

        assertEquals("PUBLIC_REGISTRATION_DISABLED", exception.code)
        verify(exactly = 0) { authService.register(any()) }
    }
}
