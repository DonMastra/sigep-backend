package com.sigep.payments.domain.service

import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import java.math.BigDecimal
import java.math.RoundingMode

class FiscalAuthorizationValidator {

    fun validate(request: FiscalAuthorizationRequest) {
        require(request.invoiceId > 0) { "invoiceId must be positive" }
        require(request.idempotencyKey.isNotBlank()) { "idempotencyKey is required" }
        require(request.idempotencyKey.length <= 128) { "idempotencyKey must have at most 128 characters" }
        require(request.sequence.issuerCuit.matches(CUIT_PATTERN)) { "issuerCuit must contain 11 digits" }
        require(request.sequence.pointOfSale in 1..99_999) { "pointOfSale is outside the WSFE range" }
        require(request.sequence.voucherType > 0) { "voucherType must be positive" }
        require(request.voucherNumber > 0) { "voucherNumber must be positive" }
        require(request.concept in 1..3) { "concept must be 1, 2 or 3" }
        require(request.receiverDocumentType >= 0) { "receiverDocumentType cannot be negative" }
        require(request.receiverDocumentNumber.matches(DOCUMENT_PATTERN)) {
            "receiverDocumentNumber must contain digits only"
        }
        require(request.receiverVatConditionId > 0) { "receiverVatConditionId is required" }
        require(request.currency.matches(CURRENCY_PATTERN)) { "currency must contain three uppercase letters" }
        require(request.exchangeRate > BigDecimal.ZERO) { "exchangeRate must be positive" }

        validateServiceDates(request)
        validateAmounts(request)
    }

    private fun validateServiceDates(request: FiscalAuthorizationRequest) {
        val hasAnyServiceDate = request.serviceFrom != null || request.serviceTo != null || request.paymentDueDate != null
        if (request.concept == PRODUCT_CONCEPT) {
            require(!hasAnyServiceDate) { "service dates are not allowed for product-only vouchers" }
            return
        }

        val serviceFrom = requireNotNull(request.serviceFrom) {
            "serviceFrom, serviceTo and paymentDueDate are required for service vouchers"
        }
        val serviceTo = requireNotNull(request.serviceTo) {
            "serviceFrom, serviceTo and paymentDueDate are required for service vouchers"
        }
        requireNotNull(request.paymentDueDate) {
            "serviceFrom, serviceTo and paymentDueDate are required for service vouchers"
        }
        require(!serviceTo.isBefore(serviceFrom)) { "serviceTo cannot be before serviceFrom" }
    }

    private fun validateAmounts(request: FiscalAuthorizationRequest) {
        val amounts = listOf(
            request.totalAmount,
            request.nonTaxedAmount,
            request.netAmount,
            request.exemptAmount,
            request.vatAmount,
            request.otherTaxesAmount
        )
        require(amounts.all { it >= BigDecimal.ZERO }) { "fiscal amounts cannot be negative" }
        require(money(request.totalAmount) > BigDecimal.ZERO) { "totalAmount must be positive" }

        val components = money(request.nonTaxedAmount)
            .add(money(request.netAmount))
            .add(money(request.exemptAmount))
            .add(money(request.vatAmount))
            .add(money(request.otherTaxesAmount))

        require(money(request.totalAmount).compareTo(components) == 0) {
            "totalAmount must equal nonTaxed + net + exempt + VAT + other taxes using HALF_EVEN"
        }

        require(request.vatSubtotals.map { it.id }.distinct().size == request.vatSubtotals.size) {
            "VAT aliquot ids cannot be duplicated"
        }
        require(request.vatSubtotals.all {
            it.id > 0 && money(it.baseAmount) > BigDecimal.ZERO && money(it.amount) >= BigDecimal.ZERO
        }) { "VAT aliquots require a positive id and base, and a non-negative amount" }
        require(
            (money(request.netAmount).compareTo(BigDecimal.ZERO) == 0 &&
                money(request.vatAmount).compareTo(BigDecimal.ZERO) == 0) || request.vatSubtotals.isNotEmpty()
        ) {
            "taxable net or VAT amounts require an aliquot breakdown"
        }
        if (request.vatSubtotals.isNotEmpty()) {
            val baseTotal = request.vatSubtotals.fold(BigDecimal.ZERO) { total, item -> total.add(money(item.baseAmount)) }
            val vatTotal = request.vatSubtotals.fold(BigDecimal.ZERO) { total, item -> total.add(money(item.amount)) }
            require(money(request.netAmount).compareTo(baseTotal) == 0) {
                "VAT base breakdown must equal netAmount"
            }
            require(money(request.vatAmount).compareTo(vatTotal) == 0) {
                "VAT amount breakdown must equal vatAmount"
            }
        }

        require(request.taxes.all {
            it.id > 0 && it.description.isNotBlank() && it.description.length <= 200 &&
                money(it.baseAmount) >= BigDecimal.ZERO && it.rate >= BigDecimal.ZERO && money(it.amount) >= BigDecimal.ZERO
        }) { "tax details require valid ids, descriptions and non-negative amounts" }
        require(request.taxes.map { it.id to it.description.trim() }.distinct().size == request.taxes.size) {
            "tax details cannot be duplicated"
        }
        require(money(request.otherTaxesAmount).compareTo(BigDecimal.ZERO) == 0 || request.taxes.isNotEmpty()) {
            "otherTaxesAmount requires a tax breakdown"
        }
        val taxTotal = request.taxes.fold(BigDecimal.ZERO) { total, item -> total.add(money(item.amount)) }
        require(money(request.otherTaxesAmount).compareTo(taxTotal) == 0) {
            "tax breakdown must equal otherTaxesAmount"
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
