package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.FiscalEnvironment
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ArcaWsaaClientTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-21T15:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `builds signed login request and parses access ticket without exposing transport types`() {
        var signedContent = ""
        var postedBody = ""
        val ticketXml = """<loginTicketResponse version="1.0"><header><expirationTime>2026-07-22T03:00:00Z</expirationTime></header><credentials><token>token-value</token><sign>sign-value</sign></credentials></loginTicketResponse>"""
        val responseXml = """<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><loginCmsResponse xmlns="http://wsaa.view.sua.dvadac.desein.afip.gov"><loginCmsReturn>${ArcaXml.escape(ticketXml)}</loginCmsReturn></loginCmsResponse></soap:Body></soap:Envelope>"""
        val transport = ArcaSoapTransport { _, action, body ->
            assertEquals("urn:LoginCms", action)
            postedBody = body
            ArcaSoapResponse(200, responseXml)
        }
        val signer = ArcaCmsSigner { content ->
            signedContent = content.toString(StandardCharsets.UTF_8)
            "CMS&VALUE"
        }
        val client = ArcaWsaaClient(testSettings(), signer, transport, clock)

        val ticket = client.login()

        assertEquals("token-value", ticket.token)
        assertEquals("sign-value", ticket.sign)
        assertEquals(Instant.parse("2026-07-22T03:00:00Z"), ticket.expiresAt)
        assertTrue(signedContent.contains("<service>wsfe</service>"))
        assertTrue(signedContent.contains("<uniqueId>1784646000</uniqueId>"))
        assertTrue(postedBody.contains("CMS&amp;VALUE"))
        assertTrue(!postedBody.contains("token-value"))
    }

    @Test
    fun `cache reuses a valid access ticket`() {
        var calls = 0
        val responseXml = wsaaResponse("2026-07-22T03:00:00Z")
        val client = ArcaWsaaClient(
            testSettings(),
            ArcaCmsSigner { "cms" },
            ArcaSoapTransport { _, _, _ -> calls += 1; ArcaSoapResponse(200, responseXml) },
            clock
        )
        val cache = CachedArcaAccessTicketProvider(client, Duration.ofMinutes(5).seconds, clock)

        val first = cache.get()
        val second = cache.get()

        assertSame(first, second)
        assertEquals(1, calls)
    }

    private fun wsaaResponse(expiration: String): String {
        val ticket = """<loginTicketResponse><header><expirationTime>$expiration</expirationTime></header><credentials><token>token</token><sign>sign</sign></credentials></loginTicketResponse>"""
        return """<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><loginCmsResponse><loginCmsReturn>${ArcaXml.escape(ticket)}</loginCmsReturn></loginCmsResponse></soap:Body></soap:Envelope>"""
    }

    private fun testSettings() = ArcaFiscalSettings(
        environment = FiscalEnvironment.HOMOLOGATION,
        issuerCuit = "30712345678",
        wsaaEndpoint = URI("https://wsaahomo.afip.gov.ar/ws/services/LoginCms"),
        wsfeEndpoint = URI("https://wswhomo.afip.gov.ar/wsfev1/service.asmx"),
        keyStorePath = Path.of("unused.p12"),
        keyStorePassword = "secret".toCharArray(),
        keyAlias = null,
        connectTimeout = Duration.ofSeconds(5),
        requestTimeout = Duration.ofSeconds(20),
        ticketRefreshSkew = Duration.ofMinutes(5)
    )
}
