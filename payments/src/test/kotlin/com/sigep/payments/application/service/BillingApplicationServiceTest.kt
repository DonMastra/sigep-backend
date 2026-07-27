package com.sigep.payments.application.service

import com.sigep.payments.application.dto.CreateFiscalInvoiceRequest
import com.sigep.payments.application.dto.FiscalOtherTaxRequest
import com.sigep.payments.application.dto.FiscalVatSubtotalRequest
import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.domain.model.BillingOutboxEvent
import com.sigep.payments.domain.model.BillingOutboxEventType
import com.sigep.payments.domain.model.BillingOutboxStatus
import com.sigep.payments.domain.model.FiscalInvoice
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.payments.domain.model.Payment
import com.sigep.payments.domain.model.PaymentReceipt
import com.sigep.payments.domain.model.PaymentStatus
import com.sigep.payments.domain.repository.BillingOutboxRepository
import com.sigep.payments.domain.repository.FiscalInvoiceAttemptRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import com.sigep.payments.domain.repository.PaymentReceiptRepository
import com.sigep.payments.domain.repository.PaymentRepository
import com.sigep.payments.domain.repository.VoucherSequenceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BillingApplicationServiceTest {

    @Test
    fun `creates a ready invoice when fiscal preflight is complete`() {
        val fixture = Fixture(BillingIssuerSettings("30712345678", 3))

        val result = fixture.service.createInvoice(1L, "create-invoice-1", invoiceRequest())

        assertEquals(FiscalInvoiceStatus.READY, result.invoice.status)
        assertTrue(result.invoice.preflightErrors.isEmpty())
        assertEquals(BigDecimal("45000.00"), result.invoice.totalAmount)
    }

    @Test
    fun `keeps invoice in draft when issuer configuration is missing`() {
        val fixture = Fixture(BillingIssuerSettings(null, null))

        val result = fixture.service.createInvoice(1L, "create-invoice-1", invoiceRequest())

        assertEquals(FiscalInvoiceStatus.DRAFT, result.invoice.status)
        assertTrue(result.invoice.preflightErrors.any { it.contains("BILLING_ISSUER_CUIT") })
        assertTrue(result.invoice.preflightErrors.any { it.contains("POINT_OF_SALE") })
    }

    @Test
    fun `persists a validated VAT and other tax breakdown`() {
        val fixture = Fixture(BillingIssuerSettings("30712345678", 3))
        val request = invoiceRequest().copy(
            exemptAmount = BigDecimal.ZERO,
            netAmount = BigDecimal("35000.00"),
            vatAmount = BigDecimal("7350.00"),
            otherTaxesAmount = BigDecimal("2650.00"),
            vatSubtotals = listOf(
                FiscalVatSubtotalRequest(5, BigDecimal("35000.00"), BigDecimal("7350.00"))
            ),
            taxes = listOf(
                FiscalOtherTaxRequest(2, "Ingresos Brutos", BigDecimal("45000.00"), BigDecimal("5.888889"), BigDecimal("2650.00"))
            )
        )

        val result = fixture.service.createInvoice(1L, "create-taxed-invoice", request)

        assertEquals(FiscalInvoiceStatus.READY, result.invoice.status)
        assertEquals(BigDecimal("35000.00"), result.invoice.vatSubtotals.single().baseAmount)
        assertEquals("Ingresos Brutos", result.invoice.taxes.single().description)
    }

    @Test
    fun `keeps invoice in draft when VAT aggregate and detail differ`() {
        val fixture = Fixture(BillingIssuerSettings("30712345678", 3))
        val request = invoiceRequest().copy(
            exemptAmount = BigDecimal("23999.00"),
            netAmount = BigDecimal("20000.00"),
            vatAmount = BigDecimal("1001.00"),
            vatSubtotals = listOf(
                FiscalVatSubtotalRequest(5, BigDecimal("20000.00"), BigDecimal("1000.00"))
            )
        )

        val result = fixture.service.createInvoice(1L, "create-invalid-taxed-invoice", request)

        assertEquals(FiscalInvoiceStatus.DRAFT, result.invoice.status)
        assertTrue(result.invoice.preflightErrors.any { it.contains("detalle IVA") })
    }

    @Test
    fun `queues one authorization event with an idempotency key`() {
        val fixture = Fixture(BillingIssuerSettings("30712345678", 3))
        val created = fixture.service.createInvoice(1L, "create-invoice-1", invoiceRequest())

        val result = fixture.service.queueAuthorization(created.invoice.id, "authorize-invoice-1")

        assertEquals(FiscalInvoiceStatus.QUEUED, result.invoice.status)
        assertEquals(BillingOutboxStatus.PENDING, result.invoice.outboxStatus)
        verify(exactly = 1) { fixture.outboxRepository.save(any()) }
    }

    private class Fixture(settings: BillingIssuerSettings) {
        val paymentRepository = mockk<PaymentRepository>()
        val receiptRepository = mockk<PaymentReceiptRepository>()
        val invoiceRepository = mockk<FiscalInvoiceRepository>()
        val attemptRepository = mockk<FiscalInvoiceAttemptRepository>()
        val outboxRepository = mockk<BillingOutboxRepository>()
        private val sequenceRepository = mockk<VoucherSequenceRepository>()
        private val fiscalAuthorityPort = mockk<FiscalAuthorityPort>()
        private val payment = paidPayment()
        private var invoice: FiscalInvoice? = null
        private var outbox: BillingOutboxEvent? = null

        val service = BillingApplicationService(
            paymentRepository,
            receiptRepository,
            invoiceRepository,
            attemptRepository,
            outboxRepository,
            sequenceRepository,
            fiscalAuthorityPort,
            settings,
            FiscalInvoicePreflightService()
        )

        init {
            every { paymentRepository.findByIdForUpdate(1L) } returns Optional.of(payment)
            every { receiptRepository.findByPaymentId(1L) } returns Optional.of(receipt(payment))
            every { invoiceRepository.findByPaymentId(1L) } answers { Optional.ofNullable(invoice) }
            every { invoiceRepository.save(any()) } answers {
                invoice = firstArg<FiscalInvoice>().let { value ->
                    if (value.id == null) value.copy(id = 5L) else value
                }
                invoice!!
            }
            every { invoiceRepository.findByIdForUpdate(5L) } answers { Optional.ofNullable(invoice) }
            every { attemptRepository.findByInvoiceIdOrderByAttemptNumberAsc(5L) } returns emptyList()
            every {
                outboxRepository.findByInvoiceIdAndEventType(5L, BillingOutboxEventType.AUTHORIZE_INVOICE)
            } answers { Optional.ofNullable(outbox) }
            every { outboxRepository.save(any()) } answers {
                outbox = firstArg<BillingOutboxEvent>().copy(id = 8L)
                outbox!!
            }
        }
    }
}

private fun paidPayment() = Payment(
    id = 1L,
    studentId = 20L,
    amount = BigDecimal("45000.00"),
    concept = "Cuota julio",
    paymentDate = LocalDate.of(2026, 7, 9),
    dueDate = LocalDate.of(2026, 7, 10),
    status = PaymentStatus.PAID,
    paymentMethod = null,
    receiptNumber = "RX-2026-00000001",
    notes = null
)

private fun receipt(payment: Payment) = PaymentReceipt(
    id = 2L,
    payment = payment,
    receiptNumber = "RX-2026-00000001",
    payerName = "Tutor Responsable",
    amount = payment.amount,
    currency = payment.currency,
    concept = payment.concept,
    issuedAt = LocalDateTime.of(2026, 7, 9, 10, 0),
    issuedBy = 99L
)

private fun invoiceRequest() = CreateFiscalInvoiceRequest(
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
