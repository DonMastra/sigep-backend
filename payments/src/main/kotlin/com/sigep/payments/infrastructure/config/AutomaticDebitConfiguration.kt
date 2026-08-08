package com.sigep.payments.infrastructure.config

import com.sigep.common.application.exception.ValidationException
import com.sigep.payments.application.gateway.AutomaticDebitAuthorizationCommand
import com.sigep.payments.application.gateway.AutomaticDebitAuthorizationResult
import com.sigep.payments.application.gateway.AutomaticDebitPort
import com.sigep.payments.domain.model.AutomaticDebitProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.util.UUID

@Configuration
class AutomaticDebitConfiguration {

    @Bean
    fun automaticDebitPort(environment: Environment): AutomaticDebitPort {
        val activeProfiles = environment.activeProfiles.map(String::lowercase).toSet()
        return when (val provider = environment.getProperty("billing.automatic-debit.provider", "disabled").lowercase()) {
            "mock" -> {
                check("prod" !in activeProfiles && "production" !in activeProfiles) {
                    "The automatic debit mock cannot be enabled with a production profile"
                }
                MockAutomaticDebitAdapter()
            }
            "disabled" -> DisabledAutomaticDebitAdapter()
            else -> error("Unsupported billing.automatic-debit.provider '$provider'")
        }
    }
}

private class DisabledAutomaticDebitAdapter : AutomaticDebitPort {
    override val provider: AutomaticDebitProvider? = null
    override val simulated: Boolean = false

    override fun authorize(command: AutomaticDebitAuthorizationCommand): AutomaticDebitAuthorizationResult =
        throw ValidationException("Automatic debit is disabled in this environment")
}

private class MockAutomaticDebitAdapter : AutomaticDebitPort {
    override val provider: AutomaticDebitProvider = AutomaticDebitProvider.MOCK
    override val simulated: Boolean = true

    override fun authorize(command: AutomaticDebitAuthorizationCommand) = AutomaticDebitAuthorizationResult(
        providerReference = "mock-mandate-${UUID.randomUUID()}"
    )
}
