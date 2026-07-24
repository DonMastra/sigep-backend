package com.sigep.payments.infrastructure.config

import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.application.gateway.FiscalEnvironment
import com.sigep.payments.application.service.BillingIssuerSettings
import com.sigep.payments.application.service.BillingDocumentSettings
import com.sigep.payments.application.service.FiscalInvoicePreflightService
import com.sigep.payments.infrastructure.fiscal.ArcaFiscalAuthorityAdapter
import com.sigep.payments.infrastructure.fiscal.ArcaFiscalSettings
import com.sigep.payments.infrastructure.fiscal.DisabledFiscalAuthorityAdapter
import com.sigep.payments.infrastructure.fiscal.FiscalResilienceSettings
import com.sigep.payments.infrastructure.fiscal.MockFiscalAuthorityAdapter
import com.sigep.payments.infrastructure.fiscal.ResilientFiscalAuthorityAdapter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate

@Configuration
class BillingFiscalConfiguration {

    @Bean
    fun billingIssuerSettings(environment: Environment) = BillingIssuerSettings(
        cuit = environment.getProperty("billing.issuer.cuit")?.trim()?.takeIf(String::isNotEmpty),
        pointOfSale = environment.getProperty("billing.issuer.point-of-sale")?.trim()?.takeIf(String::isNotEmpty)?.toInt()
    )

    @Bean
    fun billingDocumentSettings(environment: Environment) = BillingDocumentSettings(
        legalName = environment.getProperty("billing.issuer.legal-name")?.trim()?.takeIf(String::isNotEmpty),
        businessAddress = environment.getProperty("billing.issuer.business-address")?.trim()?.takeIf(String::isNotEmpty),
        vatCondition = environment.getProperty("billing.issuer.vat-condition")?.trim()?.takeIf(String::isNotEmpty),
        grossIncome = environment.getProperty("billing.issuer.gross-income")?.trim()?.takeIf(String::isNotEmpty),
        activityStart = environment.getProperty("billing.issuer.activity-start")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(LocalDate::parse)
    )

    @Bean
    fun fiscalInvoicePreflightService() = FiscalInvoicePreflightService()

    @Bean
    fun fiscalAuthorityPort(
        environment: Environment,
        issuerSettings: BillingIssuerSettings,
        meterRegistry: MeterRegistry
    ): FiscalAuthorityPort {
        val activeProfiles = environment.activeProfiles.map(String::lowercase).toSet()
        val provider = environment.getProperty("billing.fiscal.provider")
            ?.trim()
            ?.lowercase()
            ?: if ("dev" in activeProfiles) MOCK_PROVIDER else DISABLED_PROVIDER
        val configuredEnvironment = resolveFiscalEnvironment(environment, activeProfiles)

        val adapter = when (provider) {
            MOCK_PROVIDER -> {
                check("prod" !in activeProfiles && "production" !in activeProfiles) {
                    "The fiscal mock cannot be enabled with a production profile"
                }
                MockFiscalAuthorityAdapter()
            }
            EXTERNAL_MOCK_PROVIDER -> externalMockAdapter(environment, activeProfiles, issuerSettings)
            ARCA_PROVIDER -> arcaAdapter(environment, activeProfiles, configuredEnvironment, issuerSettings)
            DISABLED_PROVIDER -> DisabledFiscalAuthorityAdapter(
                providerName = DISABLED_PROVIDER,
                fiscalEnvironment = configuredEnvironment,
                reason = "Fiscal authority provider is disabled; set BILLING_FISCAL_PROVIDER=mock-service and start mock-billing-service for local development"
            )
            else -> error("Unsupported billing.fiscal.provider '$provider'")
        }

        return if (adapter is ArcaFiscalAuthorityAdapter) {
            val fiscalEnvironment = if (provider == EXTERNAL_MOCK_PROVIDER) {
                FiscalEnvironment.MOCK
            } else {
                configuredEnvironment
            }
            ResilientFiscalAuthorityAdapter(
                delegate = adapter,
                providerName = provider,
                fiscalEnvironment = fiscalEnvironment,
                settings = resilienceSettings(environment),
                meterRegistry = meterRegistry
            )
        } else {
            adapter
        }
    }

