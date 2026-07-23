package com.sigep.payments.application.service

import com.sigep.payments.application.gateway.FiscalAuthorityHealth
import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.application.gateway.FiscalAuthorizationResult
import com.sigep.payments.application.gateway.FiscalAuthorizationStatus
import com.sigep.payments.application.gateway.FiscalEnvironment
import com.sigep.payments.application.gateway.FiscalPreDispatchException
import com.sigep.payments.domain.model.BillingOutboxEvent
import com.sigep.payments.domain.model.BillingOutboxStatus
import com.sigep.payments.domain.model.FiscalInvoice
import com.sigep.payments.domain.model.FiscalInvoiceAttempt
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.payments.domain.model.FiscalAttemptOutcome
import com.sigep.payments.domain.model.Payment
import com.sigep.payments.domain.model.PaymentStatus
import com.sigep.payments.domain.model.VoucherSequence
import com.sigep.payments.domain.repository.BillingOutboxRepository
import com.sigep.payments.domain.repository.FiscalInvoiceAttemptRepository
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import com.sigep.payments.domain.repository.VoucherSequenceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BillingOutboxProcessorTest {

    @Test
    fun `authorized result advances local sequence and completes outbox`() {
        val outboxRepository = mockk<BillingOutboxRepository>()
        val invoiceRepository = mockk<FiscalInvoiceRepository>()
        val attemptRepository = mockk<FiscalInvoiceAttemptRepository>()
        val sequenceRepository = mockk<VoucherSequenceRepository>()
        val fiscalAuthorityPort = mockk<FiscalAuthorityPort>()
        var invoice = readyInvoice().copy(status = FiscalInvoiceStatus.QUEUED, authorizationKey = "authorize-5")
        var event = BillingOutboxEvent(id = 8L, invoice = invoice)
        var sequence = VoucherSequence(
            id = 3L,
            issuerCuit = "30712345678",
            pointOfSale = 3,
            voucherType = 6
        )
        val attemptSlot = slot<FiscalInvoiceAttempt>()

        every { outboxRepository.findByIdForUpdate(8L) } answers { Optional.of(event) }
        every { invoiceRepository.findByIdForUpdate(5L) } answers { Optional.of(invoice) }
        every { invoiceRepository.save(any()) } answers {
            invoice = firstArg()
            invoice
        }
        every { outboxRepository.save(any()) } answers {
            event = firstArg()
            event
        }
        every { sequenceRepository.ensureExists("30712345678", 3, 6) } returns Unit
        every { sequenceRepository.findForUpdate("30712345678", 3, 6) } answers { Optional.of(sequence) }
        every { sequenceRepository.save(any()) } answers {
            sequence = firstArg()
            sequence
        }
        every { attemptRepository.countByInvoiceId(5L) } returns 0L
        every { attemptRepository.save(capture(attemptSlot)) } answers {
            attemptSlot.captured.let { value -> if (value.id == null) value.copy(id = 4L) else value }
        }
        every { fiscalAuthorityPort.lastAuthorized(any()) } returns 0L
        every { fiscalAuthorityPort.health() } returns FiscalAuthorityHealth(
            provider = "mock",
            environment = FiscalEnvironment.MOCK,
            configured = true,
            available = true,
            checkedAt = LocalDateTime.now()
        )
        every { fiscalAuthorityPort.authorize(any()) } answers {
            val request = firstArg<com.sigep.payments.application.gateway.FiscalAuthorizationRequest>()
            FiscalAuthorizationResult(
                status = FiscalAuthorizationStatus.APPROVED,
                voucher = com.sigep.payments.application.gateway.AuthorizedVoucherKey(
                    request.sequence,
                    request.voucherNumber
                ),
                authorizationCode = "12345678901234",
                authorizationExpiresOn = LocalDate.of(2026, 7, 19),
                providerRequestId = "mock-5-1",
                processedAt = LocalDateTime.of(2026, 7, 9, 12, 0)
            )
        }

        BillingOutboxProcessor(
            outboxRepository,
            invoiceRepository,
            attemptRepository,
            sequenceRepository,
            fiscalAuthorityPort,
            FiscalInvoicePreflightService()
        ).process(8L)

        assertEquals(FiscalInvoiceStatus.AUTHORIZED, invoice.status)
        assertEquals("12345678901234", invoice.authorizationCode)
        assertEquals(1L, sequence.lastConfirmedNumber)
        assertEquals(BillingOutboxStatus.PROCESSED, event.status)
        assertNotNull(event.processedAt)
    }

    @Test
    fun `pre-dispatch provider failure keeps invoice queued and schedules backoff`() {
        val outboxRepository = mockk<BillingOutboxRepository>(relaxed = true)
        val invoiceRepository = mockk<FiscalInvoiceRepository>(relaxed = true)
        val attemptRepository = mockk<FiscalInvoiceAttemptRepository>(relaxed = true)
        val sequenceRepository = mockk<VoucherSequenceRepository>(relaxed = true)
        val fiscalAuthorityPort = mockk<FiscalAuthorityPort>()
        val invoice = readyInvoice().copy(status = FiscalInvoiceStatus.QUEUED, authorizationKey = "authorize-5")
        var event = BillingOutboxEvent(id = 8L, invoice = invoice)
        val sequence = VoucherSequence(
            id = 3L,
            issuerCuit = "30712345678",
            pointOfSale = 3,
            voucherType = 6
        )
        val before = LocalDateTime.now()

        every { outboxRepository.findByIdForUpdate(8L) } returns Optional.of(event)
        every { invoiceRepository.findByIdForUpdate(5L) } returns Optional.of(invoice)
        every { sequenceRepository.findForUpdate("30712345678", 3, 6) } returns Optional.of(sequence)
        every { outboxRepository.save(any()) } answers { firstArg<BillingOutboxEvent>().also { event = it } }
        every { fiscalAuthorityPort.lastAuthorized(any()) } throws IllegalStateException("provider unavailable")

        BillingOutboxProcessor(
            outboxRepository,
            invoiceRepository,
            attemptRepository,
            sequenceRepository,
            fiscalAuthorityPort,
            FiscalInvoicePreflightService()
        ).process(8L)

        assertEquals(BillingOutboxStatus.PENDING, event.status)
        assertEquals(1, event.attempts)
        assertTrue(event.nextAttemptAt.isAfter(before.plusSeconds(14)))
        assertEquals("Pre-dispatch provider failure: IllegalStateException", event.lastError)
        verify(exactly = 0) { fiscalAuthorityPort.authorize(any()) }
        verify(exactly = 0) { invoiceRepository.save(any()) }
    }

    @Test
    fun `local resilience rejection before authorization returns invoice to queued for safe retry`() {
        val outboxRepository = mockk<BillingOutboxRepository>(relaxed = true)
        val invoiceRepository = mockk<FiscalInvoiceRepository>(relaxed = true)
        val attemptRepository = mockk<FiscalInvoiceAttemptRepository>(relaxed = true)
        val sequenceRepository = mockk<VoucherSequenceRepository>(relaxed = true)
        val fiscalAuthorityPort = mockk<FiscalAuthorityPort>()
        var invoice = readyInvoice().copy(status = FiscalInvoiceStatus.QUEUED, authorizationKey = "authorize-5")
        var event = BillingOutboxEvent(id = 8L, invoice = invoice)
        var attempt: FiscalInvoiceAttempt? = null
        val sequence = VoucherSequence(
            id = 3L,
            issuerCuit = "30712345678",
            pointOfSale = 3,
            voucherType = 6
        )

        every { outboxRepository.findByIdForUpdate(8L) } answers { Optional.of(event) }
        every { invoiceRepository.findByIdForUpdate(5L) } answers { Optional.of(invoice) }
        every { invoiceRepository.save(any()) } answers {
            invoice = firstArg()
            invoice
        }
        every { outboxRepository.save(any()) } answers {
            event = firstArg()
            event
        }
        every { sequenceRepository.findForUpdate("30712345678", 3, 6) } returns Optional.of(sequence)
        every { attemptRepository.countByInvoiceId(5L) } returns 0L
        every { attemptRepository.save(any()) } answers {
            firstArg<FiscalInvoiceAttempt>().also { attempt = it }
        }
        every { fiscalAuthorityPort.lastAuthorized(any()) } returns 0L
        every { fiscalAuthorityPort.health() } returns FiscalAuthorityHealth(
            provider = "arca",
            environment = FiscalEnvironment.HOMOLOGATION,
            configured = true,
            available = true,
            checkedAt = LocalDateTime.now()
        )
        every { fiscalAuthorityPort.authorize(any()) } throws
            FiscalPreDispatchException("Fiscal provider circuit is open")

        BillingOutboxProcessor(
            outboxRepository,
            invoiceRepository,
            attemptRepository,
            sequenceRepository,
            fiscalAuthorityPort,
            FiscalInvoicePreflightService()
        ).process(8L)

        assertEquals(FiscalInvoiceStatus.QUEUED, invoice.status)
        assertTrue(invoice.lastErrors.orEmpty().startsWith("PRE_DISPATCH_FAILURE:"))
        assertEquals(FiscalAttemptOutcome.FAILED, attempt?.outcome)
        assertEquals(BillingOutboxStatus.PENDING, event.status)
        assertTrue(event.nextAttemptAt.isAfter(LocalDateTime.now().minusSeconds(1)))
        verify(exactly = 1) { fiscalAuthorityPort.authorize(any()) }
    }
}

private fun readyInvoice(): FiscalInvoice {
    val payment = Payment(
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
    return FiscalInvoice(
        id = 5L,
        payment = payment,
        creationKey = "create-5",
        requestFingerprint = "fingerprint",
        status = FiscalInvoiceStatus.READY,
        issuerCuit = "30712345678",
        pointOfSale = 3,
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
        currency = "PES",
        exchangeRate = BigDecimal.ONE.setScale(6),
        totalAmount = BigDecimal("45000.00"),
        nonTaxedAmount = BigDecimal("0.00"),
        netAmount = BigDecimal("0.00"),
        exemptAmount = BigDecimal("45000.00"),
        vatAmount = BigDecimal("0.00"),
        otherTaxesAmount = BigDecimal("0.00")
    )
}
