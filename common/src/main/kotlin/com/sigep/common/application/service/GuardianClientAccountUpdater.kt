package com.sigep.common.application.service

import java.time.LocalDate

data class GuardianClientAccountUpdateCommand(
    val guardianUserId: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phoneNumber: String?,
    val address: String?,
    val dateOfBirth: LocalDate?,
    val documentNumber: String?,
    val emergencyContact: String?,
    val version: Long,
    val updatedBy: Long
)

interface GuardianClientAccountUpdater {
    fun updateGuardianClientAccount(command: GuardianClientAccountUpdateCommand)
}
