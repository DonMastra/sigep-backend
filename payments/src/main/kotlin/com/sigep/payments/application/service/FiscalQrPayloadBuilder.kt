package com.sigep.payments.application.service

import com.sigep.payments.domain.model.FiscalInvoice
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Builds the version 1 ARCA verification URL for an authorized CAE voucher. */
internal object FiscalQrPayloadBuilder {

    fun build(invoice: FiscalInvoice): String? {
        val issuerCuit = invoice.issuerCuit?.takeIf { it.matches(ELEVEN_DIGITS) } ?: return null
        val pointOfSale = invoice.pointOfSale ?: return null
        val voucherNumber = invoice.voucherNumber ?: return null
        val authorizationCode = invoice.authorizationCode?.takeIf { it.matches(FOURTEEN_DIGITS) } ?: return null
        val json = buildString {
            append("{\"ver\":1")
            append(",\"fecha\":\"").append(invoice.issueDate).append('"')
            append(",\"cuit\":").append(issuerCuit)
            append(",\"ptoVta\":").append(pointOfSale)
            append(",\"tipoCmp\":").append(invoice.voucherType)
            append(",\"nroCmp\":").append(voucherNumber)
            append(",\"importe\":").append(invoice.totalAmount.stripTrailingZeros().toPlainString())
            append(",\"moneda\":\"").append(invoice.currency).append('"')
            append(",\"ctz\":").append(invoice.exchangeRate.stripTrailingZeros().toPlainString())
            append(",\"tipoDocRec\":").append(invoice.receiverDocumentType)
            append(",\"nroDocRec\":").append(invoice.receiverDocumentNumber)
            append(",\"tipoCodAut\":\"E\"")
            append(",\"codAut\":").append(authorizationCode)
            append('}')
        }
        val payload = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        return "$ARCA_QR_URL?${QUERY_PARAMETER}=$payload"
    }

    private const val ARCA_QR_URL = "https://www.arca.gob.ar/fe/qr/"
    private const val QUERY_PARAMETER = "p"
    private val ELEVEN_DIGITS = Regex("^[0-9]{11}$")
    private val FOURTEEN_DIGITS = Regex("^[0-9]{14}$")
}
