package com.sigep.payments.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "billing_accounts",
    uniqueConstraints = [UniqueConstraint(name = "uk_billing_account_guardian", columnNames = ["guardian_user_id"])]
)
data class BillingAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "guardian_user_id", nullable = false)
    val guardianUserId: Long,

    @Column(name = "display_name", nullable = false, length = 200)
    val displayName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: BillingAccountStatus = BillingAccountStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    val version: Long = 0
)

enum class BillingAccountStatus { ACTIVE, INACTIVE }

@Entity
@Table(
    name = "billing_profiles",
    uniqueConstraints = [UniqueConstraint(name = "uk_billing_profile_account", columnNames = ["account_id"])]
)
data class BillingProfile(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    val account: BillingAccount,

    @Column(name = "receiver_name", nullable = false, length = 200)
    val receiverName: String,

    @Column(name = "receiver_address", length = 300)
    val receiverAddress: String? = null,

    @Column(name = "receiver_document_type")
    val receiverDocumentType: Int? = null,

    @Column(name = "receiver_document_number", length = 20)
    val receiverDocumentNumber: String? = null,

    @Column(name = "receiver_vat_condition_id")
    val receiverVatConditionId: Int? = null,

    @Column(name = "default_voucher_type")
    val defaultVoucherType: Int? = null,

    @Column(name = "default_fiscal_concept", nullable = false)
    val defaultFiscalConcept: Int = 2,

    @Column(name = "fiscal_currency", nullable = false, length = 3)
    val fiscalCurrency: String = "PES",

    @Column(name = "rg_5866_applicable", nullable = false)
    val rg5866Applicable: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: BillingProfileStatus = BillingProfileStatus.INCOMPLETE,

    @Column(name = "updated_by")
    val updatedBy: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    val version: Long = 0
)

enum class BillingProfileStatus { INCOMPLETE, READY }

@Entity
@Table(
    name = "billing_charges",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_billing_charge_source", columnNames = ["source_type", "source_id"])
    ],
    indexes = [
        Index(name = "idx_billing_charge_status_due", columnList = "status,due_date"),
        Index(name = "idx_billing_charge_student", columnList = "student_id"),
        Index(name = "idx_billing_charge_account", columnList = "account_id")
    ]
)
data class BillingCharge(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    val account: BillingAccount,

    @Column(name = "student_id")
    val studentId: Long? = null,

    @Column(name = "student_name", nullable = false, length = 200)
    val studentName: String,

    @Column(name = "source_type", nullable = false, length = 40)
    val sourceType: String,

    @Column(name = "source_id", nullable = false)
    val sourceId: Long,

    @Column(nullable = false, length = 80)
    val concept: String,

    @Column(nullable = false, length = 500)
    val description: String,

    @Column(nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String = "ARS",

    @Column(name = "due_date", nullable = false)
    val dueDate: LocalDate,

    @Column(name = "service_from")
    val serviceFrom: LocalDate? = null,

    @Column(name = "service_to")
    val serviceTo: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: BillingChargeStatus = BillingChargeStatus.OPEN,

    @Column(name = "paid_at")
    val paidAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    val version: Long = 0
)

enum class BillingChargeStatus { OPEN, PAID, CANCELLED }

@Entity
@Table(
    name = "payment_allocations",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_payment_allocation_charge", columnNames = ["charge_id"])
    ],
    indexes = [Index(name = "idx_payment_allocation_payment", columnList = "payment_id")]
)
data class PaymentAllocation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    val payment: Payment,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false, unique = true)
    val charge: BillingCharge,

    @Column(nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(
    name = "billing_runs",
    uniqueConstraints = [UniqueConstraint(name = "uk_billing_run_key", columnNames = ["idempotency_key"])]
)
data class BillingRun(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    val idempotencyKey: String,

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    val requestFingerprint: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_mode", nullable = false, length = 20)
    val selectionMode: BillingSelectionMode,

    @Enumerated(EnumType.STRING)
    @Column(name = "amount_treatment", nullable = false, length = 20)
    val amountTreatment: FiscalAmountTreatment,

    @Column(name = "requested_by", nullable = false)
    val requestedBy: Long,

    @Column(name = "issue_date", nullable = false)
    val issueDate: LocalDate,

    @Column(name = "selected_count", nullable = false)
    val selectedCount: Int,

    @Column(name = "created_count", nullable = false)
    val createdCount: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: BillingRunStatus = BillingRunStatus.COMPLETED,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class BillingSelectionMode { INDIVIDUAL, SELECTED, FILTERED }
enum class FiscalAmountTreatment { NON_TAXED, EXEMPT }
enum class BillingRunStatus { COMPLETED }

@Entity
@Table(
    name = "billing_run_items",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_billing_run_charge", columnNames = ["run_id", "charge_id"])
    ]
)
data class BillingRunItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    val run: BillingRun,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    val charge: BillingCharge,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    val invoice: FiscalInvoice,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
