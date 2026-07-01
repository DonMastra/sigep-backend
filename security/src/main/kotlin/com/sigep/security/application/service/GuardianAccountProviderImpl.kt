package com.sigep.security.application.service

import com.sigep.common.application.exception.ForbiddenException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.service.GuardianAccountInfo
import com.sigep.common.application.service.GuardianAccountProvider
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.RegistrationRequestRepository
import com.sigep.security.domain.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class GuardianAccountProviderImpl(
    private val userRepository: UserRepository,
    private val registrationRequestRepository: RegistrationRequestRepository
) : GuardianAccountProvider {

    override fun getGuardianAccount(userId: Long): GuardianAccountInfo? =
        userRepository.findById(userId)
            .filter { it.role == UserRole.GUARDIAN }
            .map { it.toInfo() }
            .orElse(null)

    override fun activateGuardianForTuition(
        userId: Long,
        reviewedBy: Long,
        adminNotes: String?
    ): GuardianAccountInfo {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("Guardian user not found with id: $userId") }

        if (user.role != UserRole.GUARDIAN) {
            throw ForbiddenException("Only GUARDIAN accounts can be activated by tuition")
        }

        val now = LocalDateTime.now()
        val savedUser = userRepository.save(
            user.copy(
                status = AccountStatus.ACTIVE,
                active = true,
                updatedAt = now
            )
        )

        registrationRequestRepository.findByUserId(userId).ifPresent { request ->
            registrationRequestRepository.save(
                request.copy(
                    status = AccountStatus.ACTIVE,
                    reviewedAt = now,
                    reviewedBy = reviewedBy,
                    adminNotes = adminNotes ?: request.adminNotes
                )
            )
        }

        return savedUser.toInfo()
    }

    private fun User.toInfo() = GuardianAccountInfo(
        id = id!!,
        username = username,
        email = email,
        firstName = firstName,
        lastName = lastName,
        status = status.name,
        active = active
    )
}
