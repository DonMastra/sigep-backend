package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.AuthorizedVoucherKey
import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import com.sigep.payments.application.gateway.FiscalAuthorizationStatus
import com.sigep.payments.application.gateway.VoucherSequenceKey
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MockFiscalAuthorityAdapterTest {

    private val sequence = VoucherSequenceKey(
        issuerCuit = "30712345678",
        pointOfSale = 3,
        voucherType = 6
    )
    private val clock = Clock.fixed(Instant.parse("2026-07-21T15:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `approves and exposes the voucher for reconciliation`() {
        val adapter = MockFiscalAuthorityAdapter(clock = clock)

        val result = adapter.authorize(request())

        assertEquals(FiscalAuthorizationStatus.APPROVED, result.status)
        assertEquals(14, result.authorizationCode?.length)
        assertEquals(LocalDate.of(2026, 7, 31), result.authorizationExpiresOn)
        assertEquals(1L, adapter.lastAuthorized(sequence))
        assertEquals(result, adapter.consult(AuthorizedVoucherKey(sequence, 1L)))
    }

    @Test
    fun `exposes deterministic fiscal reference catalogs`() {
        val catalogs = MockFiscalAuthorityAdapter(clock = clock).referenceData()

        assertEquals(listOf("1", "6", "11"), catalogs.voucherTypes.map { it.id })
        assertEquals("CUIT", catalogs.documentTypes.first { it.id == "80" }.description)
        assertEquals("Consumidor Final", catalogs.receiverVatConditions.first { it.id == "5" }.description)
        assertEquals("PES", catalogs.currencies.single().id)
    }

    @Test
    fun `same idempotency key and payload returns the original result`() {
        val adapter = MockFiscalAuthorityAdapter(clock = clock)
        val request = request()

        val first = adapter.authorize(request)
        val repeated = adapter.authorize(request)

        assertEquals(first, repeated)
        assertEquals(1L, adapter.lastAuthorized(sequence))
    }

    @Test
    fun `same idempotency key with a different payload is rejected`() {
        val adapter = MockFiscalAuthorityAdapter(clock = clock)
        adapter.authorize(request())

        val conflict = adapter.authorize(request().copy(invoiceId = 99L))

        assertEquals(FiscalAuthorizationStatus.REJECTED, conflict.status)
        assertEquals("MOCK_IDEMPOTENCY_CONFLICT", conflict.errors.single().code)
        assertEquals(1L, adapter.lastAuthorized(sequence))
    }

    @Test
    fun `out of sequence voucher is rejected without advancing numbering`() {
        val adapter = MockFiscalAuthorityAdapter(clock = clock)
        adapter.authorize(request())

        val result = adapter.authorize(
            request().copy(
                invoiceId = 11L,
                idempotencyKey = "invoice-11-v1",
                voucherNumber = 3L
            )
        )

        assertEquals(FiscalAuthorizationStatus.REJECTED, result.status)
        assertEquals("MOCK_SEQUENCE_ERROR", result.errors.single().code)
        assertEquals(1L, adapter.lastAuthorized(sequence))
    }

    @Test
    fun `ambiguous outcome is recovered by consulting the authoritative voucher`() {
        val adapter = MockFiscalAuthorityAdapter(
            clock = clock,
            outcomePolicy = { MockFiscalOutcome.UNKNOWN }
        )

        val result = adapter.authorize(request())

        assertEquals(FiscalAuthorizationStatus.UNKNOWN, result.status)
        assertEquals(1L, adapter.lastAuthorized(sequence))
        assertEquals(
            FiscalAuthorizationStatus.APPROVED,
            adapter.consult(AuthorizedVoucherKey(sequence, 1L))?.status
        )
        assertNotNull(result.observations.singleOrNull())
    }

    @Test
    fun `requires fiscal components to equal total with half even rounding`() {
        val adapter = MockFiscalAuthorityAdapter(clock = clock)

        assertFailsWith<IllegalArgumentException> {
            adapter.authorize(request().copy(totalAmount = BigDecimal("100.01")))
        }
    }

    private fun request() = FiscalAuthorizationRequest(
        invoiceId = 10L,
        idempotencyKey = "invoice-10-v1",
        sequence = sequence,
        voucherNumber = 1L,
        concept = 2,
        receiverDocumentType = 96,
        receiverDocumentNumber = "30123456",
        receiverVatConditionId = 5,
        issueDate = LocalDate.of(2026, 7, 21),
        serviceFrom = LocalDate.of(2026, 7, 1),
        serviceTo = LocalDate.of(2026, 7, 31),
        paymentDueDate = LocalDate.of(2026, 7, 21),
        totalAmount = BigDecimal("100.00"),
        exemptAmount = BigDecimal("100.00")
    )
}
