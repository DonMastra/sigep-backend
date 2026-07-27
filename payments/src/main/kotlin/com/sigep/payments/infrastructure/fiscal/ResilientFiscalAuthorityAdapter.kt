package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.AuthorizedVoucherKey
import com.sigep.payments.application.gateway.FiscalAuthorityHealth
import com.sigep.payments.application.gateway.FiscalAuthorityPort
import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import com.sigep.payments.application.gateway.FiscalAuthorizationResult
import com.sigep.payments.application.gateway.FiscalEnvironment
import com.sigep.payments.application.gateway.FiscalPreDispatchException
import com.sigep.payments.application.gateway.FiscalReferenceData
import com.sigep.payments.application.gateway.VoucherSequenceKey
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

data class FiscalResilienceSettings(
    val maxConcurrentCalls: Int = 4,
    val maxWaitDuration: Duration = Duration.ZERO,
    val failureRateThreshold: Float = 50f,
    val minimumNumberOfCalls: Int = 5,
    val slidingWindowSize: Int = 10,
    val permittedCallsInHalfOpenState: Int = 2,
    val openStateDuration: Duration = Duration.ofSeconds(30),
    val referenceDataCacheTtl: Duration = Duration.ofHours(6),
    val referenceDataStaleIfError: Duration = Duration.ofHours(24)
) {
    init {
        require(maxConcurrentCalls > 0) { "billing.fiscal.resilience.max-concurrent-calls must be positive" }
        require(!maxWaitDuration.isNegative) { "billing.fiscal.resilience.max-wait-duration cannot be negative" }
        require(failureRateThreshold in 1f..100f) {
            "billing.fiscal.resilience.failure-rate-threshold must be between 1 and 100"
        }
        require(minimumNumberOfCalls > 0) {
            "billing.fiscal.resilience.minimum-number-of-calls must be positive"
        }
        require(slidingWindowSize >= minimumNumberOfCalls) {
            "billing.fiscal.resilience.sliding-window-size must be at least minimum-number-of-calls"
        }
        require(permittedCallsInHalfOpenState > 0) {
            "billing.fiscal.resilience.permitted-calls-in-half-open-state must be positive"
        }
        require(!openStateDuration.isZero && !openStateDuration.isNegative) {
            "billing.fiscal.resilience.open-state-duration must be positive"
        }
        require(!referenceDataCacheTtl.isNegative) {
            "billing.fiscal.reference-data-cache-ttl cannot be negative"
        }
        require(!referenceDataStaleIfError.isNegative) {
            "billing.fiscal.reference-data-stale-if-error cannot be negative"
        }
    }
}

/**
 * Decorates a remote fiscal adapter with isolation, fail-fast behavior,
 * low-cardinality metrics and a bounded cache for ARCA parametric data.
 *
 * No retry is performed here. In particular, an authorization that reached
 * the delegate and then failed remains ambiguous and must be reconciled.
 */
