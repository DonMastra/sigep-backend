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

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 2)
    val baseAmount: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    val paidAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, length = 3)
    val currency: String = "ARS",

    @Column(name = "due_date", nullable = false)
    val dueDate: LocalDate,

    @Column(name = "service_from")
    val serviceFrom: LocalDate? = null,

    @Column(name = "service_to")
    val serviceTo: LocalDate? = null,

    @Column(name = "late_fee_percentage", nullable = false, precision = 5, scale = 2)
    val lateFeePercentage: BigDecimal = BigDecimal.ZERO,

    @Column(name = "late_fee_eligible", nullable = false)
    val lateFeeEligible: Boolean = false,

    @Column(name = "automatic_debit_eligible", nullable = false)
    val automaticDebitEligible: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_channel", nullable = false, length = 30)
    val collectionChannel: BillingCollectionChannel = BillingCollectionChannel.REGULAR,

    @Column(name = "late_fee_applied_at")
    val lateFeeAppliedAt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "fiscal_disposition", nullable = false, length = 20)
    val fiscalDisposition: BillingChargeFiscalDisposition = BillingChargeFiscalDisposition.PENDING,

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

enum class BillingChargeStatus { OPEN, PARTIALLY_PAID, PAID, CANCELLED }
enum class BillingChargeFiscalDisposition { PENDING, EXCLUDED }
enum class BillingCollectionChannel { REGULAR, AUTOMATIC_DEBIT }

@Entity
@Table(
    name = "payment_allocations",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_payment_allocation_payment_charge", columnNames = ["payment_id", "charge_id"])
    ],
    indexes = [
        Index(name = "idx_payment_allocation_payment", columnList = "payment_id"),
        Index(name = "idx_payment_allocation_charge", columnList = "charge_id")
    ]
)
data class PaymentAllocation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    val payment: Payment,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    val charge: BillingCharge,

    @Column(nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(
    name = "billing_charge_fiscal_decisions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_billing_charge_fiscal_decision_key", columnNames = ["idempotency_key"])
    ],
    indexes = [Index(name = "idx_billing_charge_fiscal_decision_charge", columnList = "charge_id,created_at")]
)
data class BillingChargeFiscalDecision(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    val charge: BillingCharge,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val decision: FiscalClosure,

    @Column(length = 500)
    val reason: String? = null,

    @Column(name = "decided_by", nullable = false)
    val decidedBy: Long,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    val idempotencyKey: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class FiscalClosure { KEEP_PENDING, EXCLUDE_CHARGE }

@Entity
@Table(
    name = "billing_charge_adjustments",
    indexes = [Index(name = "idx_billing_charge_adjustment_charge", columnList = "charge_id,created_at")]
)
data class BillingChargeAdjustment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    val charge: BillingCharge,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: BillingChargeAdjustmentType = BillingChargeAdjustmentType.LATE_FEE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: BillingChargeAdjustmentStatus = BillingChargeAdjustmentStatus.ACTIVE,

    @Column(name = "base_amount_snapshot", nullable = false, precision = 12, scale = 2)
    val baseAmountSnapshot: BigDecimal,

    @Column(name = "rate_percentage", nullable = false, precision = 5, scale = 2)
    val ratePercentage: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    @Column(name = "effective_date", nullable = false)
    val effectiveDate: LocalDate,

    @Column(name = "applied_by")
    val appliedBy: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "application_source", nullable = false, length = 30)
    val applicationSource: BillingChargeAdjustmentSource,

    @Column(name = "reversal_reason", length = 500)
    val reversalReason: String? = null,

    @Column(name = "reversed_at")
    val reversedAt: LocalDateTime? = null,

    @Column(name = "reversed_by")
    val reversedBy: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class BillingChargeAdjustmentType { LATE_FEE }
enum class BillingChargeAdjustmentStatus { ACTIVE, REVERSED }
enum class BillingChargeAdjustmentSource { SCHEDULER, PAYMENT, BILLING_RUN, AUTOMATIC_DEBIT, ADMIN }

