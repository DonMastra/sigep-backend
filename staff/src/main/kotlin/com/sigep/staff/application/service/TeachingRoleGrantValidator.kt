package com.sigep.staff.application.service

import com.sigep.common.application.exception.ValidationException
import com.sigep.common.application.service.UserRoleGrantValidator
import com.sigep.staff.infrastructure.repository.TeachingStaffRepository
import org.springframework.stereotype.Component

@Component
class TeachingRoleGrantValidator(
    private val teachingStaffRepository: TeachingStaffRepository
) : UserRoleGrantValidator {
    override fun validateGrant(userId: Long, role: String) {
        if (role != "TEACHER") return
        val staff = teachingStaffRepository.findByLinkedUserId(userId)
        if (staff == null || !staff.isActive) {
            throw ValidationException(
                message = "TEACHER role requires an active teaching staff relationship",
                code = "TEACHER_STAFF_LINK_REQUIRED"
            )
        }
    }
}
