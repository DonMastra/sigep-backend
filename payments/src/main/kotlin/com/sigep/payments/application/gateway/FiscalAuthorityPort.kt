package com.sigep.payments.application.gateway

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Anti-corruption boundary between SiGEP billing and a fiscal authority.
 *
 * SOAP, WSAA credentials and ARCA-specific transport objects must remain behind
 * this port. Mock, homologation and production adapters share this contract.
 */
interface FiscalAuthorityPort {
    fun health(): FiscalAuthorityHealth

    fun referenceData(): FiscalReferenceData

    fun lastAuthorized(sequence: VoucherSequenceKey): Long

    fun authorize(request: FiscalAuthorizationRequest): FiscalAuthorizationResult

    fun consult(voucher: AuthorizedVoucherKey): FiscalAuthorizationResult?
}

/**
 * The provider call was rejected locally before any fiscal request was sent.
 *
 * Unlike a transport failure after dispatch, this condition is safe to retry
 * through the billing outbox because ARCA could not have authorized anything.
 */
class FiscalPreDispatchException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

data class FiscalCatalogEntry(
    val id: String,
    val description: String,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null
)

data class FiscalReferenceData(
    val voucherTypes: List<FiscalCatalogEntry>,
    val documentTypes: List<FiscalCatalogEntry>,
    val receiverVatConditions: List<FiscalCatalogEntry>,
    val currencies: List<FiscalCatalogEntry>,
    val retrievedAt: LocalDateTime
)

enum class FiscalEnvironment {
    MOCK,
    HOMOLOGATION,
    PRODUCTION
}

data class FiscalAuthorityHealth(
    val provider: String,
    val environment: FiscalEnvironment,
    val configured: Boolean,
    val available: Boolean,
    val checkedAt: LocalDateTime,
    val message: String? = null
)

data class VoucherSequenceKey(
    val issuerCuit: String,
    val pointOfSale: Int,
    val voucherType: Int
)

data class AuthorizedVoucherKey(
    val sequence: VoucherSequenceKey,
    val voucherNumber: Long
)

data class FiscalAuthorizationRequest(
    val invoiceId: Long,
    val idempotencyKey: String,
    val sequence: VoucherSequenceKey,
    val voucherNumber: Long,
    val concept: Int,
    val receiverDocumentType: Int,
    val receiverDocumentNumber: String,
    val receiverVatConditionId: Int,
    val issueDate: LocalDate,
    val serviceFrom: LocalDate? = null,
    val serviceTo: LocalDate? = null,
    val paymentDueDate: LocalDate? = null,
    val currency: String = "PES",
    val exchangeRate: BigDecimal = BigDecimal.ONE,
    val totalAmount: BigDecimal,
    val nonTaxedAmount: BigDecimal = BigDecimal.ZERO,
    val netAmount: BigDecimal = BigDecimal.ZERO,
    val exemptAmount: BigDecimal = BigDecimal.ZERO,
    val vatAmount: BigDecimal = BigDecimal.ZERO,
    val otherTaxesAmount: BigDecimal = BigDecimal.ZERO,
    val vatSubtotals: List<FiscalVatSubtotalRequest> = emptyList(),
    val taxes: List<FiscalOtherTaxRequest> = emptyList()
)

data class FiscalVatSubtotalRequest(
    val id: Int,
    val baseAmount: BigDecimal,
    val amount: BigDecimal
)

data class FiscalOtherTaxRequest(
    val id: Int,
    val description: String,
    val baseAmount: BigDecimal,
    val rate: BigDecimal,
    val amount: BigDecimal
)

enum class FiscalAuthorizationStatus {
    APPROVED,
    REJECTED,
    UNKNOWN
}

data class FiscalObservation(
    val code: String,
    val message: String
)

data class FiscalAuthorizationResult(
    val status: FiscalAuthorizationStatus,
    val voucher: AuthorizedVoucherKey,
    val authorizationCode: String? = null,
    val authorizationExpiresOn: LocalDate? = null,
    val observations: List<FiscalObservation> = emptyList(),
    val errors: List<FiscalObservation> = emptyList(),
    val providerRequestId: String? = null,
    val processedAt: LocalDateTime
)
