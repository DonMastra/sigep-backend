package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.AuthorizedVoucherKey
import com.sigep.payments.application.gateway.FiscalAuthorityHealth
import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import com.sigep.payments.application.gateway.FiscalAuthorizationResult
import com.sigep.payments.application.gateway.FiscalEnvironment
import com.sigep.payments.application.gateway.FiscalReferenceData
import com.sigep.payments.application.gateway.VoucherSequenceKey
import java.time.Clock
import java.time.LocalDateTime

class DisabledFiscalAuthorityAdapter(
    private val providerName: String,
    private val fiscalEnvironment: FiscalEnvironment,
    private val reason: String,
    private val clock: Clock = Clock.systemUTC()
) : FiscalAuthorityPort {

    override fun health() = FiscalAuthorityHealth(
        provider = providerName,
        environment = fiscalEnvironment,
        configured = false,
        available = false,
        checkedAt = LocalDateTime.now(clock),
        message = reason
    )

    override fun lastAuthorized(sequence: VoucherSequenceKey): Long = unavailable()

    override fun referenceData(): FiscalReferenceData = unavailable()

    override fun authorize(request: FiscalAuthorizationRequest): FiscalAuthorizationResult = unavailable()

    override fun consult(voucher: AuthorizedVoucherKey): FiscalAuthorizationResult? = unavailable()

    private fun <T> unavailable(): T = throw IllegalStateException(reason)
}
