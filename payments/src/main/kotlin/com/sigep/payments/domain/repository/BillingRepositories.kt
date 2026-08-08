package com.sigep.payments.domain.repository

import com.sigep.payments.domain.model.BillingOutboxEvent
import com.sigep.payments.domain.model.BillingOutboxEventType
import com.sigep.payments.domain.model.BillingOutboxStatus
import com.sigep.payments.domain.model.BillingAccount
import com.sigep.payments.domain.model.BillingCharge
import com.sigep.payments.domain.model.BillingChargeStatus
import com.sigep.payments.domain.model.BillingCollectionChannel
import com.sigep.payments.domain.model.BillingProfile
import com.sigep.payments.domain.model.BillingProfileStatus
import com.sigep.payments.domain.model.BillingRun
import com.sigep.payments.domain.model.BillingRunItem
import com.sigep.payments.domain.model.FiscalInvoice
import com.sigep.payments.domain.model.FiscalInvoiceAttempt
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.payments.domain.model.Payment
import com.sigep.payments.domain.model.PaymentAllocation
import com.sigep.payments.domain.model.PaymentReceipt
import com.sigep.payments.domain.model.BillingChargeAdjustment
import com.sigep.payments.domain.model.BillingChargeAdjustmentStatus
import com.sigep.payments.domain.model.BillingChargeAdjustmentType
import com.sigep.payments.domain.model.BillingChargeFiscalDecision
import com.sigep.payments.domain.model.AutomaticDebitMandate
import com.sigep.payments.domain.model.AutomaticDebitMandateStatus
import com.sigep.payments.domain.model.AutomaticDebitInstruction
import com.sigep.payments.domain.model.AutomaticDebitInstructionStatus
import com.sigep.payments.domain.model.AutomaticDebitEvent
import com.sigep.payments.domain.model.VoucherSequence
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.time.LocalDate
import java.util.Optional

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun existsByExternalReference(externalReference: String): Boolean
    fun findByCreationKey(creationKey: String): Optional<Payment>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<Payment>
}

interface PaymentReceiptRepository : JpaRepository<PaymentReceipt, Long> {
    fun findByPaymentId(paymentId: Long): Optional<PaymentReceipt>
}

interface FiscalInvoiceRepository : JpaRepository<FiscalInvoice, Long> {
    fun findByPaymentId(paymentId: Long): Optional<FiscalInvoice>
    fun findByChargeId(chargeId: Long): Optional<FiscalInvoice>
    fun findByStatus(status: FiscalInvoiceStatus, pageable: Pageable): Page<FiscalInvoice>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from FiscalInvoice invoice where invoice.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<FiscalInvoice>
}

interface BillingAccountRepository : JpaRepository<BillingAccount, Long> {
    fun findByGuardianUserId(guardianUserId: Long): Optional<BillingAccount>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from BillingAccount account where account.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<BillingAccount>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from BillingAccount account where account.guardianUserId = :guardianUserId")
    fun findByGuardianUserIdForUpdate(@Param("guardianUserId") guardianUserId: Long): Optional<BillingAccount>
}

interface BillingProfileRepository : JpaRepository<BillingProfile, Long> {
    fun findByAccountId(accountId: Long): Optional<BillingProfile>
}

interface BillingChargeRepository : JpaRepository<BillingCharge, Long> {
    fun findBySourceTypeAndSourceId(sourceType: String, sourceId: Long): Optional<BillingCharge>

    @Query(
        """
        select charge
        from BillingCharge charge
        join BillingProfile profile on profile.account.id = charge.account.id
        where (:status is null or charge.status = :status)
          and (:studentId is null or charge.studentId = :studentId)
          and (:studentQuery = '' or locate(lower(:studentQuery), lower(charge.studentName)) > 0)
          and (:profileStatus is null or profile.status = :profileStatus)
          and (:fiscalDisposition is null or charge.fiscalDisposition = :fiscalDisposition)
          and (:collectionChannel is null or charge.collectionChannel = :collectionChannel)
          and (
            :overdue is null or :overdue = false or
            (charge.dueDate < :today and charge.status <> com.sigep.payments.domain.model.BillingChargeStatus.PAID
              and charge.status <> com.sigep.payments.domain.model.BillingChargeStatus.CANCELLED)
          )
          and (
            :automaticDebitStatus is null or exists (
              select instruction.id from AutomaticDebitInstruction instruction
              where instruction.charge.id = charge.id and instruction.status = :automaticDebitStatus
            )
          )
        """
    )
    fun findByFilters(
        @Param("status") status: BillingChargeStatus?,
        @Param("studentId") studentId: Long?,
        @Param("studentQuery") studentQuery: String,
        @Param("profileStatus") profileStatus: BillingProfileStatus?,
        @Param("fiscalDisposition") fiscalDisposition: com.sigep.payments.domain.model.BillingChargeFiscalDisposition?,
        @Param("overdue") overdue: Boolean?,
        @Param("today") today: LocalDate,
        @Param("automaticDebitStatus") automaticDebitStatus: AutomaticDebitInstructionStatus?,
        @Param("collectionChannel") collectionChannel: BillingCollectionChannel?,
        pageable: Pageable
    ): Page<BillingCharge>

    fun findByAccountIdOrderByDueDateAsc(accountId: Long): List<BillingCharge>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select charge from BillingCharge charge where charge.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<BillingCharge>

