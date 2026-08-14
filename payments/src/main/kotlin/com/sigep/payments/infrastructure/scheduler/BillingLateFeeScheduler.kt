package com.sigep.payments.infrastructure.scheduler

import com.sigep.payments.application.service.BillingLateFeeService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BillingLateFeeScheduler(
    private val lateFeeService: BillingLateFeeService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${billing.late-fee.cron:0 15 1 * * *}", zone = "America/Argentina/Buenos_Aires")
    fun applyDueLateFees() {
        val processed = lateFeeService.processDueCharges()
        if (processed > 0) logger.info("Processed {} late-fee candidates", processed)
    }
}
