package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.AuthorizedVoucherKey
import com.sigep.payments.application.gateway.FiscalAuthorityHealth
import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import com.sigep.payments.application.gateway.FiscalAuthorizationResult
import com.sigep.payments.application.gateway.FiscalAuthorizationStatus
import com.sigep.payments.application.gateway.FiscalCatalogEntry
import com.sigep.payments.application.gateway.FiscalEnvironment
import com.sigep.payments.application.gateway.FiscalPreDispatchException
import com.sigep.payments.application.gateway.FiscalReferenceData
import com.sigep.payments.application.gateway.VoucherSequenceKey
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResilientFiscalAuthorityAdapterTest {

    @Test
    fun `reference data is cached and a bounded stale value is used on provider failure`() {
        val clock = MutableClock(Instant.parse("2026-07-21T12:00:00Z"))
        val registry = SimpleMeterRegistry()
        val calls = AtomicInteger()
        val delegate = StubFiscalAuthorityPort().apply {
            referenceDataHandler = {
                if (calls.incrementAndGet() == 1) referenceData(clock) else throw ProviderFailure()
            }
        }
        val adapter = resilient(
            delegate,
            registry,
            clock,
            FiscalResilienceSettings(
                referenceDataCacheTtl = Duration.ofHours(6),
                referenceDataStaleIfError = Duration.ofHours(24)
            )
        )

        val first = adapter.referenceData()
        assertEquals(first, adapter.referenceData())
        assertEquals(1, calls.get())

        clock.advance(Duration.ofHours(7))
        assertEquals(first, adapter.referenceData())
        assertEquals(2, calls.get())
        assertEquals(
            1.0,
            registry.counter(
                "sigep.billing.fiscal.reference.data.cache",
                "provider", "arca",
                "environment", "homologation",
                "outcome", "stale_fallback"
            ).count()
        )
    }

    @Test
    fun `circuit opens after remote failures and rejects the next call before dispatch`() {
        val registry = SimpleMeterRegistry()
        val calls = AtomicInteger()
        val delegate = StubFiscalAuthorityPort().apply {
            lastAuthorizedHandler = {
                calls.incrementAndGet()
                throw ProviderFailure()
            }
        }
        val adapter = resilient(
            delegate,
            registry,
            settings = FiscalResilienceSettings(
                failureRateThreshold = 50f,
                minimumNumberOfCalls = 2,
                slidingWindowSize = 2,
                openStateDuration = Duration.ofHours(1)
            )
        )

        repeat(2) {
            assertFailsWith<ProviderFailure> { adapter.lastAuthorized(sequence()) }
        }
        assertFailsWith<FiscalPreDispatchException> { adapter.lastAuthorized(sequence()) }

        assertEquals(2, calls.get())
        assertEquals(
            1.0,
            registry.counter(
                "sigep.billing.fiscal.rejections",
                "provider", "arca",
                "environment", "homologation",
                "reason", "circuit_open"
            ).count()
        )
    }

    @Test
    fun `bulkhead isolates concurrent calls without dispatching the rejected call`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val delegate = StubFiscalAuthorityPort().apply {
            lastAuthorizedHandler = {
                calls.incrementAndGet()
                entered.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
                0L
            }
        }
        val adapter = resilient(
            delegate,
            SimpleMeterRegistry(),
            settings = FiscalResilienceSettings(maxConcurrentCalls = 1, maxWaitDuration = Duration.ZERO)
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val first = executor.submit<Long> { adapter.lastAuthorized(sequence()) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))

            assertFailsWith<FiscalPreDispatchException> { adapter.lastAuthorized(sequence()) }
            assertEquals(1, calls.get())

            release.countDown()
            assertEquals(0L, first.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `authorization failure after delegate entry remains ambiguous and is never retried`() {
        val calls = AtomicInteger()
        val delegate = StubFiscalAuthorityPort().apply {
            authorizeHandler = {
                calls.incrementAndGet()
                throw ProviderFailure()
            }
        }
        val adapter = resilient(delegate, SimpleMeterRegistry())

        assertIs<ProviderFailure>(assertFailsWith<ProviderFailure> { adapter.authorize(authorizationRequest()) })
        assertEquals(1, calls.get())
    }

    private fun resilient(
        delegate: FiscalAuthorityPort,
        registry: SimpleMeterRegistry,
        clock: Clock = Clock.fixed(Instant.parse("2026-07-21T12:00:00Z"), ZoneOffset.UTC),
        settings: FiscalResilienceSettings = FiscalResilienceSettings()
    ) = ResilientFiscalAuthorityAdapter(
        delegate = delegate,
        providerName = "arca",
        fiscalEnvironment = FiscalEnvironment.HOMOLOGATION,
        settings = settings,
        meterRegistry = registry,
        clock = clock
    )

    private fun referenceData(clock: Clock) = FiscalReferenceData(
        voucherTypes = listOf(FiscalCatalogEntry("6", "Factura B")),
        documentTypes = listOf(FiscalCatalogEntry("96", "DNI")),
        receiverVatConditions = listOf(FiscalCatalogEntry("5", "Consumidor Final")),
        currencies = listOf(FiscalCatalogEntry("PES", "Pesos Argentinos")),
        retrievedAt = LocalDateTime.now(clock)
    )

    private fun sequence() = VoucherSequenceKey("30712345678", 1, 6)

    private fun authorizationRequest() = FiscalAuthorizationRequest(
        invoiceId = 1,
        idempotencyKey = "invoice-1",
        sequence = sequence(),
        voucherNumber = 1,
        concept = 1,
        receiverDocumentType = 96,
        receiverDocumentNumber = "30111222",
        receiverVatConditionId = 5,
        issueDate = LocalDate.of(2026, 7, 21),
        totalAmount = BigDecimal("100.00")
    )

    private class ProviderFailure : RuntimeException("provider failure")

    private class StubFiscalAuthorityPort : FiscalAuthorityPort {
        var referenceDataHandler: () -> FiscalReferenceData = { error("not configured") }
        var lastAuthorizedHandler: () -> Long = { 0L }
        var authorizeHandler: (FiscalAuthorizationRequest) -> FiscalAuthorizationResult = { request ->
            FiscalAuthorizationResult(
                status = FiscalAuthorizationStatus.APPROVED,
                voucher = AuthorizedVoucherKey(request.sequence, request.voucherNumber),
                processedAt = LocalDateTime.of(2026, 7, 21, 12, 0)
            )
        }

        override fun health() = FiscalAuthorityHealth(
            provider = "arca",
            environment = FiscalEnvironment.HOMOLOGATION,
            configured = true,
            available = true,
            checkedAt = LocalDateTime.of(2026, 7, 21, 12, 0)
        )

        override fun referenceData(): FiscalReferenceData = referenceDataHandler()
        override fun lastAuthorized(sequence: VoucherSequenceKey): Long = lastAuthorizedHandler()
        override fun authorize(request: FiscalAuthorizationRequest): FiscalAuthorizationResult = authorizeHandler(request)
        override fun consult(voucher: AuthorizedVoucherKey): FiscalAuthorizationResult? = null
    }

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
