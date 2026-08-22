package com.sigep.common.application.service

/**
 * Cross-module port used by security when a GUARDIAN account is created.
 * Implementations own only client-profile metadata; the user account remains
 * owned by the security module.
 */
interface GuardianClientProfileProvisioner {
    fun provisionGuardianClient(guardianUserId: Long, updatedBy: Long? = null)
}
