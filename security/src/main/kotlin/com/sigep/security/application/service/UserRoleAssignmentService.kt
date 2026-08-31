package com.sigep.security.application.service

import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.common.application.service.UserRoleGrantValidator
import com.sigep.common.application.service.UserRoleMembershipProvider
import com.sigep.security.domain.model.User
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.model.UserRoleAssignment
import com.sigep.security.domain.model.UserRoleContextEvent
import com.sigep.security.domain.model.UserRoleContextEventType
import com.sigep.security.domain.repository.UserRepository
import com.sigep.security.domain.repository.UserRoleAssignmentRepository
import com.sigep.security.domain.repository.UserRoleContextEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class UserRoleAssignmentService(
    private val userRepository: UserRepository,
    private val assignmentRepository: UserRoleAssignmentRepository,
    private val contextEventRepository: UserRoleContextEventRepository,
    private val grantValidators: List<UserRoleGrantValidator> = emptyList()
) : UserRoleMembershipProvider {
    private val logger = LoggerFactory.getLogger(UserRoleAssignmentService::class.java)

    @Transactional(readOnly = true)
    fun activeRoles(user: User): List<UserRole> {
        val userId = user.id ?: throw ValidationException("User must be persisted before reading roles")
        val persisted = assignmentRepository
            .findAllByUserIdAndRevokedAtIsNullOrderByRoleAsc(userId)
            .map { it.role }
            .distinct()
        if (persisted.isNotEmpty()) return persisted
        return if (assignmentRepository.existsByUserId(userId)) emptyList() else listOf(user.role)
    }

    @Transactional(readOnly = true)
    fun activeRoles(userId: Long): List<UserRole> = activeRoles(requireUser(userId))

    @Transactional(readOnly = true)
    fun activeUserIdsByRole(role: UserRole): List<Long> = assignmentRepository.findActiveUserIdsByRole(role)

    @Transactional(readOnly = true)
    fun assignedUserIds(): List<Long> = assignmentRepository.findAssignedUserIds()

    @Transactional(readOnly = true)
    fun isRoleActive(userId: Long, role: UserRole): Boolean {
        if (assignmentRepository.existsByUserId(userId)) {
            return assignmentRepository.existsByUserIdAndRoleAndRevokedAtIsNull(userId, role)
        }
        return userRepository.findById(userId).map { it.role == role }.orElse(false)
    }

    @Transactional(readOnly = true)
    fun isRoleUsableForSession(userId: Long, role: UserRole): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        if (!user.active || user.status != AccountStatus.ACTIVE) return false
        return if (assignmentRepository.existsByUserId(userId)) {
            assignmentRepository.existsByUserIdAndRoleAndRevokedAtIsNull(userId, role)
        } else {
            user.role == role
        }
    }

    override fun hasActiveRole(userId: Long, role: String): Boolean =
        runCatching { UserRole.valueOf(role) }
            .map { isRoleActive(userId, it) }
            .getOrDefault(false)

    fun ensureAssignment(user: User, role: UserRole, assignedBy: Long? = null): List<UserRole> {
        val userId = user.id ?: throw ValidationException("User must be persisted before assigning roles")
        saveOrReactivate(userId, role, assignedBy)
        return activeRoles(user)
    }

    fun ensureLegacyAssignmentIfMissing(user: User): List<UserRole> {
        val userId = user.id ?: throw ValidationException("User must be persisted before assigning roles")
        if (!assignmentRepository.existsByUserId(userId)) {
            saveOrReactivate(userId, user.role, assignedBy = null)
        }
        return activeRoles(user)
    }

    fun grantRole(userId: Long, role: UserRole, assignedBy: Long): List<UserRole> {
        val user = requireUser(userId)
        grantValidators.forEach { it.validateGrant(userId, role.name) }
        saveOrReactivate(userId, role, assignedBy)
        logger.info("Role {} granted to user id {} by admin id {}", role, userId, assignedBy)
        return activeRoles(user)
    }

    fun revokeRole(userId: Long, role: UserRole, revokedBy: Long): List<UserRole> {
        val user = requireUser(userId)
        val assignment = assignmentRepository.findByUserIdAndRole(userId, role).orElse(null)
            ?: throw ValidationException("Role is not assigned to the user", code = "ROLE_NOT_ASSIGNED")
        if (!assignment.isActive) {
            throw ValidationException("Role is already revoked", code = "ROLE_ALREADY_REVOKED")
        }
        if (assignmentRepository.countByUserIdAndRevokedAtIsNull(userId) <= 1) {
            throw ValidationException("A user must retain at least one active role", code = "LAST_ROLE_CANNOT_BE_REVOKED")
        }
        assignmentRepository.save(
            assignment.copy(
                revokedAt = LocalDateTime.now(),
                revokedBy = revokedBy
            )
        )
        logger.info("Role {} revoked from user id {} by admin id {}", role, userId, revokedBy)
        return activeRoles(user)
    }

    fun recordContext(
        userId: Long,
        previousRole: UserRole?,
        activeRole: UserRole,
        eventType: UserRoleContextEventType
    ) {
        contextEventRepository.save(
            UserRoleContextEvent(
                userId = userId,
                previousRole = previousRole,
                activeRole = activeRole,
                eventType = eventType
            )
        )
        logger.info(
            "Role context {} for user id {} from {} to {}",
            eventType,
            userId,
            previousRole,
            activeRole
        )
    }

    private fun saveOrReactivate(userId: Long, role: UserRole, assignedBy: Long?) {
        val now = LocalDateTime.now()
        val current = assignmentRepository.findByUserIdAndRole(userId, role).orElse(null)
        if (current?.isActive == true) return

        assignmentRepository.save(
            current?.copy(
                assignedAt = now,
                assignedBy = assignedBy,
                revokedAt = null,
                revokedBy = null
            ) ?: UserRoleAssignment(
                userId = userId,
                role = role,
                assignedAt = now,
                assignedBy = assignedBy
            )
        )
    }

    private fun requireUser(userId: Long): User = userRepository.findById(userId)
        .orElseThrow { ResourceNotFoundException("User not found with id: $userId") }
}
