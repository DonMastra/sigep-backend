package com.sigep.common.application.service

interface GuardianAccountProvider {
    fun getGuardianAccount(userId: Long): GuardianAccountInfo?
    fun activateGuardianForTuition(userId: Long, reviewedBy: Long, adminNotes: String?): GuardianAccountInfo
}

data class GuardianAccountInfo(
    val id: Long,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val status: String,
    val active: Boolean
)