class ResilientFiscalAuthorityAdapter(
    private val delegate: FiscalAuthorityPort,
    private val providerName: String,
    private val fiscalEnvironment: FiscalEnvironment,
    private val settings: FiscalResilienceSettings,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock = Clock.systemUTC()
) : FiscalAuthorityPort {

    private val circuitBreaker = CircuitBreaker.of(
        "sigep-$providerName-wsfe",
        CircuitBreakerConfig.custom()
            .failureRateThreshold(settings.failureRateThreshold)
            .minimumNumberOfCalls(settings.minimumNumberOfCalls)
            .slidingWindowSize(settings.slidingWindowSize)
            .permittedNumberOfCallsInHalfOpenState(settings.permittedCallsInHalfOpenState)
            .waitDurationInOpenState(settings.openStateDuration)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build()
    )
    private val bulkhead = Bulkhead.of(
        "sigep-$providerName-wsfe",
        BulkheadConfig.custom()
            .maxConcurrentCalls(settings.maxConcurrentCalls)
            .maxWaitDuration(settings.maxWaitDuration)
            .build()
    )
    private val referenceDataLock = Any()

    @Volatile
    private var cachedReferenceData: CachedReferenceData? = null

    init {
        Gauge.builder(CIRCUIT_STATE_METRIC, circuitBreaker) { breaker -> breaker.state.metricValue() }
            .description("Fiscal provider circuit state: 0 closed, 1 half-open, 2 open, 3 other")
            .tags(*commonTags().toTypedArray())
            .register(meterRegistry)
    }

    override fun health(): FiscalAuthorityHealth = try {
        execute(HEALTH_OPERATION) {
            delegate.health().also { health ->
                if (!health.available) throw ProviderUnavailableException(health)
            }
        }
    } catch (exception: ProviderUnavailableException) {
        exception.health
    } catch (exception: Exception) {
        FiscalAuthorityHealth(
            provider = providerName,
            environment = fiscalEnvironment,
            configured = true,
            available = false,
            checkedAt = LocalDateTime.now(clock),
            message = exception.message ?: "Fiscal provider health check failed"
        )
    }

    override fun referenceData(): FiscalReferenceData {
        val now = clock.instant()
        cachedReferenceData?.takeIf { it.isFresh(now) }?.let { cached ->
            cacheMetric(CACHE_HIT)
            return cached.data
        }

        return synchronized(referenceDataLock) {
            val synchronizedNow = clock.instant()
            cachedReferenceData?.takeIf { it.isFresh(synchronizedNow) }?.let { cached ->
                cacheMetric(CACHE_HIT)
                return@synchronized cached.data
            }

            cacheMetric(CACHE_MISS)
            try {
                execute(REFERENCE_DATA_OPERATION) { delegate.referenceData() }.also { data ->
                    cachedReferenceData = CachedReferenceData(
                        data = data,
                        freshUntil = synchronizedNow.plus(settings.referenceDataCacheTtl),
                        staleUntil = synchronizedNow
                            .plus(settings.referenceDataCacheTtl)
                            .plus(settings.referenceDataStaleIfError)
                    )
                }
            } catch (exception: Exception) {
                cachedReferenceData?.takeIf { it.canServeStale(synchronizedNow) }?.let { cached ->
                    cacheMetric(CACHE_STALE)
                    return@synchronized cached.data
                }
                throw exception
            }
        }
    }

    override fun lastAuthorized(sequence: VoucherSequenceKey): Long =
        execute(LAST_AUTHORIZED_OPERATION) { delegate.lastAuthorized(sequence) }

    override fun authorize(request: FiscalAuthorizationRequest): FiscalAuthorizationResult =
        execute(AUTHORIZE_OPERATION) { delegate.authorize(request) }

    override fun consult(voucher: AuthorizedVoucherKey): FiscalAuthorizationResult? =
        execute(CONSULT_OPERATION) { delegate.consult(voucher) }

    private fun <T> execute(operation: String, action: () -> T): T {
        val startedAt = clock.instant()
        try {
            val result = bulkhead.executeSupplier {
                circuitBreaker.executeSupplier { action() }
            }
            callMetric(operation, OUTCOME_SUCCESS)
            return result
        } catch (exception: CallNotPermittedException) {
            callMetric(operation, OUTCOME_REJECTED)
            rejectionMetric(REJECTION_CIRCUIT_OPEN)
            throw FiscalPreDispatchException("Fiscal provider circuit is open", exception)
        } catch (exception: BulkheadFullException) {
            callMetric(operation, OUTCOME_REJECTED)
            rejectionMetric(REJECTION_BULKHEAD_FULL)
            throw FiscalPreDispatchException("Fiscal provider concurrency limit was reached", exception)
        } catch (exception: Exception) {
            callMetric(operation, OUTCOME_ERROR)
            throw exception
        } finally {
            val elapsedNanos = Duration.between(startedAt, clock.instant()).toNanos().coerceAtLeast(0)
            timer(operation).record(elapsedNanos, TimeUnit.NANOSECONDS)
        }
    }

    private fun timer(operation: String): Timer = Timer.builder(CALL_DURATION_METRIC)
        .description("Duration of fiscal provider calls")
        .tags(*(commonTags() + listOf("operation", operation)).toTypedArray())
        .register(meterRegistry)

    private fun callMetric(operation: String, outcome: String) {
        meterRegistry.counter(
            CALLS_METRIC,
            *(commonTags() + listOf("operation", operation, "outcome", outcome)).toTypedArray()
        ).increment()
    }

    private fun rejectionMetric(reason: String) {
        meterRegistry.counter(
            REJECTIONS_METRIC,
            *(commonTags() + listOf("reason", reason)).toTypedArray()
        ).increment()
    }

    private fun cacheMetric(outcome: String) {
        meterRegistry.counter(
            CACHE_METRIC,
            *(commonTags() + listOf("outcome", outcome)).toTypedArray()
        ).increment()
    }

    private fun commonTags(): List<String> = listOf(
        "provider", providerName,
        "environment", fiscalEnvironment.name.lowercase()
    )

    private data class CachedReferenceData(
        val data: FiscalReferenceData,
        val freshUntil: Instant,
        val staleUntil: Instant
    ) {
        fun isFresh(now: Instant): Boolean = now.isBefore(freshUntil)
        fun canServeStale(now: Instant): Boolean = now.isBefore(staleUntil)
    }

    private class ProviderUnavailableException(val health: FiscalAuthorityHealth) :
        RuntimeException(health.message ?: "Fiscal provider is unavailable")

    private fun CircuitBreaker.State.metricValue(): Double = when (this) {
        CircuitBreaker.State.CLOSED -> 0.0
        CircuitBreaker.State.HALF_OPEN -> 1.0
        CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN -> 2.0
        else -> 3.0
    }

    private companion object {
        const val HEALTH_OPERATION = "health"
        const val REFERENCE_DATA_OPERATION = "reference_data"
        const val LAST_AUTHORIZED_OPERATION = "last_authorized"
        const val AUTHORIZE_OPERATION = "authorize"
        const val CONSULT_OPERATION = "consult"
        const val OUTCOME_SUCCESS = "success"
        const val OUTCOME_ERROR = "error"
        const val OUTCOME_REJECTED = "rejected_before_dispatch"
        const val REJECTION_CIRCUIT_OPEN = "circuit_open"
        const val REJECTION_BULKHEAD_FULL = "bulkhead_full"
        const val CACHE_HIT = "hit"
        const val CACHE_MISS = "miss"
        const val CACHE_STALE = "stale_fallback"
        const val CALLS_METRIC = "sigep.billing.fiscal.calls"
        const val CALL_DURATION_METRIC = "sigep.billing.fiscal.call.duration"
        const val REJECTIONS_METRIC = "sigep.billing.fiscal.rejections"
        const val CACHE_METRIC = "sigep.billing.fiscal.reference.data.cache"
        const val CIRCUIT_STATE_METRIC = "sigep.billing.fiscal.circuit.state"
    }
}