    private fun externalMockAdapter(
        environment: Environment,
        activeProfiles: Set<String>,
        issuerSettings: BillingIssuerSettings
    ): FiscalAuthorityPort {
        check(activeProfiles.none { it in PRODUCTION_PROFILES }) {
            "The external fiscal mock cannot be enabled with a production profile"
        }
        val issuerCuit = issuerSettings.cuit
            ?: return DisabledFiscalAuthorityAdapter(
                providerName = EXTERNAL_MOCK_PROVIDER,
                fiscalEnvironment = FiscalEnvironment.MOCK,
                reason = "External fiscal mock is not configured; missing billing.issuer.cuit"
            )
        if (!issuerCuit.matches(Regex("^[0-9]{11}$"))) {
            return DisabledFiscalAuthorityAdapter(
                providerName = EXTERNAL_MOCK_PROVIDER,
                fiscalEnvironment = FiscalEnvironment.MOCK,
                reason = "External fiscal mock is not configured; billing.issuer.cuit must contain 11 digits"
            )
        }

        val baseUri = URI.create(
            environment.getProperty("billing.mock-service.base-url") ?: DEFAULT_EXTERNAL_MOCK_BASE_URL
        )
        check(baseUri.isAbsolute && baseUri.host != null && baseUri.scheme.lowercase() in setOf("http", "https")) {
            "billing.mock-service.base-url must be an absolute HTTP(S) URL"
        }
        val normalizedBase = baseUri.toString().trimEnd('/')
        val settings = ArcaFiscalSettings(
            environment = FiscalEnvironment.MOCK,
            issuerCuit = issuerCuit,
            wsaaEndpoint = URI.create("$normalizedBase/ws/services/LoginCms"),
            wsfeEndpoint = URI.create("$normalizedBase/wsfev1/service.asmx"),
            keyStorePath = Path.of(".").toAbsolutePath().normalize(),
            keyStorePassword = charArrayOf(),
            keyAlias = null,
            connectTimeout = environment.getProperty("billing.mock-service.connect-timeout", Duration::class.java)
                ?: Duration.ofSeconds(2),
            requestTimeout = environment.getProperty("billing.mock-service.request-timeout", Duration::class.java)
                ?: Duration.ofSeconds(5),
            ticketRefreshSkew = Duration.ofMinutes(5)
        )
        return ArcaFiscalAuthorityAdapter.createExternalMock(settings)
    }

    private fun arcaAdapter(
        environment: Environment,
        activeProfiles: Set<String>,
        fiscalEnvironment: FiscalEnvironment,
        issuerSettings: BillingIssuerSettings
    ): FiscalAuthorityPort {
        check(fiscalEnvironment != FiscalEnvironment.PRODUCTION || activeProfiles.any { it in PRODUCTION_PROFILES }) {
            "ARCA production can only be selected with a production profile"
        }
        check(fiscalEnvironment != FiscalEnvironment.HOMOLOGATION || activeProfiles.none { it in PRODUCTION_PROFILES }) {
            "ARCA homologation cannot be selected with a production profile"
        }

        val keyStorePath = environment.getProperty("billing.arca.keystore.path")?.trim()?.takeIf(String::isNotEmpty)
        val keyStorePassword = environment.getProperty("billing.arca.keystore.password")?.takeIf(String::isNotEmpty)
        val missing = buildList {
            if (issuerSettings.cuit == null) add("billing.issuer.cuit")
            if (keyStorePath == null) add("billing.arca.keystore.path")
            if (keyStorePassword == null) add("billing.arca.keystore.password")
        }
        if (missing.isNotEmpty()) {
            return DisabledFiscalAuthorityAdapter(
                providerName = ARCA_PROVIDER,
                fiscalEnvironment = fiscalEnvironment,
                reason = "ARCA provider is not configured; missing ${missing.joinToString()}"
            )
        }

        val settings = ArcaFiscalSettings(
            environment = fiscalEnvironment,
            issuerCuit = requireNotNull(issuerSettings.cuit),
            wsaaEndpoint = URI.create(
                environment.getProperty("billing.arca.wsaa-url") ?: defaultWsaaEndpoint(fiscalEnvironment)
            ),
            wsfeEndpoint = URI.create(
                environment.getProperty("billing.arca.wsfe-url") ?: defaultWsfeEndpoint(fiscalEnvironment)
            ),
            keyStorePath = Path.of(requireNotNull(keyStorePath)).toAbsolutePath().normalize(),
            keyStorePassword = requireNotNull(keyStorePassword).toCharArray(),
            keyAlias = environment.getProperty("billing.arca.keystore.alias")?.trim()?.takeIf(String::isNotEmpty),
            connectTimeout = environment.getProperty("billing.arca.connect-timeout", Duration::class.java)
                ?: Duration.ofSeconds(5),
            requestTimeout = environment.getProperty("billing.arca.request-timeout", Duration::class.java)
                ?: Duration.ofSeconds(20),
            ticketRefreshSkew = environment.getProperty("billing.arca.ticket-refresh-skew", Duration::class.java)
                ?: Duration.ofMinutes(5)
        )
        val validationErrors = settings.validationErrors()
        if (validationErrors.isNotEmpty()) {
            return DisabledFiscalAuthorityAdapter(
                providerName = ARCA_PROVIDER,
                fiscalEnvironment = fiscalEnvironment,
                reason = "ARCA provider is not configured: ${validationErrors.joinToString("; ")}"
            )
        }
        return try {
            ArcaFiscalAuthorityAdapter.create(settings)
        } catch (_: Exception) {
            DisabledFiscalAuthorityAdapter(
                providerName = ARCA_PROVIDER,
                fiscalEnvironment = fiscalEnvironment,
                reason = "ARCA provider is not configured: the PKCS12 credential could not be loaded"
            )
        } finally {
            settings.keyStorePassword.fill('\u0000')
        }
    }

