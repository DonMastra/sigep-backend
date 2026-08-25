package com.sigep.common.application.service

interface UserRoleMembershipProvider {
    fun hasActiveRole(userId: Long, role: String): Boolean
}
