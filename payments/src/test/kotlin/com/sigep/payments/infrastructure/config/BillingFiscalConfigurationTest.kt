package com.sigep.payments.infrastructure.config

import com.sigep.payments.application.gateway.FiscalEnvironment
import com.sigep.payments.application.service.BillingIssuerSettings
import com.sigep.payments.infrastructure.fiscal.DisabledFiscalAuthorityAdapter
import com.sigep.payments.infrastructure.fiscal.MockFiscalAuthorityAdapter
import com.sigep.payments.infrastructure.fiscal.ResilientFiscalAuthorityAdapter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BillingFiscalConfigurationTest {

    private val configuration = BillingFiscalConfiguration()
    private val meterRegistry = SimpleMeterRegistry()

    @Test
    fun `development defaults to mock`() {
        val environment = MockEnvironment().apply { setActiveProfiles("dev") }

        assertIs<MockFiscalAuthorityAdapter>(
            configuration.fiscalAuthorityPort(environment, emptyIssuer(), meterRegistry)
        )
    }

    @Test
    fun `non development defaults to disabled`() {
        val environment = MockEnvironment().apply { setActiveProfiles("qa") }

        val adapter = assertIs<DisabledFiscalAuthorityAdapter>(
            configuration.fiscalAuthorityPort(environment, emptyIssuer(), meterRegistry)
        )
        assertEquals(FiscalEnvironment.HOMOLOGATION, adapter.health().environment)
    }

    @Test
    fun `production disabled status reports production environment`() {
        val environment = MockEnvironment().apply { setActiveProfiles("prod") }

        val adapter = assertIs<DisabledFiscalAuthorityAdapter>(
            configuration.fiscalAuthorityPort(environment, emptyIssuer(), meterRegistry)
        )
        assertEquals(FiscalEnvironment.PRODUCTION, adapter.health().environment)
    }

    @Test
    fun `production rejects mock even when explicitly configured`() {
        val environment = MockEnvironment()
            .withProperty("billing.fiscal.provider", "mock")
            .apply { setActiveProfiles("prod") }

        assertFailsWith<IllegalStateException> {
            configuration.fiscalAuthorityPort(environment, emptyIssuer(), meterRegistry)
        }
    }

    @Test
    fun `arca remains disabled when credentials are incomplete`() {
        val environment = MockEnvironment()
            .withProperty("billing.fiscal.provider", "arca")
            .apply { setActiveProfiles("qa") }

        val adapter = assertIs<DisabledFiscalAuthorityAdapter>(
            configuration.fiscalAuthorityPort(
                environment,
                BillingIssuerSettings("30712345678", 3),
                meterRegistry
            )
        )

        assertEquals("arca", adapter.health().provider)
        assertEquals(false, adapter.health().configured)
    }

    @Test
    fun `production endpoint cannot be selected from qa`() {
        val environment = MockEnvironment()
            .withProperty("billing.fiscal.provider", "arca")
            .withProperty("billing.arca.environment", "production")
            .apply { setActiveProfiles("qa") }

        assertFailsWith<IllegalStateException> {
            configuration.fiscalAuthorityPort(environment, emptyIssuer(), meterRegistry)
        }
    }

    @Test
    fun `external mock uses the remote SOAP path without a certificate`() {
        val environment = MockEnvironment()
            .withProperty("billing.fiscal.provider", "mock-service")
            .withProperty("billing.mock-service.base-url", "http://localhost:8091")
            .apply { setActiveProfiles("dev") }

        assertIs<ResilientFiscalAuthorityAdapter>(
            configuration.fiscalAuthorityPort(
                environment,
                BillingIssuerSettings("30712345678", 1),
                meterRegistry
            )
        )
    }

    @Test
    fun `production rejects external mock`() {
        val environment = MockEnvironment()
            .withProperty("billing.fiscal.provider", "mock-service")
            .apply { setActiveProfiles("prod") }

        assertFailsWith<IllegalStateException> {
            configuration.fiscalAuthorityPort(
                environment,
                BillingIssuerSettings("30712345678", 1),
                meterRegistry
            )
        }
    }

    private fun emptyIssuer() = BillingIssuerSettings(null, null)
}