    private fun resolveFiscalEnvironment(
        environment: Environment,
        activeProfiles: Set<String>
    ): FiscalEnvironment {
        val configured = environment.getProperty("billing.arca.environment")?.trim()?.lowercase()
        return when (configured) {
            "homologation" -> FiscalEnvironment.HOMOLOGATION
            "production" -> FiscalEnvironment.PRODUCTION
            null -> if ("prod" in activeProfiles || "production" in activeProfiles) {
                FiscalEnvironment.PRODUCTION
            } else {
                FiscalEnvironment.HOMOLOGATION
            }
            else -> error("Unsupported billing.arca.environment '$configured'")
        }
    }

    private fun resilienceSettings(environment: Environment) = FiscalResilienceSettings(
        maxConcurrentCalls = environment.getProperty(
            "billing.fiscal.resilience.max-concurrent-calls",
            Int::class.java
        ) ?: 4,
        maxWaitDuration = environment.getProperty(
            "billing.fiscal.resilience.max-wait-duration",
            Duration::class.java
        ) ?: Duration.ZERO,
        failureRateThreshold = environment.getProperty(
            "billing.fiscal.resilience.failure-rate-threshold",
            Float::class.java
        ) ?: 50f,
        minimumNumberOfCalls = environment.getProperty(
            "billing.fiscal.resilience.minimum-number-of-calls",
            Int::class.java
        ) ?: 5,
        slidingWindowSize = environment.getProperty(
            "billing.fiscal.resilience.sliding-window-size",
            Int::class.java
        ) ?: 10,
        permittedCallsInHalfOpenState = environment.getProperty(
            "billing.fiscal.resilience.permitted-calls-in-half-open-state",
            Int::class.java
        ) ?: 2,
        openStateDuration = environment.getProperty(
            "billing.fiscal.resilience.open-state-duration",
            Duration::class.java
        ) ?: Duration.ofSeconds(30),
        referenceDataCacheTtl = environment.getProperty(
            "billing.fiscal.reference-data-cache-ttl",
            Duration::class.java
        ) ?: Duration.ofHours(6),
        referenceDataStaleIfError = environment.getProperty(
            "billing.fiscal.reference-data-stale-if-error",
            Duration::class.java
        ) ?: Duration.ofHours(24)
    )

    private fun defaultWsaaEndpoint(environment: FiscalEnvironment): String = when (environment) {
        // The current official WSAA page publishes the afip.gov.ar aliases;
        // keep the URL configurable because ARCA is migrating service names.
        FiscalEnvironment.HOMOLOGATION -> "https://wsaahomo.afip.gov.ar/ws/services/LoginCms"
        FiscalEnvironment.PRODUCTION -> "https://wsaa.afip.gov.ar/ws/services/LoginCms"
        FiscalEnvironment.MOCK -> error("ARCA cannot use the mock environment")
    }

    private fun defaultWsfeEndpoint(environment: FiscalEnvironment): String = when (environment) {
        FiscalEnvironment.HOMOLOGATION -> "https://wswhomo.afip.gov.ar/wsfev1/service.asmx"
        FiscalEnvironment.PRODUCTION -> "https://servicios1.afip.gov.ar/wsfev1/service.asmx"
        FiscalEnvironment.MOCK -> error("ARCA cannot use the mock environment")
    }

    private companion object {
        const val MOCK_PROVIDER = "mock"
        const val EXTERNAL_MOCK_PROVIDER = "mock-service"
        const val ARCA_PROVIDER = "arca"
        const val DISABLED_PROVIDER = "disabled"
        const val DEFAULT_EXTERNAL_MOCK_BASE_URL = "http://localhost:8091"
        val PRODUCTION_PROFILES = setOf("prod", "production")
    }
}
