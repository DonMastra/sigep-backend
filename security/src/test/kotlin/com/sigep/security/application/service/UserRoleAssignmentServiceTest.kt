package com.sigep.security.application.service

import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.UserRepository
import com.sigep.security.domain.repository.UserRoleAssignmentRepository
import com.sigep.security.domain.repository.UserRoleContextEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals

class UserRoleAssignmentServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val assignmentRepository = mockk<UserRoleAssignmentRepository>()
    private val contextEventRepository = mockk<UserRoleContextEventRepository>()
    private val service = UserRoleAssignmentService(
        userRepository,
        assignmentRepository,
        contextEventRepository
    )
    private val legacyAdmin = User(
        id = 9,
        username = "legacy",
        email = "legacy@sigep.test",
        password = "hash",
        firstName = "Legacy",
        lastName = "User",
        role = UserRole.ADMIN,
        status = AccountStatus.ACTIVE,
        active = true
    )

    @Test
    fun `legacy role is used only before the first assignment is created`() {
        every { assignmentRepository.findAllByUserIdAndRevokedAtIsNullOrderByRoleAsc(9) } returns emptyList()
        every { assignmentRepository.existsByUserId(9) } returns false

        assertEquals(listOf(UserRole.ADMIN), service.activeRoles(legacyAdmin))
    }

    @Test
    fun `revoking every persisted assignment never resurrects the legacy role`() {
        every { assignmentRepository.existsByUserId(9) } returns true
        every { assignmentRepository.findAllByUserIdAndRevokedAtIsNullOrderByRoleAsc(9) } returns emptyList()

        assertEquals(emptyList(), service.ensureLegacyAssignmentIfMissing(legacyAdmin))
        verify(exactly = 0) { assignmentRepository.save(any()) }
    }

    @Test
    fun `an inactive account cannot keep using an otherwise assigned role`() {
        every { userRepository.findById(9) } returns Optional.of(legacyAdmin.copy(active = false))

        assertEquals(false, service.isRoleUsableForSession(9, UserRole.ADMIN))
        verify(exactly = 0) { assignmentRepository.existsByUserId(any()) }
    }
}
