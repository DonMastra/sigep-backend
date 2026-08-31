package com.sigep.security.application.service

import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.service.GuardianClientAccountUpdateCommand
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.time.LocalDate
import java.util.Optional

class GuardianClientAccountUpdaterImplTest {
    private val userRepository = mockk<UserRepository>()
    private val roleAssignmentService = mockk<UserRoleAssignmentService>()
    private val service = GuardianClientAccountUpdaterImpl(userRepository, roleAssignmentService)

    @Test
    fun `updates an assigned guardian account without changing roles or access`() {
        val saved = slot<User>()
        every { userRepository.findById(10) } returns Optional.of(user())
        every { roleAssignmentService.isRoleActive(10, UserRole.GUARDIAN) } returns true
        every { userRepository.existsByEmailIgnoreCase("nueva@example.test") } returns false
        every { userRepository.saveAndFlush(capture(saved)) } answers { saved.captured }

        service.updateGuardianClientAccount(command())

        assertEquals("Lucia", saved.captured.firstName)
        assertEquals("Tutor", saved.captured.lastName)
        assertEquals("nueva@example.test", saved.captured.email)
        assertEquals("111", saved.captured.phoneNumber)
        assertNull(saved.captured.address)
        assertEquals(UserRole.ADMIN, saved.captured.role)
        assertEquals(AccountStatus.PENDING_APPROVAL, saved.captured.status)
        assertEquals(false, saved.captured.active)
    }

    @Test
    fun `rejects stale account data before writing`() {
        every { userRepository.findById(10) } returns Optional.of(user(version = 6))
        every { roleAssignmentService.isRoleActive(10, UserRole.GUARDIAN) } returns true

        val error = assertThrows(ResourceConflictException::class.java) {
            service.updateGuardianClientAccount(command(version = 5))
        }

        assertEquals("GUARDIAN_CLIENT_ACCOUNT_VERSION_CONFLICT", error.code)
        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    @Test
    fun `rejects an email already used by another account`() {
        every { userRepository.findById(10) } returns Optional.of(user())
        every { roleAssignmentService.isRoleActive(10, UserRole.GUARDIAN) } returns true
        every { userRepository.existsByEmailIgnoreCase("nueva@example.test") } returns true

        val error = assertThrows(ResourceConflictException::class.java) {
            service.updateGuardianClientAccount(command())
        }

        assertEquals("GUARDIAN_EMAIL_ALREADY_EXISTS", error.code)
        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    @Test
    fun `maps a database optimistic locking race to the account conflict contract`() {
        every { userRepository.findById(10) } returns Optional.of(user())
        every { roleAssignmentService.isRoleActive(10, UserRole.GUARDIAN) } returns true
        every { userRepository.existsByEmailIgnoreCase("nueva@example.test") } returns false
        every { userRepository.saveAndFlush(any()) } throws ObjectOptimisticLockingFailureException(User::class.java, 10)

        val error = assertThrows(ResourceConflictException::class.java) {
            service.updateGuardianClientAccount(command())
        }

        assertEquals("GUARDIAN_CLIENT_ACCOUNT_VERSION_CONFLICT", error.code)
    }

    @Test
    fun `does not expose an account without an active guardian role`() {
        every { userRepository.findById(10) } returns Optional.of(user())
        every { roleAssignmentService.isRoleActive(10, UserRole.GUARDIAN) } returns false

        assertThrows(ResourceNotFoundException::class.java) {
            service.updateGuardianClientAccount(command())
        }

        verify(exactly = 0) { userRepository.saveAndFlush(any()) }
    }

    private fun user(version: Long = 4) = User(
        id = 10,
        username = "lucia",
        email = "lucia@example.test",
        password = "hash",
        firstName = "Anterior",
        lastName = "Nombre",
        role = UserRole.ADMIN,
        status = AccountStatus.PENDING_APPROVAL,
        active = false,
        version = version
    )

    private fun command(version: Long = 4) = GuardianClientAccountUpdateCommand(
        guardianUserId = 10,
        firstName = " Lucia ",
        lastName = " Tutor ",
        email = "NUEVA@EXAMPLE.TEST",
        phoneNumber = " 111 ",
        address = " ",
        dateOfBirth = LocalDate.of(1985, 3, 20),
        documentNumber = " 123 ",
        emergencyContact = " Ana ",
        version = version,
        updatedBy = 1
    )
}
