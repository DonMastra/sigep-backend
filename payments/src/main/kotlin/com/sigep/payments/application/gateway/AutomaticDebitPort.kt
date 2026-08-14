package com.sigep.payments.application.gateway

import com.sigep.payments.domain.model.AutomaticDebitProvider

interface AutomaticDebitPort {
    val provider: AutomaticDebitProvider?
    val simulated: Boolean

    fun authorize(command: AutomaticDebitAuthorizationCommand): AutomaticDebitAuthorizationResult
}

data class AutomaticDebitAuthorizationCommand(
    val accountId: Long,
    val maskedLabel: String,
    val consentVersion: String
)

data class AutomaticDebitAuthorizationResult(
    val providerReference: String
)
