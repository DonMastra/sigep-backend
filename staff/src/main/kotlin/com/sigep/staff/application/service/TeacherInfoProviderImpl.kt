package com.sigep.staff.application.service

import com.sigep.common.application.service.TeacherInfoProvider
import com.sigep.security.domain.model.AccountStatus
import com.sigep.security.domain.model.UserRole
import com.sigep.security.domain.repository.UserRepository
import com.sigep.staff.infrastructure.repository.TeachingStaffRepository
import org.springframework.stereotype.Service

@Service
class TeacherInfoProviderImpl(
    private val teachingStaffRepository: TeachingStaffRepository,
    private val userRepository: UserRepository
) : TeacherInfoProvider {

    override fun getTeacherNamesByIds(teacherIds: Collection<Long>): Map<Long, String> {
        if (teacherIds.isEmpty()) {
            return emptyMap()
        }

        val activeStaff = teachingStaffRepository.findAllByLinkedUserIdInAndIsActiveTrue(teacherIds)
        val eligibleUserIds = userRepository.findAllById(activeStaff.mapNotNull { it.linkedUserId }.distinct())
            .filter { user ->
                user.role in setOf(UserRole.TEACHER, UserRole.ADMIN) &&
                    user.status == AccountStatus.ACTIVE &&
                    user.active
            }
            .mapNotNull { it.id }
            .toSet()

        return activeStaff
            .mapNotNull { staff ->
                staff.linkedUserId
                    ?.takeIf(eligibleUserIds::contains)
                    ?.let { it to staff.fullName }
            }
            .toMap()
    }

    override fun getTeacherNameById(teacherId: Long): String? =
        getTeacherNamesByIds(listOf(teacherId))[teacherId]
}

