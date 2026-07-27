package com.sigep.payments.application.service

import com.sigep.payments.application.dto.ConfirmPaymentRequest
import com.sigep.payments.application.dto.CreateFiscalInvoiceRequest
import com.sigep.payments.domain.model.FiscalInvoice
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class BillingIssuerSettings(
    val cuit: String?,
    val pointOfSale: Int?
)

class FiscalInvoicePreflightService {

    fun evaluate(invoice: FiscalInvoice): List<String> = buildList {
        if (invoice.issuerCuit?.matches(CUIT_PATTERN) != true) {
            add("Falta configurar BILLING_ISSUER_CUIT con 11 digitos")
        }
        if (invoice.pointOfSale == null || invoice.pointOfSale !in 1..99_998) {
            add("Falta configurar BILLING_ISSUER_POINT_OF_SALE")
        }
        if (invoice.voucherType <= 0) {
            add("El tipo de comprobante debe ser positivo")
        }
        if (invoice.concept !in 1..3) {
            add("El concepto WSFE debe ser 1, 2 o 3")
        }
        if (!invoice.receiverDocumentNumber.matches(DOCUMENT_PATTERN)) {
            add("El documento del receptor debe contener solo digitos")
        }
        if (invoice.receiverAddress.isBlank()) {
            add("El domicilio del receptor es obligatorio para emitir el comprobante")
        }
        if (invoice.receiverVatConditionId <= 0) {
            add("La condicion IVA del receptor es obligatoria")
        }
        if (!invoice.currency.matches(CURRENCY_PATTERN)) {
            add("La moneda WSFE debe tener tres letras mayusculas")
        }
        if (invoice.exchangeRate <= BigDecimal.ZERO) {
            add("La cotizacion debe ser positiva")
        }

        if (invoice.concept == PRODUCT_CONCEPT) {
            if (invoice.serviceFrom != null || invoice.serviceTo != null || invoice.paymentDueDate != null) {
                add("Las fechas de servicio no corresponden a un comprobante solo de productos")
            }
        } else {
            if (invoice.serviceFrom == null || invoice.serviceTo == null || invoice.paymentDueDate == null) {
                add("Las fechas desde, hasta y vencimiento son obligatorias para servicios")
            } else if (invoice.serviceTo.isBefore(invoice.serviceFrom)) {
                add("La fecha hasta del servicio no puede ser anterior a la fecha desde")
            }
        }

        val components = money(invoice.nonTaxedAmount)
            .add(money(invoice.netAmount))
            .add(money(invoice.exemptAmount))
            .add(money(invoice.vatAmount))
            .add(money(invoice.otherTaxesAmount))
        if (money(invoice.totalAmount).compareTo(components) != 0) {
            add("El total debe coincidir con no gravado + neto + exento + IVA + tributos")
        }
        if (money(invoice.totalAmount).compareTo(money(invoice.payment.amount)) != 0) {
            add("El total fiscal debe coincidir con el monto del pago")
        }
        if (money(invoice.totalAmount) <= BigDecimal.ZERO) {
            add("El total fiscal debe ser positivo")
        }
        if (invoice.vatSubtotals.map { it.id }.distinct().size != invoice.vatSubtotals.size) {
            add("Las alicuotas IVA no pueden repetirse")
        }
        if (invoice.vatSubtotals.any { it.id <= 0 || money(it.baseAmount) <= BigDecimal.ZERO || money(it.amount) < BigDecimal.ZERO }) {
            add("Cada alicuota IVA debe tener codigo positivo, base positiva e importe no negativo")
        }
        if ((money(invoice.netAmount) > BigDecimal.ZERO || money(invoice.vatAmount) > BigDecimal.ZERO) && invoice.vatSubtotals.isEmpty()) {
            add("El neto gravado y el IVA requieren el desglose de alicuotas WSFE")
        }
        if (invoice.vatSubtotals.isNotEmpty()) {
            val detailBase = invoice.vatSubtotals.fold(BigDecimal.ZERO) { total, item -> total.add(money(item.baseAmount)) }
            val detailVat = invoice.vatSubtotals.fold(BigDecimal.ZERO) { total, item -> total.add(money(item.amount)) }
            if (money(invoice.netAmount).compareTo(detailBase) != 0) {
                add("La suma de bases IVA debe coincidir con el neto gravado")
            }
            if (money(invoice.vatAmount).compareTo(detailVat) != 0) {
                add("La suma del detalle IVA debe coincidir con el IVA total")
            }
        }
        if (invoice.taxes.any {
                it.id <= 0 || it.description.isBlank() || it.description.length > 200 ||
                    money(it.baseAmount) < BigDecimal.ZERO || it.rate < BigDecimal.ZERO || money(it.amount) < BigDecimal.ZERO
            }) {
            add("Cada tributo debe tener codigo y descripcion, con base, alicuota e importe no negativos")
        }
        if (invoice.taxes.map { it.id to it.description.trim() }.distinct().size != invoice.taxes.size) {
            add("Los tributos no pueden repetirse")
        }
        if (money(invoice.otherTaxesAmount) > BigDecimal.ZERO && invoice.taxes.isEmpty()) {
            add("El importe de tributos requiere su desglose WSFE")
        }
        val detailTaxes = invoice.taxes.fold(BigDecimal.ZERO) { total, item -> total.add(money(item.amount)) }
        if (money(invoice.otherTaxesAmount).compareTo(detailTaxes) != 0) {
            add("La suma del detalle de tributos debe coincidir con el total de tributos")
        }
    }

