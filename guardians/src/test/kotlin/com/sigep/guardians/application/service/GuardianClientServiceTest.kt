package com.sigep.guardians.application.service

import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.guardians.application.dto.UpdateGuardianClientProfileRequest
import com.sigep.guardians.domain.model.GuardianClientProfile
import com.sigep.guardians.domain.model.GuardianClientSearchCriteria
import com.sigep.guardians.domain.model.GuardianClientSummaryReadModel
import com.sigep.guardians.domain.model.GuardianContactChannel
import com.sigep.guardians.domain.repository.GuardianClientProfileRepository
import com.sigep.guardians.domain.repository.GuardianClientReadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.util.Optional

class GuardianClientServiceTest {
    private val profileRepository = mockk<GuardianClientProfileRepository>()
    private val readRepository = mockk<GuardianClientReadRepository>()
    private val service = GuardianClientService(profileRepository, readRepository)

    @Test
    fun `lists the cross-domain summary with standard pagination`() {
        val criteria = GuardianClientSearchCriteria(page = 0, size = 20)
        every { readRepository.search(criteria) } returns PageImpl(listOf(summary()), PageRequest.of(0, 20), 1)

        val result = service.list(criteria)

        assertEquals(1, result.totalElements)
        assertEquals("CLI-000000000010", result.content.single().clientNumber)
        assertEquals(BigDecimal("1500.00"), result.content.single().outstandingAmount)
    }

    @Test
    fun `rejects stale profile updates before writing`() {
        val profile = GuardianClientProfile(guardianUserId = 10, clientNumber = "CLI-000000000010", version = 3)
        every { readRepository.existsGuardian(10) } returns true
        every { profileRepository.insertIfMissing(10, "CLI-000000000010", 1) } returns 0
        every { profileRepository.findById(10) } returns Optional.of(profile)

        val error = assertThrows(ResourceConflictException::class.java) {
            service.updateProfile(
                guardianUserId = 10,
                request = UpdateGuardianClientProfileRequest(GuardianContactChannel.PHONE, "nota", version = 2),
                updatedBy = 1
            )
        }

        assertEquals("GUARDIAN_CLIENT_VERSION_CONFLICT", error.code)
        verify(exactly = 0) { profileRepository.saveAndFlush(any()) }
    }

    @Test
    fun `provisions a deterministic one-to-one profile`() {
        every { profileRepository.insertIfMissing(42, "CLI-000000000042", 1) } returns 1

        service.provisionGuardianClient(42, 1)

        verify {
            profileRepository.insertIfMissing(42, "CLI-000000000042", 1)
        }
    }

    private fun summary() = GuardianClientSummaryReadModel(
        guardianUserId = 10,
        clientNumber = "CLI-000000000010",
        firstName = "Lucia",
        lastName = "Tutor",
        email = "lucia@example.test",
        phoneNumber = null,
        documentNumber = null,
        accountStatus = "PENDING_APPROVAL",
        accountActive = false,
        preferredContactChannel = "EMAIL",
        studentCount = 1,
        activeStudentCount = 1,
        activeEnrollmentCount = 1,
        tuitionApplicationCount = 1,
        billingAccountId = 5,
        billingAccountStatus = "ACTIVE",
        billingProfileStatus = "READY",
        openChargeCount = 1,
        overdueChargeCount = 0,
        outstandingAmount = BigDecimal("1500.00"),
        lastPaymentDate = null,
        missingContactData = true,
        profileVersion = 0
    )
}
