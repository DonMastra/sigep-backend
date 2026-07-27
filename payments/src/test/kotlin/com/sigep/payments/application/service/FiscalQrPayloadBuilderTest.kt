package com.sigep.payments.application.service

import com.sigep.payments.domain.model.FiscalInvoice
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.payments.domain.model.Payment
import com.sigep.payments.domain.model.PaymentMethod
import com.sigep.payments.domain.model.PaymentStatus
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FiscalQrPayloadBuilderTest {

    @Test
    fun `builds ARCA version one QR payload for authorized CAE`() {
        val url = FiscalQrPayloadBuilder.build(invoice())

        assertTrue(url!!.startsWith("https://www.arca.gob.ar/fe/qr/?p="))
        val payload = url.substringAfter("?p=")
        val json = Base64.getDecoder().decode(payload).toString(StandardCharsets.UTF_8)
        assertEquals(
            """{"ver":1,"fecha":"2026-07-21","cuit":30712345678,"ptoVta":3,"tipoCmp":11,"nroCmp":42,"importe":100,"moneda":"PES","ctz":1,"tipoDocRec":80,"nroDocRec":20123456789,"tipoCodAut":"E","codAut":71234567890123}""",
            json
        )
    }

    @Test
    fun `does not expose QR before authorization`() {
        assertNull(FiscalQrPayloadBuilder.build(invoice().copy(authorizationCode = null)))
    }

    private fun invoice() = FiscalInvoice(
        id = 7,
        payment = Payment(
            id = 5,
            studentId = 3,
            amount = BigDecimal("100.00"),
            concept = "Cuota julio",
            paymentDate = LocalDate.of(2026, 7, 21),
            dueDate = LocalDate.of(2026, 7, 21),
            status = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            receiptNumber = "RX-2026-000005",
            notes = null
        ),
        creationKey = "invoice-7",
        requestFingerprint = "fingerprint",
        status = FiscalInvoiceStatus.AUTHORIZED,
        issuerCuit = "30712345678",
        pointOfSale = 3,
        voucherType = 11,
        voucherNumber = 42,
        concept = 2,
        receiverName = "Tutor",
        receiverAddress = "Calle Falsa 123, Buenos Aires",
        receiverDocumentType = 80,
        receiverDocumentNumber = "20123456789",
        receiverVatConditionId = 5,
        issueDate = LocalDate.of(2026, 7, 21),
        serviceFrom = LocalDate.of(2026, 7, 1),
        serviceTo = LocalDate.of(2026, 7, 31),
        paymentDueDate = LocalDate.of(2026, 7, 21),
        currency = "PES",
        exchangeRate = BigDecimal.ONE,
        totalAmount = BigDecimal("100.00"),
        nonTaxedAmount = BigDecimal.ZERO,
        netAmount = BigDecimal("100.00"),
        exemptAmount = BigDecimal.ZERO,
        vatAmount = BigDecimal.ZERO,
        otherTaxesAmount = BigDecimal.ZERO,
        authorizationCode = "71234567890123",
        authorizationExpiresOn = LocalDate.of(2026, 7, 31)
    )
}
