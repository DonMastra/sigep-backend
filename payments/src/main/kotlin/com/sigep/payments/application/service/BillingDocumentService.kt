package com.sigep.payments.application.service

import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.sigep.common.application.exception.ResourceConflictException
import com.sigep.common.application.exception.ResourceNotFoundException
import com.sigep.common.application.exception.ValidationException
import com.sigep.payments.domain.model.FiscalInvoice
import com.sigep.payments.domain.model.FiscalInvoiceStatus
import com.sigep.payments.domain.model.PaymentReceipt
import com.sigep.payments.domain.repository.FiscalInvoiceRepository
import com.sigep.payments.domain.repository.FiscalInvoiceAttemptRepository
import com.sigep.payments.domain.repository.PaymentReceiptRepository
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BillingDocumentSettings(
    val legalName: String?,
    val businessAddress: String?,
    val vatCondition: String?,
    val grossIncome: String?,
    val activityStart: LocalDate?
) {
    fun invoiceValidationErrors(): List<String> = buildList {
        if (legalName.isNullOrBlank()) add("Falta configurar BILLING_ISSUER_LEGAL_NAME")
        if (businessAddress.isNullOrBlank()) add("Falta configurar BILLING_ISSUER_BUSINESS_ADDRESS")
        if (vatCondition.isNullOrBlank()) add("Falta configurar BILLING_ISSUER_VAT_CONDITION")
        if (grossIncome.isNullOrBlank()) add("Falta configurar BILLING_ISSUER_GROSS_INCOME")
        if (activityStart == null) add("Falta configurar BILLING_ISSUER_ACTIVITY_START con formato AAAA-MM-DD")
    }
}

data class GeneratedBillingDocument(
    val filename: String,
    val content: ByteArray
)

@Service
@Transactional(readOnly = true)
class BillingDocumentService(
    private val receiptRepository: PaymentReceiptRepository,
    private val invoiceRepository: FiscalInvoiceRepository,
    private val attemptRepository: FiscalInvoiceAttemptRepository,
    settings: BillingDocumentSettings
) {
    private val renderer by lazy { BillingPdfRenderer(settings) }

    fun receipt(paymentId: Long): GeneratedBillingDocument {
        val receipt = receiptRepository.findByPaymentId(paymentId)
            .orElseThrow { ResourceNotFoundException("Receipt for payment $paymentId not found") }
        return GeneratedBillingDocument(
            filename = "recibo-${safeFilename(receipt.receiptNumber)}.pdf",
            content = renderer.renderReceipt(receipt)
        )
    }

    fun invoice(invoiceId: Long): GeneratedBillingDocument {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { ResourceNotFoundException("Fiscal invoice $invoiceId not found") }
        if (invoice.status !in AUTHORIZED_STATUSES) {
            throw ResourceConflictException("Only an authorized fiscal invoice can be downloaded")
        }
        val documentErrors = renderer.invoiceValidationErrors(invoice)
        if (documentErrors.isNotEmpty()) {
            throw ValidationException("Fiscal invoice document is incomplete", documentErrors)
        }
        return GeneratedBillingDocument(
            filename = "factura-${requireNotNull(invoice.pointOfSale).toString().padStart(5, '0')}-${requireNotNull(invoice.voucherNumber).toString().padStart(8, '0')}.pdf",
            content = renderer.renderInvoice(invoice, documentWarning(invoice))
        )
    }

    private fun documentWarning(invoice: FiscalInvoice): String? {
        val latestAttempt = attemptRepository
            .findByInvoiceIdOrderByAttemptNumberAsc(requireNotNull(invoice.id))
            .lastOrNull()
        return when {
            latestAttempt?.environment.equals("HOMOLOGATION", ignoreCase = true) ->
                "HOMOLOGACION ARCA - DOCUMENTO SIN VALIDEZ FISCAL"
            latestAttempt?.environment.equals("MOCK", ignoreCase = true) ||
                latestAttempt?.provider.equals("mock", ignoreCase = true) ||
                invoice.providerRequestId?.startsWith("mock-") == true ->
                "AMBIENTE MOCK - DOCUMENTO SIN VALIDEZ FISCAL"
            else -> null
        }
    }

    private fun safeFilename(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "-")

    private companion object {
        val AUTHORIZED_STATUSES = setOf(
            FiscalInvoiceStatus.AUTHORIZED,
            FiscalInvoiceStatus.AUTHORIZED_WITH_OBSERVATIONS
        )
    }
}

