package com.sigep.payments.application.service

import com.sigep.common.application.exception.ValidationException
import com.sigep.payments.application.dto.BillingWorkflowDto
import com.sigep.payments.application.dto.RegisterPaymentAndInvoiceRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BillingWorkflowService(
    private val paymentService: PaymentApplicationService,
    private val billingService: BillingApplicationService
) {

    @Transactional
    fun registerPaymentAndInvoice(
        idempotencyKey: String,
        request: RegisterPaymentAndInvoiceRequest,
        adminId: Long
    ): BillingWorkflowDto {
        if (idempotencyKey.isBlank() || idempotencyKey.length > ROOT_KEY_MAX_LENGTH) {
            throw ValidationException("Idempotency-Key is required and must have at most $ROOT_KEY_MAX_LENGTH characters")
        }

        val created = paymentService.create(
            "$idempotencyKey:create",
            request.payment,
            request.confirmation.paymentDate
        )
        val paymentId = created.payment.id
        val confirmed = paymentService.confirm(
            paymentId,
            "$idempotencyKey:confirm",
            request.confirmation,
            adminId
        )
        val invoice = billingService.createInvoice(
            paymentId,
            "$idempotencyKey:invoice",
            request.invoice
        )
        return BillingWorkflowDto(payment = confirmed, invoice = invoice)
    }

    private companion object {
        const val ROOT_KEY_MAX_LENGTH = 120
    }
}
