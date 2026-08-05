package com.sigep.payments.domain.repository

import com.sigep.payments.domain.model.BillingOutboxEvent
import com.sigep.payments.domain.model.BillingOutboxEventType
import com.sigep.payments.domain.model.BillingOutboxStatus
import com.sigep.payments.domain.model.BillingAccount
import com.sigep.payments.domain.model.BillingCharge
import com.sigep.payments.domain.model.BillingChargeStatus
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
          and (:studentQuery is null or locate(lower(:studentQuery), lower(charge.studentName)) > 0)
          and (:profileStatus is null or profile.status = :profileStatus)
        """
    )
    fun findByFilters(
        @Param("status") status: BillingChargeStatus?,
        @Param("studentId") studentId: Long?,
        @Param("studentQuery") studentQuery: String?,
        @Param("profileStatus") profileStatus: BillingProfileStatus?,
        pageable: Pageable
    ): Page<BillingCharge>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select charge from BillingCharge charge where charge.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<BillingCharge>
}

interface PaymentAllocationRepository : JpaRepository<PaymentAllocation, Long> {
    fun findByChargeId(chargeId: Long): Optional<PaymentAllocation>
    fun findByPaymentId(paymentId: Long): List<PaymentAllocation>
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
