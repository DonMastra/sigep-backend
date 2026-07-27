package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.AuthorizedVoucherKey
import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import com.sigep.payments.application.gateway.FiscalAuthorizationStatus
import com.sigep.payments.application.gateway.FiscalEnvironment
import com.sigep.payments.application.gateway.FiscalOtherTaxRequest
import com.sigep.payments.application.gateway.FiscalPreDispatchException
import com.sigep.payments.application.gateway.FiscalVatSubtotalRequest
import com.sigep.payments.application.gateway.VoucherSequenceKey
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Opt-in contract smoke against DonMastra/mock-billing-service.
 *
 * Run with SIGEP_EXTERNAL_MOCK_SMOKE=true while the mock is listening on
 * BILLING_MOCK_SERVICE_BASE_URL (defaults to http://localhost:8091).
 */
class ExternalMockFiscalAuthorityAdapterSmokeTest {

    @Test
    fun `external mock exercises WSAA catalogs authorization and consultation`() {
        assumeTrue(System.getenv("SIGEP_EXTERNAL_MOCK_SMOKE").equals("true", ignoreCase = true))
        val baseUrl = System.getenv("BILLING_MOCK_SERVICE_BASE_URL") ?: "http://localhost:8091"
        resetMock(baseUrl)
        val adapter = externalAdapter(baseUrl)
        val sequence = VoucherSequenceKey("30712345678", 1, 11)

        assertTrue(adapter.health().available)
        val catalogs = adapter.referenceData()
        assertTrue(catalogs.voucherTypes.any { it.id == "11" })
        assertTrue(catalogs.receiverVatConditions.any { it.id == "5" })
        assertEquals(0L, adapter.lastAuthorized(sequence))

        val authorization = adapter.authorize(
            FiscalAuthorizationRequest(
                invoiceId = 999,
                idempotencyKey = "external-mock-smoke-999",
                sequence = sequence,
                voucherNumber = 1,
                concept = 1,
                receiverDocumentType = 96,
                receiverDocumentNumber = "30111222",
                receiverVatConditionId = 5,
                issueDate = LocalDate.now(),
                totalAmount = BigDecimal("131.00"),
                netAmount = BigDecimal("100.00"),
                vatAmount = BigDecimal("21.00"),
                otherTaxesAmount = BigDecimal("10.00"),
                vatSubtotals = listOf(
                    FiscalVatSubtotalRequest(5, BigDecimal("100.00"), BigDecimal("21.00"))
                ),
                taxes = listOf(
                    FiscalOtherTaxRequest(
                        id = 99,
                        description = "Tasa municipal",
                        baseAmount = BigDecimal("100.00"),
                        rate = BigDecimal("10.00"),
                        amount = BigDecimal("10.00")
                    )
                )
            )
        )

        assertEquals(FiscalAuthorizationStatus.APPROVED, authorization.status)
        assertNotNull(authorization.authorizationCode)
        assertEquals(1L, adapter.lastAuthorized(sequence))
        assertNotNull(adapter.consult(AuthorizedVoucherKey(sequence, 1)))
    }

    @Test
    fun `external mock HTTP 503 opens the circuit and then rejects before dispatch`() {
        assumeTrue(System.getenv("SIGEP_EXTERNAL_MOCK_SMOKE").equals("true", ignoreCase = true))
        val baseUrl = System.getenv("BILLING_MOCK_SERVICE_BASE_URL") ?: "http://localhost:8091"
        resetMock(baseUrl)
        val resilient = ResilientFiscalAuthorityAdapter(
            delegate = externalAdapter(baseUrl),
            providerName = "mock-service",
            fiscalEnvironment = FiscalEnvironment.MOCK,
            settings = FiscalResilienceSettings(
                failureRateThreshold = 50f,
                minimumNumberOfCalls = 2,
                slidingWindowSize = 2,
                openStateDuration = Duration.ofHours(1)
            ),
            meterRegistry = SimpleMeterRegistry()
        )
        val sequence = VoucherSequenceKey("30712345678", 1, 11)

        setScenario(baseUrl, "SERVICE_UNAVAILABLE")
        try {
            repeat(2) {
                assertFailsWith<ArcaProtocolException> { resilient.lastAuthorized(sequence) }
            }
            assertFailsWith<FiscalPreDispatchException> { resilient.lastAuthorized(sequence) }
        } finally {
            resetMock(baseUrl)
        }
    }

    private fun externalAdapter(baseUrl: String): ArcaFiscalAuthorityAdapter {
        val settings = ArcaFiscalSettings(
            environment = FiscalEnvironment.MOCK,
            issuerCuit = "30712345678",
            wsaaEndpoint = URI.create("${baseUrl.trimEnd('/')}/ws/services/LoginCms"),
            wsfeEndpoint = URI.create("${baseUrl.trimEnd('/')}/wsfev1/service.asmx"),
            keyStorePath = Path.of("."),
            keyStorePassword = charArrayOf(),
            keyAlias = null,
            connectTimeout = Duration.ofSeconds(2),
            requestTimeout = Duration.ofSeconds(5),
            ticketRefreshSkew = Duration.ofMinutes(5)
        )
        return ArcaFiscalAuthorityAdapter.createExternalMock(settings)
    }

    private fun setScenario(baseUrl: String, scenario: String) {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("${baseUrl.trimEnd('/')}/api/mock/scenarios"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"scenario":"$scenario"}"""))
                .build(),
            HttpResponse.BodyHandlers.discarding()
        )
        assertTrue(response.statusCode() in 200..299)
    }

    private fun resetMock(baseUrl: String) {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("${baseUrl.trimEnd('/')}/api/mock/reset"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.discarding()
        )
        assertTrue(response.statusCode() in 200..299)
    }
}
