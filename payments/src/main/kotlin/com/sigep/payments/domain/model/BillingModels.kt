package com.sigep.payments.domain.model

import jakarta.persistence.Column
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "payment_receipts")
data class PaymentReceipt(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    val payment: Payment,

    @Column(nullable = false, unique = true, length = 40)
    val receiptNumber: String,

    @Column(nullable = false, length = 200)
    val payerName: String,

    @Column(nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(nullable = false, length = 500)
    val concept: String,

    @Column(nullable = false)
    val issuedAt: LocalDateTime,

    @Column(nullable = false)
    val issuedBy: Long,

    @Column(nullable = false, length = 100)
    val documentType: String = "X",

    @Column(nullable = false, length = 120)
    val fiscalDisclaimer: String = "DOCUMENTO NO VALIDO COMO FACTURA",

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(
    name = "fiscal_invoices",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_fiscal_invoice_payment", columnNames = ["payment_id"]),
        UniqueConstraint(name = "uk_fiscal_invoice_creation_key", columnNames = ["creation_key"]),
        UniqueConstraint(
            name = "uk_fiscal_invoice_voucher",
            columnNames = ["issuer_cuit", "point_of_sale", "voucher_type", "voucher_number"]
        )
    ]
)
data class FiscalInvoice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    val payment: Payment,

    @Column(name = "creation_key", nullable = false, unique = true, length = 128)
    val creationKey: String,

    @Column(nullable = false, length = 64)
    val requestFingerprint: String,

    @Column(length = 128)
    val authorizationKey: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val status: FiscalInvoiceStatus,

    @Column(length = 11)
    val issuerCuit: String? = null,

    val pointOfSale: Int? = null,

    @Column(nullable = false)
    val voucherType: Int,

    val voucherNumber: Long? = null,

    @Column(nullable = false)
    val concept: Int,

    @Column(nullable = false, length = 200)
    val receiverName: String,

    @Column(nullable = false, length = 300)
    val receiverAddress: String = "",

    @Column(nullable = false)
    val receiverDocumentType: Int,

    @Column(nullable = false, length = 20)
    val receiverDocumentNumber: String,

    @Column(nullable = false)
    val receiverVatConditionId: Int,

    @Column(nullable = false)
    val issueDate: LocalDate,

    val serviceFrom: LocalDate? = null,

    val serviceTo: LocalDate? = null,

    val paymentDueDate: LocalDate? = null,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(nullable = false, precision = 18, scale = 6)
    val exchangeRate: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    val totalAmount: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    val nonTaxedAmount: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    val netAmount: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    val exemptAmount: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    val vatAmount: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 2)
    val otherTaxesAmount: BigDecimal,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fiscal_invoice_vat_subtotals", joinColumns = [JoinColumn(name = "invoice_id")])
    @OrderColumn(name = "line_order")
    val vatSubtotals: List<FiscalVatSubtotal> = emptyList(),

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fiscal_invoice_taxes", joinColumns = [JoinColumn(name = "invoice_id")])
    @OrderColumn(name = "line_order")
    val taxes: List<FiscalOtherTax> = emptyList(),

    @Column(length = 14)
    val authorizationCode: String? = null,

    val authorizationExpiresOn: LocalDate? = null,

    val authorizedAt: LocalDateTime? = null,

    @Column(length = 200)
    val providerRequestId: String? = null,

    @Column(length = 4000)
    val preflightErrors: String? = null,

    @Column(length = 4000)
    val lastObservations: String? = null,

    @Column(length = 4000)
    val lastErrors: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    val version: Long = 0
)

@Embeddable
data class FiscalVatSubtotal(
    @Column(name = "vat_id", nullable = false)
    val id: Int,

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 2)
    val baseAmount: BigDecimal,

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal
)

@Embeddable
data class FiscalOtherTax(
    @Column(name = "tax_id", nullable = false)
    val id: Int,

    @Column(nullable = false, length = 200)
    val description: String,

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 2)
    val baseAmount: BigDecimal,

    @Column(nullable = false, precision = 12, scale = 6)
    val rate: BigDecimal,

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal
)

enum class FiscalInvoiceStatus {
    DRAFT,
    READY,
    QUEUED,
    AUTHORIZING,
    AUTHORIZED,
    AUTHORIZED_WITH_OBSERVATIONS,
    REJECTED,
    UNKNOWN
}

@Entity
@Table(
    name = "fiscal_invoice_attempts",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_fiscal_attempt_number", columnNames = ["invoice_id", "attempt_number"])
    ]
)
data class FiscalInvoiceAttempt(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    val invoice: FiscalInvoice,

    @Column(nullable = false)
    val attemptNumber: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: FiscalAttemptType,

    @Column(nullable = false, length = 30)
    val provider: String,

    @Column(nullable = false, length = 30)
    val environment: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val outcome: FiscalAttemptOutcome,

    @Column(length = 200)
    val providerRequestId: String? = null,

    @Column(length = 4000)
    val observations: String? = null,

    @Column(length = 4000)
    val errors: String? = null,

    @Column(nullable = false)
    val requestedAt: LocalDateTime,

    val respondedAt: LocalDateTime? = null
)

enum class FiscalAttemptType {
    AUTHORIZATION,
    RECONCILIATION
}

enum class FiscalAttemptOutcome {
    PROCESSING,
    APPROVED,
    REJECTED,
    UNKNOWN,
    FAILED
}

@Entity
@Table(
    name = "billing_outbox",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_billing_outbox_invoice_type", columnNames = ["invoice_id", "event_type"])
    ]
)
data class BillingOutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    val invoice: FiscalInvoice,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val eventType: BillingOutboxEventType = BillingOutboxEventType.AUTHORIZE_INVOICE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val status: BillingOutboxStatus = BillingOutboxStatus.PENDING,

    @Column(nullable = false)
    val attempts: Int = 0,

    @Column(nullable = false)
    val nextAttemptAt: LocalDateTime = LocalDateTime.now(),

    @Column(length = 1000)
    val lastError: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    val processedAt: LocalDateTime? = null,

    @Version
    val version: Long = 0
)

enum class BillingOutboxEventType {
    AUTHORIZE_INVOICE
}

enum class BillingOutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    WAITING_RECONCILIATION,
    FAILED
}

@Entity
@Table(
    name = "voucher_sequences",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_voucher_sequence",
            columnNames = ["issuer_cuit", "point_of_sale", "voucher_type"]
        )
    ]
)
data class VoucherSequence(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 11)
    val issuerCuit: String,

    @Column(nullable = false)
    val pointOfSale: Int,

    @Column(nullable = false)
    val voucherType: Int,

    @Column(nullable = false)
    val lastConfirmedNumber: Long = 0,

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Version
    val version: Long = 0
)
