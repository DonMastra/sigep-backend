package com.sigep.common.application.service

/**
 * Cross-module validation hook for role assignments that require a domain relationship.
 * Implementations must ignore roles they do not own.
 */
fun interface UserRoleGrantValidator {
    fun validateGrant(userId: Long, role: String)
}
