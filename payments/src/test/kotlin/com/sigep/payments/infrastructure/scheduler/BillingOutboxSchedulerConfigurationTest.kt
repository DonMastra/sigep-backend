package com.sigep.payments.infrastructure.scheduler

import com.sigep.payments.application.service.BillingOutboxProcessor
import com.sigep.payments.domain.repository.BillingOutboxRepository
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.function.Supplier
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class BillingOutboxSchedulerConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(BillingOutboxScheduler::class.java)
        .withBean(
            BillingOutboxRepository::class.java,
            Supplier { mockk<BillingOutboxRepository>(relaxed = true) }
        )
        .withBean(
            BillingOutboxProcessor::class.java,
            Supplier { mockk<BillingOutboxProcessor>(relaxed = true) }
        )

    @Test
    fun `scheduler is disabled when billing outbox is disabled`() {
        contextRunner
            .withPropertyValues("billing.outbox.enabled=false")
            .run { context ->
                assertFalse(context.containsBean("billingOutboxScheduler"))
            }
    }

    @Test
    fun `scheduler remains enabled by default outside constrained environments`() {
        contextRunner.run { context ->
            assertNotNull(context.getBean(BillingOutboxScheduler::class.java))
        }
    }
}
