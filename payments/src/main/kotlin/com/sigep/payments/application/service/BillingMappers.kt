package com.sigep.payments.application.service

import com.sigep.payments.application.dto.FiscalInvoiceAttemptDto
import com.sigep.payments.application.dto.FiscalInvoiceDto
import com.sigep.payments.application.dto.FiscalOtherTaxDto
import com.sigep.payments.application.dto.FiscalVatSubtotalDto
import com.sigep.payments.application.dto.PaymentDto
import com.sigep.payments.application.dto.PaymentReceiptDto
import com.sigep.payments.domain.model.BillingOutboxStatus
import com.sigep.payments.domain.model.FiscalInvoice
import com.sigep.payments.domain.model.FiscalInvoiceAttempt
import com.sigep.payments.domain.model.Payment
import com.sigep.payments.domain.model.PaymentReceipt

internal fun Payment.toDto() = PaymentDto(
    id = requireNotNull(id),
    studentId = studentId,
    amount = amount,
    currency = currency,
    concept = concept,
    paymentDate = paymentDate,
    dueDate = dueDate,
    status = status,
    paymentMethod = paymentMethod,
    receiptNumber = receiptNumber,
    externalReference = externalReference,
    notes = notes,
    confirmedAt = confirmedAt,
    confirmedBy = confirmedBy,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun PaymentReceipt.toDto() = PaymentReceiptDto(
    id = requireNotNull(id),
    paymentId = requireNotNull(payment.id),
    receiptNumber = receiptNumber,
    payerName = payerName,
    amount = amount,
    currency = currency,
    concept = concept,
    issuedAt = issuedAt,
    issuedBy = issuedBy,
    documentType = documentType,
    fiscalDisclaimer = fiscalDisclaimer
)

internal fun FiscalInvoice.toDto(outboxStatus: BillingOutboxStatus? = null) = FiscalInvoiceDto(
    id = requireNotNull(id),
    paymentId = payment?.id,
    chargeId = charge?.id,
    studentId = sourceStudentId(),
    paymentReceiptNumber = payment?.receiptNumber,
    status = status,
    issuerCuit = issuerCuit,
    pointOfSale = pointOfSale,
    voucherType = voucherType,
    voucherNumber = voucherNumber,
    concept = concept,
    receiverName = receiverName,
    receiverAddress = receiverAddress,
    receiverDocumentType = receiverDocumentType,
    receiverDocumentNumber = receiverDocumentNumber,
    receiverVatConditionId = receiverVatConditionId,
    issueDate = issueDate,
    serviceFrom = serviceFrom,
    serviceTo = serviceTo,
    paymentDueDate = paymentDueDate,
    currency = currency,
    exchangeRate = exchangeRate,
    totalAmount = totalAmount,
    nonTaxedAmount = nonTaxedAmount,
    netAmount = netAmount,
    exemptAmount = exemptAmount,
    vatAmount = vatAmount,
    otherTaxesAmount = otherTaxesAmount,
    vatSubtotals = vatSubtotals.map { FiscalVatSubtotalDto(it.id, it.baseAmount, it.amount) },
    taxes = taxes.map { FiscalOtherTaxDto(it.id, it.description, it.baseAmount, it.rate, it.amount) },
    authorizationCode = authorizationCode,
    authorizationExpiresOn = authorizationExpiresOn,
    authorizedAt = authorizedAt,
    qrUrl = FiscalQrPayloadBuilder.build(this),
    providerRequestId = providerRequestId,
    preflightErrors = preflightErrors.toMessages(),
    observations = lastObservations.toMessages(),
    errors = lastErrors.toMessages(),
    outboxStatus = outboxStatus,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun FiscalInvoiceAttempt.toDto() = FiscalInvoiceAttemptDto(
    id = requireNotNull(id),
    attemptNumber = attemptNumber,
    type = type,
    provider = provider,
    environment = environment,
    outcome = outcome,
    providerRequestId = providerRequestId,
    observations = observations.toMessages(),
    errors = errors.toMessages(),
    requestedAt = requestedAt,
    respondedAt = respondedAt
)

internal fun List<String>.toStorage(): String? = takeIf { it.isNotEmpty() }?.joinToString("\n")

internal fun String?.toMessages(): List<String> = this
    ?.lineSequence()
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.toList()
    .orEmpty()
