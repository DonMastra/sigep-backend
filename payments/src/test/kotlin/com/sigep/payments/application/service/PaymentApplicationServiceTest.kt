package com.sigep.payments.application.service

import com.sigep.payments.application.dto.ConfirmPaymentRequest
import com.sigep.payments.application.dto.CreatePaymentRequest
import com.sigep.payments.domain.model.BillingOutboxEventType
import com.sigep.payments.domain.model.Payment
import com.sigep.payments.domain.model.PaymentMethod
import com.sigep.payments.domain.model.PaymentReceipt
import com.sigep.payments.domain.model.PaymentStatus
import com.sigep.payments.domain.repository.BillingOutboxRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import com.sigep.payments.domain.repository.PaymentReceiptRepository
import com.sigep.payments.domain.repository.PaymentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PaymentApplicationServiceTest {

    private lateinit var paymentRepository: PaymentRepository
    private lateinit var receiptRepository: PaymentReceiptRepository
    private lateinit var invoiceRepository: FiscalInvoiceRepository
    private lateinit var outboxRepository: BillingOutboxRepository
    private lateinit var service: PaymentApplicationService

    @BeforeEach
    fun setUp() {
        paymentRepository = mockk()
        receiptRepository = mockk()
        invoiceRepository = mockk()
        outboxRepository = mockk()
        service = PaymentApplicationService(paymentRepository, receiptRepository, invoiceRepository, outboxRepository)
    }

    @Test
    fun `creates a pending payment without pretending it has been collected`() {
        every { paymentRepository.existsByExternalReference("transfer-1") } returns false
        every { paymentRepository.findByCreationKey("create-payment-1") } returns Optional.empty()
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().copy(id = 1L) }
        emptyDetailDependencies()

        val result = service.create(
            "create-payment-1",
            CreatePaymentRequest(
                studentId = 20L,
                amount = BigDecimal("45000.00"),
                concept = "Cuota julio",
                dueDate = LocalDate.of(2026, 7, 10),
                externalReference = " transfer-1 "
            )
        )

        assertEquals(PaymentStatus.PENDING, result.payment.status)
        assertEquals(null, result.payment.paymentDate)
        assertEquals("transfer-1", result.payment.externalReference)
        assertEquals(null, result.receipt)
    }

    @Test
    fun `confirming a payment issues one non fiscal receipt`() {
        var storedPayment = pendingPayment()
        var storedReceipt: PaymentReceipt? = null
        every { paymentRepository.findByIdForUpdate(1L) } answers { Optional.of(storedPayment) }
        every { paymentRepository.save(any()) } answers {
            storedPayment = firstArg()
            storedPayment
        }
        every { receiptRepository.save(any()) } answers {
            storedReceipt = firstArg<PaymentReceipt>().copy(id = 10L)
            storedReceipt!!
        }
        every { receiptRepository.findByPaymentId(1L) } answers { Optional.ofNullable(storedReceipt) }
        every { invoiceRepository.findByPaymentId(1L) } returns Optional.empty()

        val result = service.confirm(1L, "confirm-payment-1", confirmation(), adminId = 99L)

        assertEquals(PaymentStatus.PAID, result.payment.status)
        assertEquals("RX-2026-00000001", result.payment.receiptNumber)
        assertEquals("X", result.receipt?.documentType)
        assertEquals("DOCUMENTO NO VALIDO COMO FACTURA", result.receipt?.fiscalDisclaimer)
        assertEquals(99L, result.payment.confirmedBy)
    }

    @Test
    fun `repeating confirmation with the same key and payload is idempotent`() {
        var storedPayment = pendingPayment()
        var storedReceipt: PaymentReceipt? = null
        every { paymentRepository.findByIdForUpdate(1L) } answers { Optional.of(storedPayment) }
        every { paymentRepository.save(any()) } answers {
            storedPayment = firstArg()
            storedPayment
        }
        every { receiptRepository.save(any()) } answers {
            storedReceipt = firstArg<PaymentReceipt>().copy(id = 10L)
            storedReceipt!!
        }
        every { receiptRepository.findByPaymentId(1L) } answers { Optional.ofNullable(storedReceipt) }
        every { invoiceRepository.findByPaymentId(1L) } returns Optional.empty()

        val first = service.confirm(1L, "confirm-payment-1", confirmation(), adminId = 99L)
        val repeated = service.confirm(1L, "confirm-payment-1", confirmation(), adminId = 99L)

        assertEquals(first, repeated)
        assertNotNull(repeated.receipt)
        verify(exactly = 1) { receiptRepository.save(any()) }
    }

    private fun emptyDetailDependencies() {
        every { receiptRepository.findByPaymentId(1L) } returns Optional.empty()
        every { invoiceRepository.findByPaymentId(1L) } returns Optional.empty()
        every {
            outboxRepository.findByInvoiceIdAndEventType(any(), BillingOutboxEventType.AUTHORIZE_INVOICE)
        } returns Optional.empty()
    }

    private fun pendingPayment() = Payment(
        id = 1L,
        studentId = 20L,
        amount = BigDecimal("45000.00"),
        concept = "Cuota julio",
        dueDate = LocalDate.of(2026, 7, 10),
        status = PaymentStatus.PENDING,
        paymentMethod = null,
        receiptNumber = null,
        notes = null
    )

    private fun confirmation() = ConfirmPaymentRequest(
        paymentDate = LocalDate.of(2026, 7, 9),
        paymentMethod = PaymentMethod.BANK_TRANSFER,
        payerName = "Tutor Responsable"
    )
}
