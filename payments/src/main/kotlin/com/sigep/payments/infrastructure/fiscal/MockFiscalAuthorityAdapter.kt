package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.AuthorizedVoucherKey
import com.sigep.payments.application.gateway.FiscalAuthorityHealth
import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import com.sigep.payments.application.gateway.FiscalAuthorizationResult
import com.sigep.payments.application.gateway.FiscalAuthorizationStatus
import com.sigep.payments.application.gateway.FiscalEnvironment
import com.sigep.payments.application.gateway.FiscalCatalogEntry
import com.sigep.payments.application.gateway.FiscalObservation
import com.sigep.payments.application.gateway.FiscalReferenceData
import com.sigep.payments.application.gateway.VoucherSequenceKey
import com.sigep.payments.domain.service.FiscalAuthorizationValidator
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Deterministic adapter for local development and contract tests.
 *
 * This adapter deliberately keeps state in memory. It is not a substitute for
 * the persistent invoice/outbox/sequence state owned by the billing module and
 * must never be selected in production.
 */
class MockFiscalAuthorityAdapter(
    private val clock: Clock = Clock.systemUTC(),
    private val validator: FiscalAuthorizationValidator = FiscalAuthorizationValidator(),
    private val outcomePolicy: (FiscalAuthorizationRequest) -> MockFiscalOutcome = { MockFiscalOutcome.APPROVE }
) : FiscalAuthorityPort {

    private val sequences = ConcurrentHashMap<VoucherSequenceKey, Long>()
    private val results = ConcurrentHashMap<AuthorizedVoucherKey, FiscalAuthorizationResult>()
    private val idempotency = ConcurrentHashMap<String, IdempotentCall>()
    private val sequenceLocks = ConcurrentHashMap<VoucherSequenceKey, Any>()

    override fun health() = FiscalAuthorityHealth(
        provider = "mock",
        environment = FiscalEnvironment.MOCK,
        configured = true,
        available = true,
        checkedAt = LocalDateTime.now(clock),
        message = "Deterministic in-process fiscal authority mock"
    )

    override fun lastAuthorized(sequence: VoucherSequenceKey): Long = sequences[sequence] ?: 0L

    override fun referenceData() = FiscalReferenceData(
        voucherTypes = listOf(
            FiscalCatalogEntry("1", "Factura A"),
            FiscalCatalogEntry("6", "Factura B"),
            FiscalCatalogEntry("11", "Factura C")
        ),
        documentTypes = listOf(
            FiscalCatalogEntry("80", "CUIT"),
            FiscalCatalogEntry("86", "CUIL"),
            FiscalCatalogEntry("96", "DNI"),
            FiscalCatalogEntry("99", "Consumidor Final")
        ),
        receiverVatConditions = listOf(
            FiscalCatalogEntry("1", "IVA Responsable Inscripto"),
            FiscalCatalogEntry("4", "IVA Sujeto Exento"),
            FiscalCatalogEntry("5", "Consumidor Final"),
            FiscalCatalogEntry("6", "Responsable Monotributo"),
            FiscalCatalogEntry("13", "Monotributista Social"),
            FiscalCatalogEntry("16", "Monotributo Trabajador Independiente Promovido")
        ),
        currencies = listOf(FiscalCatalogEntry("PES", "Pesos Argentinos")),
        retrievedAt = LocalDateTime.now(clock)
    )

    override fun authorize(request: FiscalAuthorizationRequest): FiscalAuthorizationResult {
        validator.validate(request)

        idempotency[request.idempotencyKey]?.let { previous ->
            return if (previous.request == request) {
                previous.result
            } else {
                rejected(
                    request,
                    "MOCK_IDEMPOTENCY_CONFLICT",
                    "The idempotency key was already used with a different request"
                )
            }
        }

        val lock = sequenceLocks.computeIfAbsent(request.sequence) { Any() }
        return synchronized(lock) {
            idempotency[request.idempotencyKey]?.let { previous ->
                return@synchronized if (previous.request == request) previous.result else rejected(
                    request,
                    "MOCK_IDEMPOTENCY_CONFLICT",
                    "The idempotency key was already used with a different request"
                )
            }

            val expectedNumber = sequences[request.sequence]?.plus(1)
            val outcome = outcomePolicy(request)
            val result = when {
                expectedNumber != null && request.voucherNumber != expectedNumber -> rejected(
                    request,
                    "MOCK_SEQUENCE_ERROR",
                    "Expected voucher number $expectedNumber but received ${request.voucherNumber}"
                )
                outcome == MockFiscalOutcome.REJECT -> rejected(
                    request,
                    "MOCK_BUSINESS_REJECTION",
                    "Mock policy rejected the fiscal request"
                )
                outcome == MockFiscalOutcome.UNKNOWN -> {
                    val authoritativeResult = approved(request)
                    sequences[request.sequence] = request.voucherNumber
                    results[authoritativeResult.voucher] = authoritativeResult
                    unknown(request)
                }
                else -> approved(request)
            }

            idempotency[request.idempotencyKey] = IdempotentCall(request, result)
            if (result.status == FiscalAuthorizationStatus.APPROVED) {
                sequences[request.sequence] = request.voucherNumber
                results[result.voucher] = result
            }
            result
        }
    }

    override fun consult(voucher: AuthorizedVoucherKey): FiscalAuthorizationResult? = results[voucher]

    private fun approved(request: FiscalAuthorizationRequest): FiscalAuthorizationResult {
        val processedAt = LocalDateTime.now(clock)
        return FiscalAuthorizationResult(
            status = FiscalAuthorizationStatus.APPROVED,
            voucher = request.voucherKey(),
            authorizationCode = deterministicCae(request),
            authorizationExpiresOn = request.issueDate.plusDays(10),
            providerRequestId = "mock-${request.invoiceId}-${request.voucherNumber}",
            processedAt = processedAt
        )
    }

    private fun rejected(
        request: FiscalAuthorizationRequest,
        code: String,
        message: String
    ) = FiscalAuthorizationResult(
        status = FiscalAuthorizationStatus.REJECTED,
        voucher = request.voucherKey(),
        errors = listOf(FiscalObservation(code, message)),
        providerRequestId = "mock-${request.invoiceId}-${request.voucherNumber}",
        processedAt = LocalDateTime.now(clock)
    )

    private fun unknown(request: FiscalAuthorizationRequest) = FiscalAuthorizationResult(
        status = FiscalAuthorizationStatus.UNKNOWN,
        voucher = request.voucherKey(),
        observations = listOf(
            FiscalObservation(
                "MOCK_AMBIGUOUS_TIMEOUT",
                "The mock simulates a lost response; reconcile before retrying"
            )
        ),
        providerRequestId = "mock-${request.invoiceId}-${request.voucherNumber}",
        processedAt = LocalDateTime.now(clock)
    )

    private fun deterministicCae(request: FiscalAuthorizationRequest): String {
        val input = listOf(
            request.sequence.issuerCuit,
            request.sequence.pointOfSale,
            request.sequence.voucherType,
            request.voucherNumber,
            request.invoiceId
        ).joinToString(":")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        val positiveDigits = digest.joinToString("") { byte -> "%03d".format(byte.toInt() and 0xff) }
        return positiveDigits.take(CAE_LENGTH)
    }

    private fun FiscalAuthorizationRequest.voucherKey() = AuthorizedVoucherKey(sequence, voucherNumber)

    private data class IdempotentCall(
        val request: FiscalAuthorizationRequest,
        val result: FiscalAuthorizationResult
    )

    private companion object {
        const val CAE_LENGTH = 14
    }
}

enum class MockFiscalOutcome {
    APPROVE,
    REJECT,
    UNKNOWN
}
