package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.AuthorizedVoucherKey
import com.sigep.payments.application.gateway.FiscalAuthorityHealth
import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import com.sigep.payments.application.gateway.FiscalAuthorizationResult
import com.sigep.payments.application.gateway.FiscalReferenceData
import com.sigep.payments.application.gateway.VoucherSequenceKey
import com.sigep.payments.domain.service.FiscalAuthorizationValidator
import java.time.Clock
import java.time.LocalDateTime
import java.nio.charset.StandardCharsets
import java.util.Base64

class ArcaFiscalAuthorityAdapter internal constructor(
    private val settings: ArcaFiscalSettings,
    private val wsfe: ArcaWsfeClient,
    private val validator: FiscalAuthorizationValidator = FiscalAuthorizationValidator(),
    private val clock: Clock = Clock.systemUTC(),
    private val providerName: String = PROVIDER
) : FiscalAuthorityPort {

    override fun health(): FiscalAuthorityHealth {
        val health = wsfe.health()
        return FiscalAuthorityHealth(
            provider = providerName,
            environment = settings.environment,
            configured = true,
            available = health.available,
            checkedAt = LocalDateTime.now(clock),
            message = health.message
        )
    }

    override fun lastAuthorized(sequence: VoucherSequenceKey): Long = wsfe.lastAuthorized(sequence)

    override fun referenceData(): FiscalReferenceData = wsfe.referenceData()

    override fun authorize(request: FiscalAuthorizationRequest): FiscalAuthorizationResult {
        validator.validate(request)
        return wsfe.authorize(request)
    }

    override fun consult(voucher: AuthorizedVoucherKey): FiscalAuthorizationResult? = wsfe.consult(voucher)

    internal companion object {
        const val PROVIDER = "arca"

        fun create(settings: ArcaFiscalSettings, clock: Clock = Clock.systemUTC()): ArcaFiscalAuthorityAdapter {
            val errors = settings.validationErrors()
            if (errors.isNotEmpty()) throw ArcaConfigurationException(errors.joinToString("; "))
            val transport = JdkArcaSoapTransport(settings.connectTimeout, settings.requestTimeout)
            val signer = Pkcs12ArcaCmsSigner(settings, clock)
            val wsaa = ArcaWsaaClient(settings, signer, transport, clock)
            val tickets = CachedArcaAccessTicketProvider(
                wsaa,
                settings.ticketRefreshSkew.seconds,
                clock
            )
            val wsfe = ArcaWsfeClient(settings, tickets, transport, clock)
            return ArcaFiscalAuthorityAdapter(settings, wsfe, clock = clock)
        }

        /**
         * Uses the same WSAA/WSFE mapping as ARCA but signs the access request
         * with a transparent mock payload. This path is local-only and must
         * never be selected from a production profile.
         */
        fun createExternalMock(
            settings: ArcaFiscalSettings,
            clock: Clock = Clock.systemUTC()
        ): ArcaFiscalAuthorityAdapter {
            val transport = JdkArcaSoapTransport(settings.connectTimeout, settings.requestTimeout)
            val signer = ArcaCmsSigner { content ->
                val mockPayload = String(content, StandardCharsets.UTF_8) +
                    "<cuit>${settings.issuerCuit}</cuit>"
                Base64.getEncoder().encodeToString(mockPayload.toByteArray(StandardCharsets.UTF_8))
            }
            val wsaa = ArcaWsaaClient(settings, signer, transport, clock)
            val tickets = CachedArcaAccessTicketProvider(wsaa, settings.ticketRefreshSkew.seconds, clock)
            val wsfe = ArcaWsfeClient(
                settings = settings,
                tickets = tickets,
                transport = transport,
                clock = clock
            )
            return ArcaFiscalAuthorityAdapter(
                settings = settings,
                wsfe = wsfe,
                clock = clock,
                providerName = EXTERNAL_MOCK_PROVIDER
            )
        }

        private const val EXTERNAL_MOCK_PROVIDER = "mock-service"
    }
}