    private fun money(value: BigDecimal): BigDecimal = value.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN)

    private companion object {
        const val PRODUCT_CONCEPT = 1
        const val MONEY_SCALE = 2
        val CUIT_PATTERN = Regex("^[0-9]{11}$")
        val DOCUMENT_PATTERN = Regex("^[0-9]{1,20}$")
        val CURRENCY_PATTERN = Regex("^[A-Z]{3}$")
    }
}

internal object BillingFingerprint {
    fun payment(request: com.sigep.payments.application.dto.CreatePaymentRequest): String = sha256(
        listOf(
            request.studentId,
            request.amount.toPlainString(),
            request.currency,
            request.concept.trim(),
            request.dueDate,
            request.externalReference?.trim(),
            request.notes?.trim()
        ).joinToString("|")
    )

    fun confirmation(request: ConfirmPaymentRequest): String = sha256(
        listOf(
            request.paymentDate,
            request.paymentMethod,
            request.payerName.trim()
        ).joinToString("|")
    )

    fun invoice(request: CreateFiscalInvoiceRequest): String = sha256(
        listOf(
            request.voucherType,
            request.concept,
            request.receiverName.trim(),
            request.receiverAddress.trim(),
            request.receiverDocumentType,
            request.receiverDocumentNumber,
            request.receiverVatConditionId,
            request.issueDate,
            request.serviceFrom,
            request.serviceTo,
            request.paymentDueDate,
            request.currency,
            request.exchangeRate.toPlainString(),
            request.nonTaxedAmount.toPlainString(),
            request.netAmount.toPlainString(),
            request.exemptAmount.toPlainString(),
            request.vatAmount.toPlainString(),
            request.otherTaxesAmount.toPlainString(),
            request.vatSubtotals
                .sortedBy { it.id }
                .joinToString(";") { "${it.id},${it.baseAmount.toPlainString()},${it.amount.toPlainString()}" },
            request.taxes
                .sortedWith(compareBy({ it.id }, { it.description.trim() }))
                .joinToString(";") {
                    "${it.id},${it.description.trim()},${it.baseAmount.toPlainString()},${it.rate.toPlainString()},${it.amount.toPlainString()}"
                }
        ).joinToString("|")
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
