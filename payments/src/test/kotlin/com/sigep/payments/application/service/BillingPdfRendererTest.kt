package com.sigep.payments.application.service

import com.sigep.payments.domain.model.FiscalInvoice
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.payments.domain.model.FiscalVatSubtotal
import com.sigep.payments.domain.model.Payment
import com.sigep.payments.domain.model.PaymentMethod
import com.sigep.payments.domain.model.PaymentReceipt
import com.sigep.payments.domain.model.PaymentStatus
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertContains
import kotlin.test.assertTrue

class BillingPdfRendererTest {
    private val renderer = BillingPdfRenderer(
        BillingDocumentSettings(
            legalName = "Instituto Educativo del Sur S.A.",
            businessAddress = "Av. Siempre Viva 742, Ciudad Autonoma de Buenos Aires",
            vatCondition = "Responsable Monotributo",
            grossIncome = "30712345678",
            activityStart = LocalDate.of(2018, 3, 1)
        )
    )
    private val payment = Payment(
        id = 1L,
        studentId = 20L,
        amount = BigDecimal("121.00"),
        concept = "Cuota mensual julio 2026 - Nivel secundario",
        paymentDate = LocalDate.of(2026, 7, 21),
        dueDate = LocalDate.of(2026, 7, 10),
        status = PaymentStatus.PAID,
        paymentMethod = PaymentMethod.BANK_TRANSFER,
        receiptNumber = "RX-2026-00000001",
        notes = null
    )
    private val receipt = PaymentReceipt(
        id = 2L,
        payment = payment,
        receiptNumber = "RX-2026-00000001",
        payerName = "Maria Perez",
        amount = payment.amount,
        currency = "ARS",
        concept = payment.concept,
        issuedAt = LocalDateTime.of(2026, 7, 21, 14, 30),
        issuedBy = 99L
    )

    @Test
    fun `renders readable payment receipt PDF`() {
        val pdf = renderer.renderReceipt(receipt)
        val text = extractText(pdf)

        assertTrue(pdf.copyOfRange(0, 4).contentEquals("%PDF".toByteArray()))
        assertContains(text, "RECIBO X")
        assertContains(text, "DOCUMENTO NO VALIDO COMO FACTURA")
        assertContains(text, "Maria Perez")
        writeSample("recibo-sigep-muestra.pdf", pdf)
    }

    @Test
    fun `renders authorized invoice with CAE and QR`() {
        val pdf = renderer.renderInvoice(
            invoice(),
            documentWarning = "AMBIENTE MOCK - DOCUMENTO SIN VALIDEZ FISCAL"
        )
        val text = extractText(pdf)

        assertTrue(pdf.copyOfRange(0, 4).contentEquals("%PDF".toByteArray()))
        assertContains(text, "FACTURA B")
        assertContains(text, "CAE: 71234567890123")
        assertContains(text, "Autorizacion simulada por adapter mock")
        assertContains(text, "AMBIENTE MOCK - DOCUMENTO SIN VALIDEZ FISCAL")
        writeSample("factura-sigep-muestra.pdf", pdf)
    }

    @Test
    fun `marks ARCA homologation invoices as non fiscal documents`() {
        val pdf = renderer.renderInvoice(
            invoice(),
            documentWarning = "HOMOLOGACION ARCA - DOCUMENTO SIN VALIDEZ FISCAL"
        )
        val text = extractText(pdf)

        assertContains(text, "HOMOLOGACION ARCA - DOCUMENTO SIN VALIDEZ FISCAL")
        assertContains(text, "Autorizacion de homologacion ARCA")
    }

    private fun invoice() = FiscalInvoice(
        id = 3L,
        payment = payment,
        creationKey = "invoice-create",
        requestFingerprint = "a".repeat(64),
        authorizationKey = "invoice-authorize",
        status = FiscalInvoiceStatus.AUTHORIZED,
        issuerCuit = "30712345678",
        pointOfSale = 3,
        voucherType = 6,
        voucherNumber = 42,
        concept = 2,
        receiverName = "Maria Perez",
        receiverAddress = "Calle Falsa 123, Buenos Aires",
        receiverDocumentType = 96,
        receiverDocumentNumber = "30123456",
        receiverVatConditionId = 5,
        issueDate = LocalDate.of(2026, 7, 21),
        serviceFrom = LocalDate.of(2026, 7, 1),
        serviceTo = LocalDate.of(2026, 7, 31),
        paymentDueDate = LocalDate.of(2026, 7, 21),
        currency = "PES",
        exchangeRate = BigDecimal.ONE,
        totalAmount = BigDecimal("121.00"),
        nonTaxedAmount = BigDecimal.ZERO,
        netAmount = BigDecimal("100.00"),
        exemptAmount = BigDecimal.ZERO,
        vatAmount = BigDecimal("21.00"),
        otherTaxesAmount = BigDecimal.ZERO,
        vatSubtotals = listOf(FiscalVatSubtotal(5, BigDecimal("100.00"), BigDecimal("21.00"))),
        authorizationCode = "71234567890123",
        authorizationExpiresOn = LocalDate.of(2026, 7, 31),
        authorizedAt = LocalDateTime.of(2026, 7, 21, 15, 0),
        providerRequestId = "arca:3:6:42"
    )

    private fun extractText(pdf: ByteArray): String = Loader.loadPDF(pdf).use { document ->
        PDFTextStripper().getText(document)
    }

    private fun writeSample(filename: String, pdf: ByteArray) {
        val outputDirectory = System.getenv("BILLING_PDF_SAMPLE_DIR")?.takeIf(String::isNotBlank) ?: return
        Files.createDirectories(Path.of(outputDirectory))
        Files.write(Path.of(outputDirectory, filename), pdf)
    }
}
