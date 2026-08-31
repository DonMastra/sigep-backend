package com.sigep.security.application.service

import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.common.application.service.GuardianClientAccountUpdateCommand
import com.sigep.common.application.service.GuardianClientAccountUpdater
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional
class GuardianClientAccountUpdaterImpl(
    private val userRepository: UserRepository,
    private val roleAssignmentService: UserRoleAssignmentService
) : GuardianClientAccountUpdater {
    private val logger = LoggerFactory.getLogger(GuardianClientAccountUpdaterImpl::class.java)

    override fun updateGuardianClientAccount(command: GuardianClientAccountUpdateCommand) {
        val user = userRepository.findById(command.guardianUserId).orElse(null)
        if (user == null || !roleAssignmentService.isRoleActive(command.guardianUserId, UserRole.GUARDIAN)) {
            throw ResourceNotFoundException("Guardian client not found with id: ${command.guardianUserId}")
        }
        if (user.version != command.version) {
            throw ResourceConflictException(
                message = "Guardian client account was modified by another user",
                code = "GUARDIAN_CLIENT_ACCOUNT_VERSION_CONFLICT",
                field = "version"
            )
        }

        val firstName = command.firstName.trim()
        val lastName = command.lastName.trim()
        val email = command.email.trim().lowercase()
        val dateOfBirth = command.dateOfBirth
        if (firstName.isEmpty()) throw ValidationException("First name is required", field = "firstName")
        if (lastName.isEmpty()) throw ValidationException("Last name is required", field = "lastName")
        if (email.isEmpty()) throw ValidationException("Email is required", field = "email")
        if (dateOfBirth != null && !dateOfBirth.isBefore(LocalDate.now())) {
            throw ValidationException("Date of birth must be in the past", field = "dateOfBirth")
        }
        if (!email.equals(user.email, ignoreCase = true) && userRepository.existsByEmailIgnoreCase(email)) {
            throw ResourceConflictException(
                message = "Email is already registered",
                code = "GUARDIAN_EMAIL_ALREADY_EXISTS",
                field = "email"
            )
        }

        try {
            userRepository.saveAndFlush(
                user.copy(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    phoneNumber = command.phoneNumber.clean(),
                    address = command.address.clean(),
                    dateOfBirth = dateOfBirth,
                    documentNumber = command.documentNumber.clean(),
                    emergencyContact = command.emergencyContact.clean(),
                    updatedAt = LocalDateTime.now()
                )
            )
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw ResourceConflictException(
                message = "Guardian client account was modified by another user",
                code = "GUARDIAN_CLIENT_ACCOUNT_VERSION_CONFLICT",
                field = "version"
            )
        } catch (_: DataIntegrityViolationException) {
            throw ResourceConflictException(
                message = "Email is already registered",
                code = "GUARDIAN_EMAIL_ALREADY_EXISTS",
                field = "email"
            )
        }
        logger.info(
            "Guardian client account id {} updated by admin id {}",
            command.guardianUserId,
            command.updatedBy
        )
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