internal class BillingPdfRenderer(
    private val settings: BillingDocumentSettings
) {
    private val regular: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val amountFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("es", "AR")))

    fun invoiceValidationErrors(invoice: FiscalInvoice): List<String> = buildList {
        addAll(settings.invoiceValidationErrors())
        if (invoice.receiverAddress.isBlank() || invoice.receiverAddress.startsWith("NO INFORMADO")) {
            add("El comprobante no tiene un domicilio valido del receptor")
        }
        if (invoice.issuerCuit.isNullOrBlank()) add("El comprobante no tiene CUIT emisor")
        if (invoice.pointOfSale == null || invoice.voucherNumber == null) add("El comprobante no tiene numeracion fiscal")
        if (invoice.authorizationCode.isNullOrBlank() || invoice.authorizationExpiresOn == null) {
            add("El comprobante no tiene CAE y vencimiento")
        }
        if (FiscalQrPayloadBuilder.build(invoice).isNullOrBlank()) add("No se pudo construir el QR fiscal")
    }

    fun renderReceipt(receipt: PaymentReceipt): ByteArray = document { _, page, canvas ->
        val width = page.mediaBox.width
        header(canvas, width, "RECIBO X", "Documento interno de pago")
        fill(canvas, 36f, 704f, width - 72f, 28f, LIGHT_RED)
        text(canvas, receipt.fiscalDisclaimer, 48f, 714f, bold, 10f, DARK_RED)

        text(canvas, safe(settings.legalName ?: "SiGEP"), 42f, 675f, bold, 16f, INK)
        text(canvas, safe(settings.businessAddress ?: "Sistema de Gestion Educativa"), 42f, 657f, regular, 9f, MUTED)
        labelValue(canvas, "Recibo", receipt.receiptNumber, 370f, 677f)
        labelValue(canvas, "Fecha", receipt.issuedAt.toLocalDate().format(dateFormat), 370f, 659f)

        box(canvas, 36f, 565f, width - 72f, 68f)
        labelValue(canvas, "Pagador", receipt.payerName, 48f, 610f, 430f)
        labelValue(canvas, "Medio", paymentMethodLabel(receipt.payment.paymentMethod?.name), 48f, 587f, 430f)

        tableHeader(canvas, 36f, 522f, width - 72f, listOf("Concepto" to 360f, "Importe" to 145f))
        val conceptLines = wrap(receipt.concept, regular, 10f, 340f).take(3)
        conceptLines.forEachIndexed { index, line -> text(canvas, line, 48f, 496f - index * 14f, regular, 10f, INK) }
        textRight(canvas, money(receipt.amount, receipt.currency), width - 48f, 496f, bold, 11f, INK)
        line(canvas, 36f, 455f, width - 36f, 455f, BORDER)

        fill(canvas, 330f, 392f, width - 366f, 48f, PALE_BLUE)
        text(canvas, "TOTAL RECIBIDO", 346f, 420f, bold, 9f, MUTED)
        textRight(canvas, money(receipt.amount, receipt.currency), width - 48f, 400f, bold, 18f, BLUE)

        text(canvas, "Este recibo acredita el pago registrado en SiGEP.", 42f, 92f, regular, 9f, MUTED)
        text(canvas, "No reemplaza al comprobante fiscal autorizado por ARCA.", 42f, 77f, bold, 9f, DARK_RED)
        footer(canvas, width)
    }

    fun renderInvoice(invoice: FiscalInvoice, documentWarning: String? = null): ByteArray {
        val errors = invoiceValidationErrors(invoice)
        require(errors.isEmpty()) { errors.joinToString("; ") }
        val qrUrl = requireNotNull(FiscalQrPayloadBuilder.build(invoice))
        return document { document, page, canvas ->
            val width = page.mediaBox.width
            val left = 24f
            val right = width - 24f
            val contentWidth = right - left
            val voucherLetter = voucherLetter(invoice.voucherType)
            val pointOfSale = requireNotNull(invoice.pointOfSale).toString().padStart(5, '0')
            val voucherNumber = requireNotNull(invoice.voucherNumber).toString().padStart(8, '0')

            officialBox(canvas, left, 24f, contentWidth, 794f)
            officialBox(canvas, left, 682f, contentWidth, 136f)
            line(canvas, width / 2f, 682f, width / 2f, 818f, OFFICIAL_BORDER)

            fun centeredLines(value: String, center: Float, y: Float, font: PDFont, size: Float, maxWidth: Float, gap: Float) {
                wrap(value, font, size, maxWidth).take(2).forEachIndexed { index, lineValue ->
                    textCentered(canvas, lineValue, center, y - index * gap, font, size, OFFICIAL_INK)
                }
            }

            fun field(label: String, value: String, x: Float, y: Float, maxWidth: Float = 250f) {
                text(canvas, "$label:", x, y, bold, 8.5f, OFFICIAL_INK)
                val labelWidth = bold.getStringWidth(safe("$label:")) / 1000f * 8.5f
                text(canvas, truncate(value, regular, 8.5f, maxWidth - labelWidth - 6f), x + labelWidth + 6f, y, regular, 8.5f, OFFICIAL_INK)
            }

            centeredLines(requireNotNull(settings.legalName), width / 4f, 786f, bold, 13f, 220f, 15f)
            centeredLines(requireNotNull(settings.businessAddress), width / 4f, 754f, regular, 8.5f, 220f, 12f)
            centeredLines(requireNotNull(settings.vatCondition), width / 4f, 726f, bold, 8.5f, 220f, 12f)

            text(canvas, "FACTURA", 315f, 790f, bold, 18f, OFFICIAL_INK)
            text(canvas, "$pointOfSale-$voucherNumber", 315f, 768f, bold, 11f, OFFICIAL_INK)
            text(canvas, "Fecha de Emision: ${invoice.issueDate.format(dateFormat)}", 315f, 748f, regular, 8.5f, OFFICIAL_INK)
            text(canvas, "CUIT: ${invoice.issuerCuit}", 315f, 728f, regular, 8.5f, OFFICIAL_INK)
            text(canvas, "Ingresos Brutos: ${safe(requireNotNull(settings.grossIncome))}", 315f, 712f, regular, 8.5f, OFFICIAL_INK)
            text(canvas, "Inicio de Actividades: ${requireNotNull(settings.activityStart).format(dateFormat)}", 315f, 696f, regular, 8.5f, OFFICIAL_INK)

            officialBox(canvas, width / 2f - 19f, 774f, 38f, 44f)
            textCentered(canvas, voucherLetter, width / 2f, 798f, bold, 22f, OFFICIAL_INK)
            textCentered(canvas, "COD. ${invoice.voucherType.toString().padStart(2, '0')}", width / 2f, 779f, bold, 5.5f, OFFICIAL_INK)
            textCentered(canvas, "ORIGINAL", width / 2f, 768f, regular, 5.5f, OFFICIAL_INK)

            fill(canvas, left, 650f, contentWidth, 21f, MOCK_PALE_RED)
            textCentered(canvas, documentWarning ?: "COMPROBANTE AUTORIZADO", width / 2f, 657f, bold, 8.5f, if (documentWarning != null) DARK_RED else OFFICIAL_INK)

            officialBox(canvas, left, 576f, contentWidth, 62f)
            field("Nombre", invoice.receiverName, 34f, 620f, 260f)
            field("Domicilio", invoice.receiverAddress, 34f, 603f, 260f)
            field("Cond. IVA", receiverVatConditionLabel(invoice.receiverVatConditionId), 34f, 586f, 260f)
            field("Documento", "${documentTypeLabel(invoice.receiverDocumentType)} ${invoice.receiverDocumentNumber}", 310f, 620f, 250f)
            field("Localidad", "No informado", 310f, 603f, 250f)
            field("Provincia", "No informado", 310f, 586f, 250f)

            val tableTop = 548f
            val tableBottom = 482f
            fill(canvas, left, tableTop, contentWidth, 22f, OFFICIAL_HEADER)
            officialBox(canvas, left, tableBottom, contentWidth, tableTop - tableBottom)
            val columns = listOf(60f, 270f, 65f, 75f, contentWidth - 60f - 270f - 65f - 75f)
            var cursor = left
            columns.dropLast(1).forEach { columnWidth ->
                cursor += columnWidth
                line(canvas, cursor, tableBottom, cursor, tableTop, OFFICIAL_BORDER)
            }
            val headers = listOf("Codigo", "Descripcion", "Cantidad", "P. Unitario", "Importe")
            cursor = left
            headers.forEachIndexed { index, header ->
                val columnWidth = columns[index]
                textCentered(canvas, header, cursor + columnWidth / 2f, tableTop + 7f, bold, 7.5f, OFFICIAL_INK)
                cursor += columnWidth
            }
            textCentered(canvas, "001", left + columns[0] / 2f, 520f, regular, 8f, OFFICIAL_INK)
            centeredLines(invoice.payment.concept, left + columns[0] + columns[1] / 2f, 526f, regular, 8f, columns[1] - 12f, 11f)
            textCentered(canvas, "1", left + columns[0] + columns[1] + columns[2] / 2f, 520f, regular, 8f, OFFICIAL_INK)
            textRight(canvas, amountOnly(invoice.totalAmount), left + columns[0] + columns[1] + columns[2] + columns[3] - 8f, 520f, regular, 8f, OFFICIAL_INK)
            textRight(canvas, amountOnly(invoice.totalAmount), right - 8f, 520f, regular, 8f, OFFICIAL_INK)

            officialBox(canvas, left, 143f, contentWidth, 70f)
            textRight(canvas, "Subtotal: ${amountOnly(invoice.totalAmount)}", right - 14f, 192f, regular, 8.5f, OFFICIAL_INK)
            textRight(canvas, "Dto/Recargo: ${amountOnly(BigDecimal.ZERO)}", right - 14f, 175f, regular, 8.5f, OFFICIAL_INK)
            textRight(canvas, "Total: ${currencyLabel(invoice.currency)} ${amountOnly(invoice.totalAmount)}", right - 14f, 154f, bold, 11f, OFFICIAL_INK)
            officialBox(canvas, left, 116f, contentWidth, 18f)

            val qrImage = qrImage(document, qrUrl)
            canvas.drawImage(qrImage, 38f, 37f, 72f, 72f)
            text(canvas, "ARCA", 124f, 94f, bold, 14f, OFFICIAL_INK)
            text(canvas, "Agencia de Recaudacion y Control Aduanero", 124f, 80f, regular, 6.5f, OFFICIAL_INK)
            text(
                canvas,
                when {
                    documentWarning?.startsWith("AMBIENTE MOCK") == true -> "Autorizacion simulada por adapter mock"
                    documentWarning != null -> "Autorizacion de homologacion ARCA"
                    else -> "Comprobante autorizado por ARCA"
                },
                124f,
                66f,
                bold,
                8f,
                OFFICIAL_INK
            )
            text(canvas, "CAE Nro.: ${invoice.authorizationCode}", 330f, 94f, bold, 9f, OFFICIAL_INK)
            text(canvas, "Fecha de Vto. de CAE: ${invoice.authorizationExpiresOn?.format(dateFormat)}", 330f, 78f, regular, 8.5f, OFFICIAL_INK)
            text(canvas, "Documento de prueba sin validez fiscal", 330f, 62f, regular, 7.5f, DARK_RED)
            text(canvas, "Generado por SiGEP", left, 31f, regular, 7f, MUTED)
            textRight(canvas, "Documento PDF", right, 31f, regular, 7f, MUTED)
        }
    }

    private fun document(draw: (PDDocument, PDPage, PDPageContentStream) -> Unit): ByteArray {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { canvas -> draw(document, page, canvas) }
            return ByteArrayOutputStream().use { output ->
                document.save(output)
                output.toByteArray()
            }
        }
    }

    private fun header(canvas: PDPageContentStream, width: Float, title: String, subtitle: String) {
        fill(canvas, 0f, 704f, width, 138f, BLUE)
        fill(canvas, 36f, 746f, 40f, 40f, Color.WHITE)
        textCentered(canvas, "S", 56f, 758f, bold, 24f, BLUE)
        text(canvas, "SiGEP", 88f, 765f, bold, 20f, Color.WHITE)
        text(canvas, "Gestion educativa", 88f, 748f, regular, 8f, Color.WHITE)
        textRight(canvas, title, width - 36f, 768f, bold, 18f, Color.WHITE)
        textRight(canvas, subtitle, width - 36f, 748f, regular, 8.5f, Color.WHITE)
    }

    private fun tableHeader(canvas: PDPageContentStream, x: Float, y: Float, width: Float, columns: List<Pair<String, Float>>) {
        fill(canvas, x, y, width, 24f, BLUE)
        var cursor = x + 12f
        columns.forEach { (label, columnWidth) ->
            text(canvas, label, cursor, y + 8f, bold, 8.5f, Color.WHITE)
            cursor += columnWidth
        }
    }

    private fun footer(canvas: PDPageContentStream, width: Float) {
        line(canvas, 36f, 48f, width - 36f, 48f, BORDER)
        text(canvas, "Generado por SiGEP", 36f, 31f, regular, 7.5f, MUTED)
        textRight(canvas, "Documento PDF", width - 36f, 31f, regular, 7.5f, MUTED)
    }

    private fun labelValue(canvas: PDPageContentStream, label: String, value: String, x: Float, y: Float, maxWidth: Float = 190f) {
        text(canvas, "$label:", x, y, bold, 8.5f, MUTED)
        val labelWidth = bold.getStringWidth(safe("$label:")) / 1000f * 8.5f
        val available = (maxWidth - labelWidth - 6f).coerceAtLeast(40f)
        text(canvas, truncate(value, regular, 9f, available), x + labelWidth + 6f, y, regular, 9f, INK)
    }

    private fun text(canvas: PDPageContentStream, value: String, x: Float, y: Float, font: PDFont, size: Float, color: Color) {
        canvas.beginText()
        canvas.setFont(font, size)
        canvas.setNonStrokingColor(color)
        canvas.setRenderingMode(RenderingMode.FILL)
        canvas.newLineAtOffset(x, y)
        canvas.showText(safe(value))
        canvas.endText()
    }

    private fun textRight(canvas: PDPageContentStream, value: String, right: Float, y: Float, font: PDFont, size: Float, color: Color) {
        val safeValue = safe(value)
        val width = font.getStringWidth(safeValue) / 1000f * size
        text(canvas, safeValue, right - width, y, font, size, color)
    }

    private fun textCentered(canvas: PDPageContentStream, value: String, center: Float, y: Float, font: PDFont, size: Float, color: Color) {
        val safeValue = safe(value)
        val width = font.getStringWidth(safeValue) / 1000f * size
        text(canvas, safeValue, center - width / 2f, y, font, size, color)
    }

    private fun fill(canvas: PDPageContentStream, x: Float, y: Float, width: Float, height: Float, color: Color) {
        canvas.setNonStrokingColor(color)
        canvas.addRect(x, y, width, height)
        canvas.fill()
    }

    private fun box(canvas: PDPageContentStream, x: Float, y: Float, width: Float, height: Float) {
        canvas.setStrokingColor(BORDER)
        canvas.setLineWidth(0.8f)
        canvas.addRect(x, y, width, height)
        canvas.stroke()
    }

    private fun officialBox(canvas: PDPageContentStream, x: Float, y: Float, width: Float, height: Float) {
        canvas.setStrokingColor(OFFICIAL_BORDER)
        canvas.setLineWidth(0.8f)
        canvas.addRect(x, y, width, height)
        canvas.stroke()
    }

    private fun line(canvas: PDPageContentStream, x1: Float, y1: Float, x2: Float, y2: Float, color: Color) {
        canvas.setStrokingColor(color)
        canvas.setLineWidth(0.8f)
        canvas.moveTo(x1, y1)
        canvas.lineTo(x2, y2)
        canvas.stroke()
    }

    private fun wrap(value: String, font: PDFont, size: Float, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        var line = ""
        safe(value).split(Regex("\\s+")).filter(String::isNotBlank).forEach { word ->
            val candidate = if (line.isBlank()) word else "$line $word"
            if (font.getStringWidth(candidate) / 1000f * size <= maxWidth) {
                line = candidate
            } else {
                if (line.isNotBlank()) result += line
                line = truncate(word, font, size, maxWidth)
            }
        }
        if (line.isNotBlank()) result += line
        return result.ifEmpty { listOf("") }
    }

    private fun truncate(value: String, font: PDFont, size: Float, maxWidth: Float): String {
        val safeValue = safe(value)
        if (font.getStringWidth(safeValue) / 1000f * size <= maxWidth) return safeValue
        var text = safeValue
        while (text.isNotEmpty() && font.getStringWidth("$text...") / 1000f * size > maxWidth) {
            text = text.dropLast(1)
        }
        return "$text..."
    }

    private fun safe(value: String): String = buildString {
        value.replace('\n', ' ').replace('\r', ' ').forEach { character ->
            val candidate = character.toString()
            append(if (runCatching { regular.encode(candidate) }.isSuccess) character else '?')
        }
    }

    private fun qrImage(document: PDDocument, url: String): PDImageXObject {
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 320, 320)
        return LosslessFactory.createFromImage(document, MatrixToImageWriter.toBufferedImage(matrix))
    }

    private fun money(amount: BigDecimal, currency: String): String = "${currencyLabel(currency)} ${amountFormat.format(amount)}"

    private fun amountOnly(amount: BigDecimal): String = amountFormat.format(amount)

    private fun currencyLabel(currency: String): String = when (currency) {
        "PES", "ARS" -> "$"
        else -> currency
    }

    private fun paymentMethodLabel(method: String?): String = when (method) {
        "CASH" -> "Efectivo"
        "BANK_TRANSFER" -> "Transferencia bancaria"
        "DEBIT_CARD" -> "Tarjeta de debito"
        "CREDIT_CARD" -> "Tarjeta de credito"
        "CHECK" -> "Cheque"
        else -> "No informado"
    }

    private fun voucherLetter(type: Int): String = when (type) {
        1, 2, 3, 4, 5 -> "A"
        6, 7, 8, 9, 10 -> "B"
        11, 12, 13, 15 -> "C"
        else -> "X"
    }

    private fun documentTypeLabel(type: Int): String = when (type) {
        80 -> "CUIT"
        86 -> "CUIL"
        96 -> "DNI"
        99 -> "CF"
        else -> type.toString()
    }

    private fun receiverVatConditionLabel(id: Int): String = when (id) {
        1 -> "1 - Resp. Inscripto"
        4 -> "4 - IVA Exento"
        5 -> "5 - Consumidor Final"
        6 -> "6 - Monotributo"
        13 -> "13 - Monotributo Social"
        16 -> "16 - Monotributo Promovido"
        else -> "Codigo $id"
    }

    private companion object {
        val BLUE = Color(35, 73, 145)
        val PALE_BLUE = Color(236, 243, 255)
        val LIGHT_RED = Color(255, 239, 239)
        val MOCK_PALE_RED = Color(255, 239, 239)
        val DARK_RED = Color(157, 31, 45)
        val INK = Color(31, 41, 55)
        val MUTED = Color(99, 115, 129)
        val BORDER = Color(210, 218, 230)
        val OFFICIAL_BORDER = Color(55, 55, 55)
        val OFFICIAL_HEADER = Color(225, 225, 225)
        val OFFICIAL_INK = Color(20, 20, 20)
    }
}