@Entity
@Table(
    name = "automatic_debit_mandates",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_automatic_debit_mandate_provider_reference",
            columnNames = ["provider", "provider_reference"]
        )
    ],
    indexes = [Index(name = "idx_automatic_debit_mandate_status", columnList = "status,account_id")]
)
data class AutomaticDebitMandate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    val account: BillingAccount,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val provider: AutomaticDebitProvider,

    @Column(name = "provider_reference", nullable = false, length = 200)
    val providerReference: String,

    @Column(name = "masked_label", nullable = false, length = 100)
    val maskedLabel: String,

    @Column(name = "processor_name", nullable = false, length = 80)
    val processorName: String = "Generic",

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 30)
    val instrumentType: AutomaticDebitInstrumentType = AutomaticDebitInstrumentType.CARD,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val scope: AutomaticDebitScope = AutomaticDebitScope.INSTALLMENTS,

    @Column(name = "effective_from", nullable = false)
    val effectiveFrom: LocalDate = LocalDate.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val status: AutomaticDebitMandateStatus,

    @Column(name = "is_default", nullable = false)
    val isDefault: Boolean = true,

    @Column(name = "consent_version", nullable = false, length = 40)
    val consentVersion: String,

    @Column(name = "consented_at", nullable = false)
    val consentedAt: LocalDateTime,

    @Column(name = "consented_by", nullable = false)
    val consentedBy: Long,

    @Column(name = "cancelled_at")
    val cancelledAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    val version: Long = 0
)

enum class AutomaticDebitProvider { MANUAL, MOCK }
enum class AutomaticDebitMandateStatus { PENDING_AUTHORIZATION, ACTIVE, PAUSED, CANCELLED, EXPIRED }
enum class AutomaticDebitInstrumentType { CARD, BANK_ACCOUNT }
enum class AutomaticDebitScope { INSTALLMENTS, INSTALLMENTS_AND_ENROLLMENT }

@Entity
@Table(
    name = "automatic_debit_instructions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_automatic_debit_instruction_key", columnNames = ["idempotency_key"]),
        UniqueConstraint(name = "uk_automatic_debit_instruction_provider_reference", columnNames = ["provider_reference"])
    ],
    indexes = [
        Index(name = "idx_automatic_debit_instruction_schedule", columnList = "status,processing_date,created_at"),
        Index(name = "idx_automatic_debit_instruction_charge", columnList = "charge_id,created_at"),
        Index(name = "idx_automatic_debit_instruction_invoice", columnList = "invoice_id,created_at")
    ]
)
data class AutomaticDebitInstruction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mandate_id", nullable = false)
    val mandate: AutomaticDebitMandate,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    val charge: BillingCharge,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    val invoice: FiscalInvoice,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    val payment: Payment? = null,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    val idempotencyKey: String,

    @Column(name = "provider_reference", unique = true, length = 200)
    val providerReference: String? = null,

    @Column(nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(name = "processing_date", nullable = false)
    val processingDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val status: AutomaticDebitInstructionStatus,

    @Column(name = "failure_code", length = 80)
    val failureCode: String? = null,

    @Column(name = "failure_message", length = 500)
    val failureMessage: String? = null,

    @Column(name = "submission_reference", length = 150)
    val submissionReference: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 40)
    val resolution: AutomaticDebitResolution? = null,

    @Column(name = "resolution_reason", length = 500)
    val resolutionReason: String? = null,

    @Column(name = "resolved_by")
    val resolvedBy: Long? = null,

    @Column(name = "created_by")
    val createdBy: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "submitted_at")
    val submittedAt: LocalDateTime? = null,

    @Column(name = "resolved_at")
    val resolvedAt: LocalDateTime? = null,

    @Version
    val version: Long = 0
)

enum class AutomaticDebitInstructionStatus {
    READY_FOR_PROCESSING,
    SUBMITTED,
    APPROVED,
    REJECTED,
    UNKNOWN,
    ACCOUNTING_RESOLUTION_REQUIRED,
    CREDIT_NOTE_REQUIRED,
    REVERSED,
    CANCELLED
}

enum class AutomaticDebitResolution { KEEP_INVOICE, REQUEST_CREDIT_NOTE }

@Entity
@Table(
    name = "automatic_debit_events",
    uniqueConstraints = [UniqueConstraint(name = "uk_automatic_debit_event_provider", columnNames = ["provider_event_id"])],
    indexes = [Index(name = "idx_automatic_debit_event_instruction", columnList = "instruction_id,occurred_at")]
)
data class AutomaticDebitEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instruction_id", nullable = false)
    val instruction: AutomaticDebitInstruction,

    @Column(name = "provider_event_id", nullable = false, unique = true, length = 200)
    val providerEventId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val type: AutomaticDebitEventType,

    @Column(name = "sanitized_detail", length = 1000)
    val sanitizedDetail: String? = null,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: LocalDateTime,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class AutomaticDebitEventType {
    PREPARED,
    SUBMITTED,
    APPROVED,
    REJECTED,
    UNKNOWN,
    RESOLUTION_KEEP_INVOICE,
    RESOLUTION_CREDIT_NOTE_REQUIRED,
    REVERSED,
    CANCELLED
}

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
