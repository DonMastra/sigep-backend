package com.sigep.common.application.service

import java.math.BigDecimal
import java.time.LocalDate

interface BillingChargeProvider {
    fun upsertCharge(command: BillingChargeCommand): BillingChargeInfo
    fun cancelCharge(sourceType: String, sourceId: Long)
}

data class BillingChargeCommand(
    val guardianUserId: Long,
    val studentId: Long?,
    val studentName: String,
    val sourceType: String,
    val sourceId: Long,
    val concept: String,
    val description: String,
    val amount: BigDecimal,
    val currency: String,
    val dueDate: LocalDate,
    val serviceFrom: LocalDate?,
    val serviceTo: LocalDate?,
    val receiverName: String,
    val receiverAddress: String?,
    val receiverDocumentNumber: String?
)

data class BillingChargeInfo(
    val id: Long,
    val sourceType: String,
    val sourceId: Long,
    val status: String
)

interface BillingChargeSettlementObserver {
    fun onChargePaid(sourceType: String, sourceId: Long, paymentId: Long)
}
