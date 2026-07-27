package com.sigep.payments.infrastructure.scheduler

import com.sigep.payments.application.service.BillingOutboxProcessor
import com.sigep.payments.domain.model.BillingOutboxStatus
import com.sigep.payments.domain.repository.BillingOutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class BillingOutboxScheduler(
    private val outboxRepository: BillingOutboxRepository,
    private val processor: BillingOutboxProcessor
) {

    private val logger = LoggerFactory.getLogger(BillingOutboxScheduler::class.java)

    @Scheduled(fixedDelayString = "\${billing.outbox.poll-delay-ms:1000}")
    fun dispatch() {
        val ids = outboxRepository.findProcessableIds(
            statuses = setOf(BillingOutboxStatus.PENDING),
            now = LocalDateTime.now(),
            pageable = PageRequest.of(0, BATCH_SIZE)
        )
        ids.forEach { eventId ->
            try {
                processor.process(eventId)
            } catch (exception: Exception) {
                logger.error("Unexpected billing outbox failure for event {}", eventId, exception)
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 20
    }
}
