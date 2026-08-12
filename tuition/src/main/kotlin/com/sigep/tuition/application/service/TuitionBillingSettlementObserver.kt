package com.sigep.tuition.application.service

import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.service.BillingChargeSettlementObserver
import com.sigep.common.application.service.BillingChargeSettlement
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
    override fun onChargeSettlementChanged(settlement: BillingChargeSettlement) {
        if (settlement.sourceType != BILLING_SOURCE_TYPE) {
            return
        }
        val entry = ledgerEntryRepository.findById(settlement.sourceId)
            .orElseThrow { ResourceNotFoundException("Tuition ledger entry ${settlement.sourceId} not found") }
        if (entry.status == TuitionLedgerStatus.CANCELLED) {
            throw ResourceConflictException("Cancelled tuition ledger entry ${settlement.sourceId} cannot be settled")
        }

        val now = LocalDateTime.now()
        val ledgerStatus = when (settlement.status) {
            "PAID" -> TuitionLedgerStatus.PAID
            "PARTIALLY_PAID" -> TuitionLedgerStatus.PARTIALLY_PAID
            else -> TuitionLedgerStatus.PENDING
        }
        ledgerEntryRepository.save(
            entry.copy(
                status = ledgerStatus,
                paidAmount = settlement.paidAmount,
                lateFeeAmount = settlement.lateFeeAmount,
                billingReference = settlement.paymentId?.let { "PAYMENT-$it" } ?: entry.billingReference,
                updatedAt = now
            )
        )
        val application = entry.application
        if (
            entry.concept == TuitionLedgerConcept.TUITION_ENROLLMENT &&
            ledgerStatus == TuitionLedgerStatus.PAID &&
            application.status == TuitionApplicationStatus.PAYMENT_PENDING
        ) {
            val studentId = application.studentId
                ?: throw ResourceConflictException(
                    message = "Student must be resolved before enrollment payment",
                    code = "STUDENT_NOT_RESOLVED",
                    field = "studentId"
                )
            applicationRepository.save(
                application.copy(
                    studentId = studentId,
                    status = TuitionApplicationStatus.ENROLLED_PENDING_PLACEMENT,
                    updatedAt = now
                )
            )
        } else if (
            entry.concept == TuitionLedgerConcept.TUITION_ENROLLMENT &&
            ledgerStatus != TuitionLedgerStatus.PAID &&
            application.status in setOf(
                TuitionApplicationStatus.ENROLLED_PENDING_PLACEMENT,
                TuitionApplicationStatus.READY_FOR_ACADEMIC_ASSIGNMENT,
                TuitionApplicationStatus.WAITLISTED
            )
        ) {
            applicationRepository.save(
                application.copy(
                    status = TuitionApplicationStatus.PAYMENT_PENDING,
                    warningMessage = "El pago de matricula fue revertido o quedo incompleto; la asignacion academica permanece bloqueada.",
                    updatedAt = now
                )
            )
        } else if (
            entry.concept == TuitionLedgerConcept.TUITION_ENROLLMENT &&
            ledgerStatus != TuitionLedgerStatus.PAID &&
            application.status == TuitionApplicationStatus.APPROVED
        ) {
            applicationRepository.save(
                application.copy(
                    warningMessage = "El pago de matricula fue revertido luego de la asignacion. Revisar la deuda sin eliminar al estudiante ni su historial.",
                    updatedAt = now
                )
            )
        }
    }

    private companion object {
        const val BILLING_SOURCE_TYPE = "TUITION_LEDGER"
    }
}
