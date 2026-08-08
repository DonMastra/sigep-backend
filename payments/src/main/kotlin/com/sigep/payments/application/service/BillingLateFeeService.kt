package com.sigep.payments.application.service

import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.common.application.service.BillingChargeSettlement
import com.sigep.common.application.service.BillingChargeSettlementObserver
import com.sigep.payments.domain.model.BillingCharge
import com.sigep.payments.domain.model.BillingChargeAdjustment
import com.sigep.payments.domain.model.BillingChargeAdjustmentSource
import com.sigep.payments.domain.model.BillingChargeAdjustmentStatus
import com.sigep.payments.domain.model.BillingChargeAdjustmentType
import com.sigep.payments.domain.model.BillingChargeStatus
import com.sigep.payments.domain.repository.BillingChargeAdjustmentRepository
import com.sigep.payments.domain.repository.BillingChargeRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class BillingLateFeeService(
    private val chargeRepository: BillingChargeRepository,
    private val adjustmentRepository: BillingChargeAdjustmentRepository,
    private val invoiceRepository: FiscalInvoiceRepository,
    private val settlementObservers: List<BillingChargeSettlementObserver>
) {

    @Transactional
    fun applyIfDue(
        chargeId: Long,
        source: BillingChargeAdjustmentSource,
        actorId: Long? = null
    ): BillingCharge {
        val charge = chargeRepository.findByIdForUpdate(chargeId)
            .orElseThrow { ResourceNotFoundException("Billing charge $chargeId not found") }
        if (!isDue(charge)) return charge
        if (invoiceRepository.findByChargeId(chargeId).isPresent) return charge
        adjustmentRepository.findByChargeIdAndTypeAndStatus(
            chargeId,
            BillingChargeAdjustmentType.LATE_FEE,
            BillingChargeAdjustmentStatus.ACTIVE
        ).orElse(null)?.let { return charge }

        val capitalPaid = charge.paidAmount.min(charge.baseAmount)
        val pendingPrincipal = money((charge.baseAmount - capitalPaid).max(BigDecimal.ZERO))
        if (pendingPrincipal <= BigDecimal.ZERO) return charge
        val fee = money(pendingPrincipal.multiply(charge.lateFeePercentage).divide(BigDecimal(100)))
        if (fee <= BigDecimal.ZERO) return charge

        val now = LocalDateTime.now(BUSINESS_ZONE)
        adjustmentRepository.save(
            BillingChargeAdjustment(
                charge = charge,
                baseAmountSnapshot = pendingPrincipal,
                ratePercentage = charge.lateFeePercentage,
                amount = fee,
                effectiveDate = LocalDate.now(BUSINESS_ZONE),
                appliedBy = actorId,
                applicationSource = source,
                createdAt = now
            )
        )
        val updated = chargeRepository.save(
            charge.copy(
                amount = money(charge.amount + fee),
                lateFeeAppliedAt = now,
                updatedAt = now
            )
        )
        notifySettlement(updated, paymentId = null)
        return updated
    }

    @Transactional
    fun reverse(chargeId: Long, reason: String, adminId: Long): BillingCharge {
        if (reason.isBlank()) throw ValidationException("A reason is required to reverse a late fee")
        val charge = chargeRepository.findByIdForUpdate(chargeId)
            .orElseThrow { ResourceNotFoundException("Billing charge $chargeId not found") }
        if (invoiceRepository.findByChargeId(chargeId).isPresent) {
            throw ResourceConflictException("A late fee cannot be reversed after an invoice was prepared")
        }
        val adjustment = adjustmentRepository.findByChargeIdAndTypeAndStatus(
            chargeId,
            BillingChargeAdjustmentType.LATE_FEE,
            BillingChargeAdjustmentStatus.ACTIVE
        ).orElseThrow { ResourceNotFoundException("Active late fee for charge $chargeId not found") }
        val newTotal = money(charge.amount - adjustment.amount)
        if (charge.paidAmount > newTotal) {
            throw ResourceConflictException("The late fee cannot be reversed because it has already been collected")
        }
        val now = LocalDateTime.now(BUSINESS_ZONE)
        adjustmentRepository.save(
            adjustment.copy(
                status = BillingChargeAdjustmentStatus.REVERSED,
                reversalReason = reason.trim(),
                reversedAt = now,
                reversedBy = adminId
            )
        )
        val updatedStatus = when {
            charge.paidAmount == newTotal -> BillingChargeStatus.PAID
            charge.paidAmount > BigDecimal.ZERO -> BillingChargeStatus.PARTIALLY_PAID
            else -> BillingChargeStatus.OPEN
        }
        val updated = chargeRepository.save(
            charge.copy(amount = newTotal, status = updatedStatus, updatedAt = now)
        )
        notifySettlement(updated, paymentId = null)
        return updated
    }

    fun processDueCharges(batchSize: Int = 100): Int {
        val ids = chargeRepository.findLateFeeCandidateIds(
            LocalDate.now(BUSINESS_ZONE),
            setOf(BillingChargeStatus.OPEN, BillingChargeStatus.PARTIALLY_PAID),
            PageRequest.of(0, batchSize.coerceIn(1, 500))
        )
        ids.forEach { applyIfDue(it, BillingChargeAdjustmentSource.SCHEDULER) }
        return ids.size
    }

    private fun isDue(charge: BillingCharge): Boolean =
        charge.status in setOf(BillingChargeStatus.OPEN, BillingChargeStatus.PARTIALLY_PAID) &&
            charge.lateFeeEligible &&
            charge.lateFeeAppliedAt == null &&
            charge.lateFeePercentage > BigDecimal.ZERO &&
            LocalDate.now(BUSINESS_ZONE).isAfter(charge.dueDate)

    private fun notifySettlement(charge: BillingCharge, paymentId: Long?) {
        val outstanding = money((charge.amount - charge.paidAmount).max(BigDecimal.ZERO))
        val settlement = BillingChargeSettlement(
            sourceType = charge.sourceType,
            sourceId = charge.sourceId,
            paymentId = paymentId,
            baseAmount = charge.baseAmount,
            lateFeeAmount = money(charge.amount - charge.baseAmount),
            paidAmount = charge.paidAmount,
            outstandingAmount = outstanding,
            status = charge.status.name
        )
        settlementObservers.forEach { it.onChargeSettlementChanged(settlement) }
    }

    private fun money(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_EVEN)

    private companion object {
        val BUSINESS_ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    }
}
