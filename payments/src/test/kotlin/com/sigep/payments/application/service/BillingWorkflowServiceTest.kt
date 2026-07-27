package com.sigep.payments.application.service

import com.sigep.payments.application.dto.BillingWorkflowDto
import com.sigep.payments.application.dto.ConfirmPaymentRequest
import com.sigep.payments.application.dto.CreateFiscalInvoiceRequest
import com.sigep.payments.application.dto.CreatePaymentRequest
import com.sigep.payments.application.dto.FiscalInvoiceDetailDto
import com.sigep.payments.application.dto.PaymentDetailDto
import com.sigep.payments.application.dto.PaymentDto
import com.sigep.payments.application.dto.RegisterPaymentAndInvoiceRequest
import com.sigep.payments.domain.model.PaymentMethod
import com.sigep.payments.domain.model.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

class BillingWorkflowServiceTest {

    @Test
    fun `register workflow derives stable keys for all three local steps`() {
        val paymentService = mockk<PaymentApplicationService>()
        val billingService = mockk<BillingApplicationService>()
        val request = workflowRequest()
        val created = paymentDetail(PaymentStatus.PENDING)
        val confirmed = paymentDetail(PaymentStatus.PAID)
        val invoice = mockk<FiscalInvoiceDetailDto>()

        every {
            paymentService.create(
                "workflow-1:create",
                request.payment,
                request.confirmation.paymentDate
            )
        } returns created
        every {
            paymentService.confirm(1L, "workflow-1:confirm", request.confirmation, 99L)
        } returns confirmed
        every { billingService.createInvoice(1L, "workflow-1:invoice", request.invoice) } returns invoice

        val result: BillingWorkflowDto = BillingWorkflowService(paymentService, billingService)
            .registerPaymentAndInvoice("workflow-1", request, 99L)

        assertEquals(PaymentStatus.PAID, result.payment.payment.status)
        verify(exactly = 1) {
            paymentService.create(
                "workflow-1:create",
                request.payment,
                request.confirmation.paymentDate
            )
        }
        verify(exactly = 1) { billingService.createInvoice(1L, "workflow-1:invoice", request.invoice) }
    }

    private fun workflowRequest() = RegisterPaymentAndInvoiceRequest(
        payment = CreatePaymentRequest(
            studentId = 20L,
            amount = BigDecimal("45000.00"),
            concept = "Cuota julio",
            dueDate = LocalDate.of(2026, 7, 10)
        ),
        confirmation = ConfirmPaymentRequest(
            paymentDate = LocalDate.of(2026, 7, 9),
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            payerName = "Tutor Responsable"
        ),
        invoice = CreateFiscalInvoiceRequest(
            voucherType = 6,
            concept = 2,
            receiverName = "Consumidor Final",
            receiverAddress = "Calle Falsa 123, Buenos Aires",
            receiverDocumentType = 96,
            receiverDocumentNumber = "30123456",
            receiverVatConditionId = 5,
            issueDate = LocalDate.of(2026, 7, 9),
            serviceFrom = LocalDate.of(2026, 7, 1),
            serviceTo = LocalDate.of(2026, 7, 31),
            paymentDueDate = LocalDate.of(2026, 7, 9),
            exemptAmount = BigDecimal("45000.00")
        )
    )

    private fun paymentDetail(status: PaymentStatus) = PaymentDetailDto(
        payment = PaymentDto(
            id = 1L,
            studentId = 20L,
            amount = BigDecimal("45000.00"),
            currency = "ARS",
            concept = "Cuota julio",
            paymentDate = if (status == PaymentStatus.PAID) LocalDate.of(2026, 7, 9) else null,
            dueDate = LocalDate.of(2026, 7, 10),
            status = status,
            paymentMethod = if (status == PaymentStatus.PAID) PaymentMethod.BANK_TRANSFER else null,
            receiptNumber = null,
            externalReference = null,
            notes = null,
            confirmedAt = null,
            confirmedBy = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        ),
        receipt = null,
        invoice = null
    )
}
