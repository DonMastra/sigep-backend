package com.sigep.tuition.application.service

import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.service.BillingChargeSettlementObserver
import com.sigep.tuition.domain.model.TuitionApplicationStatus
import com.sigep.tuition.domain.model.TuitionLedgerConcept
import com.sigep.tuition.domain.model.TuitionLedgerStatus
import com.sigep.tuition.domain.repository.TuitionApplicationRepository
import com.sigep.tuition.domain.repository.TuitionLedgerEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class TuitionBillingSettlementObserver(
    private val ledgerEntryRepository: TuitionLedgerEntryRepository,
    private val applicationRepository: TuitionApplicationRepository
) : BillingChargeSettlementObserver {

    @Transactional
    override fun onChargePaid(sourceType: String, sourceId: Long, paymentId: Long) {
        if (sourceType != BILLING_SOURCE_TYPE) {
            return
        }
        val entry = ledgerEntryRepository.findById(sourceId)
            .orElseThrow { ResourceNotFoundException("Tuition ledger entry $sourceId not found") }
        if (entry.status == TuitionLedgerStatus.PAID) {
            return
        }
        if (entry.status == TuitionLedgerStatus.CANCELLED) {
            throw ResourceConflictException("Cancelled tuition ledger entry $sourceId cannot be paid")
        }

        val now = LocalDateTime.now()
        ledgerEntryRepository.save(
            entry.copy(
                status = TuitionLedgerStatus.PAID,
                billingReference = "PAYMENT-$paymentId",
                updatedAt = now
            )
        )
        val application = entry.application
        if (
            entry.concept == TuitionLedgerConcept.TUITION_ENROLLMENT &&
            application.status in setOf(
                TuitionApplicationStatus.SEAT_RESERVED,
                TuitionApplicationStatus.PAYMENT_PENDING
            )
        ) {
            applicationRepository.save(
                application.copy(
                    status = TuitionApplicationStatus.READY_FOR_ADMIN_APPROVAL,
                    updatedAt = now
                )
            )
        }
    }

    private companion object {
        const val BILLING_SOURCE_TYPE = "TUITION_LEDGER"
    }
}
