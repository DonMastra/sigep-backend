package com.sigep.staff.application.service

import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.UserRepository
import com.sigep.staff.domain.model.PaymentStatus
import com.sigep.staff.domain.model.TeachingStaff
import com.sigep.staff.infrastructure.repository.TeachingStaffRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class TeacherInfoProviderImplTest {
    private val teachingStaffRepository = mockk<TeachingStaffRepository>()
    private val userRepository = mockk<UserRepository>()
    private val provider = TeacherInfoProviderImpl(teachingStaffRepository, userRepository)

    @Test
    fun `resolves only active staff linked to eligible teaching accounts`() {
        val teacher = staff(1, 101, "Agustin", "Rosado")
        val guardian = staff(2, 102, "Nombre", "Tutor")

        every {
            teachingStaffRepository.findAllByLinkedUserIdInAndIsActiveTrue(listOf(101, 102))
        } returns listOf(teacher, guardian)
        every { userRepository.findAllById(listOf(101, 102)) } returns listOf(
            user(101, "arosado", UserRole.TEACHER),
            user(102, "guardian", UserRole.GUARDIAN)
        )

        val result = provider.getTeacherNamesByIds(listOf(101, 102))

        assertEquals(mapOf(101L to "Agustin Rosado"), result)
    }

    private fun staff(id: Long, linkedUserId: Long, firstName: String, lastName: String) = TeachingStaff(
        id = id,
        linkedUserId = linkedUserId,
        firstName = firstName,
        lastName = lastName,
        email = "$id@example.com",
        phoneNumber = "0000000000",
        documentNumber = "DOC-$id",
        birthDate = LocalDate.of(1990, 1, 1),
        address = "Test",
        hireDate = LocalDate.of(2026, 4, 1),
        monthlySalary = 0.0,
        paymentStatus = PaymentStatus.UP_TO_DATE,
        emergencyContactName = "",
        emergencyContactPhone = ""
    )

    private fun user(id: Long, username: String, role: UserRole) = User(
        id = id,
        username = username,
        email = "$username@example.com",
        password = "hash",
        firstName = username,
        lastName = "Test",
        role = role,
        status = AccountStatus.ACTIVE,
        active = true
    )
}
