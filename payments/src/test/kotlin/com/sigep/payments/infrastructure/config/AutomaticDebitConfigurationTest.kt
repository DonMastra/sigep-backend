package com.sigep.payments.infrastructure.config

import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AutomaticDebitConfigurationTest {
    private val configuration = AutomaticDebitConfiguration()

    @Test
    fun `mock is simulated outside production`() {
        val environment = MockEnvironment()
            .withProperty("billing.automatic-debit.provider", "mock")
            .apply { setActiveProfiles("qa") }

        assertTrue(configuration.automaticDebitPort(environment).simulated)
    }

    @Test
    fun `mock fails closed in production`() {
        val environment = MockEnvironment()
            .withProperty("billing.automatic-debit.provider", "mock")
            .apply { setActiveProfiles("prod") }

        assertFailsWith<IllegalStateException> {
            configuration.automaticDebitPort(environment)
        }
    }

    @Test
    fun `disabled provider is not simulated`() {
        val environment = MockEnvironment()
            .withProperty("billing.automatic-debit.provider", "disabled")

        assertFalse(configuration.automaticDebitPort(environment).simulated)
    }
}
