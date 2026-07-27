package com.sigep.payments.domain.model

import com.sigep.common.domain.AggregateRoot
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "payments")
data class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val studentId: Long,

    @Column(nullable = false, precision = 12, scale = 2)
    val amount: BigDecimal,

    // Keep Hibernate's development-time schema update safe for legacy rows.
    // V16 remains the authoritative migration and performs the same backfill
    // explicitly in QA/production databases.
    @Column(nullable = false, length = 3, columnDefinition = "varchar(3) default 'ARS'")
    val currency: String = "ARS",

    @Column(nullable = false)
    val concept: String,

    val paymentDate: LocalDate? = null,

    @Column(nullable = false)
    val dueDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: PaymentStatus = PaymentStatus.PENDING,

    @Enumerated(EnumType.STRING)
    val paymentMethod: PaymentMethod?,

    @Column(unique = true)
    val receiptNumber: String?,

    @Column(unique = true, length = 150)
    val externalReference: String? = null,

    @Column(unique = true, length = 128)
    val creationKey: String? = null,

    @Column(length = 64)
    val creationFingerprint: String? = null,

    @Column(unique = true, length = 128)
    val confirmationKey: String? = null,

    @Column(length = 64)
    val confirmationFingerprint: String? = null,

    val confirmedAt: LocalDateTime? = null,

    val confirmedBy: Long? = null,

    @Column(length = 1000)
    val notes: String?,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    // PostgreSQL cannot add a NOT NULL version column to a populated table
    // unless existing rows receive the optimistic-lock baseline.
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    val version: Long = 0
) : AggregateRoot

enum class PaymentStatus {
    PENDING,
    PAID,
    OVERDUE,
    CANCELLED
}

enum class PaymentMethod {
    CASH,
    CREDIT_CARD,
    DEBIT_CARD,
    BANK_TRANSFER,
    CHECK
}

