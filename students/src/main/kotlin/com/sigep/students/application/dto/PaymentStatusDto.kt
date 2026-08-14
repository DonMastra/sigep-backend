package com.sigep.students.application.dto

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Extensible cuando se implemente el módulo de payments
 */
data class StudentPaymentStatusDto(
    val studentId: Long,
    val status: PaymentStatus,
    val balance: BigDecimal,
    val lastPaymentDate: LocalDate?,
    val nextDueDate: LocalDate?
)

enum class PaymentStatus {
    UP_TO_DATE,
    PENDING,
    OVERDUE
}