    @Query(
        """
        select charge.id from BillingCharge charge
        where charge.lateFeeEligible = true
          and charge.lateFeeAppliedAt is null
          and charge.lateFeePercentage > 0
          and charge.dueDate < :today
          and charge.status in :statuses
        order by charge.dueDate asc, charge.id asc
        """
    )
    fun findLateFeeCandidateIds(
        @Param("today") today: LocalDate,
        @Param("statuses") statuses: Set<BillingChargeStatus>,
        pageable: Pageable
    ): List<Long>

}

interface PaymentAllocationRepository : JpaRepository<PaymentAllocation, Long> {
    fun findByChargeIdOrderByCreatedAtAsc(chargeId: Long): List<PaymentAllocation>
    fun findByPaymentId(paymentId: Long): List<PaymentAllocation>
}

interface BillingChargeFiscalDecisionRepository : JpaRepository<BillingChargeFiscalDecision, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Optional<BillingChargeFiscalDecision>
    fun findByChargeIdOrderByCreatedAtAsc(chargeId: Long): List<BillingChargeFiscalDecision>
}

interface BillingChargeAdjustmentRepository : JpaRepository<BillingChargeAdjustment, Long> {
    fun findByChargeIdAndTypeAndStatus(
        chargeId: Long,
        type: BillingChargeAdjustmentType,
        status: BillingChargeAdjustmentStatus
    ): Optional<BillingChargeAdjustment>
    fun findByChargeIdOrderByCreatedAtAsc(chargeId: Long): List<BillingChargeAdjustment>
}

interface AutomaticDebitMandateRepository : JpaRepository<AutomaticDebitMandate, Long> {
    fun findByAccountIdAndIsDefaultTrueAndStatusIn(
        accountId: Long,
        statuses: Set<AutomaticDebitMandateStatus>
    ): Optional<AutomaticDebitMandate>
    fun findByAccountIdOrderByCreatedAtDesc(accountId: Long): List<AutomaticDebitMandate>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<AutomaticDebitMandate>
}

interface AutomaticDebitInstructionRepository : JpaRepository<AutomaticDebitInstruction, Long> {
    fun existsByChargeIdAndStatusIn(chargeId: Long, statuses: Set<AutomaticDebitInstructionStatus>): Boolean
    fun findByChargeIdOrderByCreatedAtDesc(chargeId: Long): List<AutomaticDebitInstruction>
    fun findByInvoiceIdOrderByCreatedAtDesc(invoiceId: Long): List<AutomaticDebitInstruction>
    fun findByIdempotencyKey(idempotencyKey: String): Optional<AutomaticDebitInstruction>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select instruction from AutomaticDebitInstruction instruction where instruction.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<AutomaticDebitInstruction>
}

interface AutomaticDebitEventRepository : JpaRepository<AutomaticDebitEvent, Long> {
    fun existsByProviderEventId(providerEventId: String): Boolean
    fun findByInstructionIdOrderByOccurredAtAsc(instructionId: Long): List<AutomaticDebitEvent>
}

interface BillingRunRepository : JpaRepository<BillingRun, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Optional<BillingRun>
}

interface BillingRunItemRepository : JpaRepository<BillingRunItem, Long> {
    fun findByRunIdOrderByIdAsc(runId: Long): List<BillingRunItem>
}

interface FiscalInvoiceAttemptRepository : JpaRepository<FiscalInvoiceAttempt, Long> {
    fun countByInvoiceId(invoiceId: Long): Long
    fun findByInvoiceIdOrderByAttemptNumberAsc(invoiceId: Long): List<FiscalInvoiceAttempt>
}

interface BillingOutboxRepository : JpaRepository<BillingOutboxEvent, Long> {
    fun findByInvoiceIdAndEventType(
        invoiceId: Long,
        eventType: BillingOutboxEventType
    ): Optional<BillingOutboxEvent>

    @Query(
        """
        select event.id
        from BillingOutboxEvent event
        where event.status in :statuses
          and event.nextAttemptAt <= :now
        order by event.createdAt asc
        """
    )
    fun findProcessableIds(
        @Param("statuses") statuses: Set<BillingOutboxStatus>,
        @Param("now") now: LocalDateTime,
        pageable: Pageable
    ): List<Long>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from BillingOutboxEvent event where event.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<BillingOutboxEvent>
}

interface VoucherSequenceRepository : JpaRepository<VoucherSequence, Long> {
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO voucher_sequences (
                issuer_cuit, point_of_sale, voucher_type, last_confirmed_number, updated_at, version
            ) VALUES (:issuerCuit, :pointOfSale, :voucherType, 0, NOW(), 0)
            ON CONFLICT (issuer_cuit, point_of_sale, voucher_type) DO NOTHING
        """
    )
    fun ensureExists(
        @Param("issuerCuit") issuerCuit: String,
        @Param("pointOfSale") pointOfSale: Int,
        @Param("voucherType") voucherType: Int
    )

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select sequence from VoucherSequence sequence
        where sequence.issuerCuit = :issuerCuit
          and sequence.pointOfSale = :pointOfSale
          and sequence.voucherType = :voucherType
        """
    )
    fun findForUpdate(
        @Param("issuerCuit") issuerCuit: String,
        @Param("pointOfSale") pointOfSale: Int,
        @Param("voucherType") voucherType: Int
    ): Optional<VoucherSequence>
}
