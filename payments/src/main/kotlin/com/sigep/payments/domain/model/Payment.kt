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

    @Column(nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false)
    val concept: String,

    @Column(nullable = false)
    val paymentDate: LocalDate?,

    @Column(nullable = false)
    val dueDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: PaymentStatus = PaymentStatus.PENDING,

    @Enumerated(EnumType.STRING)
    val paymentMethod: PaymentMethod?,

    @Column(unique = true)
    val receiptNumber: String?,

    @Column(length = 1000)
    val notes: String?,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
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

