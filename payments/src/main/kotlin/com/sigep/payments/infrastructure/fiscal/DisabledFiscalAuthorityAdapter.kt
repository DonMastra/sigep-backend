package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.AuthorizedVoucherKey
import com.sigep.payments.application.gateway.FiscalAuthorityHealth
import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import com.sigep.payments.application.gateway.FiscalAuthorizationResult
import com.sigep.payments.application.gateway.FiscalCatalogEntry
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

    override fun referenceData() = FiscalReferenceData(
        voucherTypes = listOf(
            FiscalCatalogEntry("1", "Factura A"),
            FiscalCatalogEntry("6", "Factura B"),
            FiscalCatalogEntry("11", "Factura C")
        ),
        documentTypes = listOf(
            FiscalCatalogEntry("80", "CUIT"),
            FiscalCatalogEntry("86", "CUIL"),
            FiscalCatalogEntry("96", "DNI"),
            FiscalCatalogEntry("99", "Consumidor Final")
        ),
        receiverVatConditions = listOf(
            FiscalCatalogEntry("1", "IVA Responsable Inscripto"),
            FiscalCatalogEntry("4", "IVA Sujeto Exento"),
            FiscalCatalogEntry("5", "Consumidor Final"),
            FiscalCatalogEntry("6", "Responsable Monotributo"),
            FiscalCatalogEntry("13", "Monotributista Social"),
            FiscalCatalogEntry("16", "Monotributo Trabajador Independiente Promovido")
        ),
        currencies = listOf(FiscalCatalogEntry("PES", "Pesos Argentinos")),
        retrievedAt = LocalDateTime.now(clock)
    )

    override fun authorize(request: FiscalAuthorizationRequest): FiscalAuthorizationResult = unavailable()

    override fun consult(voucher: AuthorizedVoucherKey): FiscalAuthorizationResult? = unavailable()

    private fun <T> unavailable(): T = throw IllegalStateException(reason)
}
