package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.FiscalEnvironment
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

internal class ArcaFiscalSettings(
    val environment: FiscalEnvironment,
    val issuerCuit: String,
    val wsaaEndpoint: URI,
    val wsfeEndpoint: URI,
    val keyStorePath: Path,
    val keyStorePassword: CharArray,
    val keyAlias: String?,
    val connectTimeout: Duration,
    val requestTimeout: Duration,
    val ticketRefreshSkew: Duration,
    val serviceName: String = WSFE_SERVICE
) {
    fun validationErrors(): List<String> = buildList {
        if (!issuerCuit.matches(Regex("^[0-9]{11}$"))) add("billing.issuer.cuit must contain 11 digits")
        validateEndpoint("billing.arca.wsaa-url", wsaaEndpoint)
        validateEndpoint("billing.arca.wsfe-url", wsfeEndpoint)
        if (!Files.isRegularFile(keyStorePath) || !Files.isReadable(keyStorePath)) {
            add("billing.arca.keystore.path must point to a readable PKCS12 file")
        }
        if (keyStorePassword.isEmpty()) add("billing.arca.keystore.password is required")
        if (connectTimeout.isZero || connectTimeout.isNegative) add("billing.arca.connect-timeout must be positive")
        if (requestTimeout.isZero || requestTimeout.isNegative) add("billing.arca.request-timeout must be positive")
        if (ticketRefreshSkew.isNegative) add("billing.arca.ticket-refresh-skew cannot be negative")
    }

    private fun MutableList<String>.validateEndpoint(name: String, endpoint: URI) {
        if (endpoint.scheme?.lowercase() != "https" || endpoint.host.isNullOrBlank()) {
            add("$name must be an absolute HTTPS URL")
        }
    }

    override fun toString(): String =
        "ArcaFiscalSettings(environment=$environment, issuerCuit=redacted, endpoints=configured, keyStore=redacted)"

    private companion object {
        const val WSFE_SERVICE = "wsfe"
    }
}
