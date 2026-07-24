package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.AuthorizedVoucherKey
import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import com.sigep.payments.application.gateway.FiscalAuthorizationStatus
import com.sigep.payments.application.gateway.FiscalEnvironment
import com.sigep.payments.application.gateway.FiscalOtherTaxRequest
import com.sigep.payments.application.gateway.FiscalVatSubtotalRequest
import com.sigep.payments.application.gateway.VoucherSequenceKey
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.net.URI
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArcaWsfeClientTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-21T15:00:00Z"), ZoneOffset.UTC)
    private val sequence = VoucherSequenceKey("30712345678", 3, 11)

    @Test
    fun `queries last authorized voucher with cached WSAA credentials`() {
        var requestBody = ""
        val client = client { _, action, body ->
            assertTrue(action.endsWith("FECompUltimoAutorizado"))
            requestBody = body
            ArcaSoapResponse(200, soap("<FECompUltimoAutorizadoResult><PtoVta>3</PtoVta><CbteTipo>11</CbteTipo><CbteNro>42</CbteNro></FECompUltimoAutorizadoResult>"))
        }

        assertEquals(42, client.lastAuthorized(sequence))
        assertTrue(requestBody.contains("<Token>token</Token>"))
        assertTrue(requestBody.contains("<Cuit>30712345678</Cuit>"))
    }

    @Test
    fun `loads typed WSFE reference catalogs`() {
        val operations = mutableListOf<String>()
        val client = client { _, action, body ->
            operations += action.substringAfterLast('/')
            assertTrue(body.contains("<Token>token</Token>"))
            val result = when {
                action.endsWith("FEParamGetTiposCbte") ->
                    "<FEParamGetTiposCbteResult><ResultGet><CbteTipo><Id>11</Id><Desc>Factura C</Desc><FchDesde>20110325</FchDesde></CbteTipo></ResultGet></FEParamGetTiposCbteResult>"
                action.endsWith("FEParamGetTiposDoc") ->
                    "<FEParamGetTiposDocResult><ResultGet><DocTipo><Id>80</Id><Desc>CUIT</Desc></DocTipo></ResultGet></FEParamGetTiposDocResult>"
                action.endsWith("FEParamGetCondicionIvaReceptor") ->
                    "<FEParamGetCondicionIvaReceptorResult><ResultGet><CondicionIvaReceptor><Id>5</Id><Desc>Consumidor Final</Desc><FchHasta>20991231</FchHasta></CondicionIvaReceptor></ResultGet></FEParamGetCondicionIvaReceptorResult>"
                action.endsWith("FEParamGetTiposMonedas") ->
                    "<FEParamGetTiposMonedasResult><ResultGet><Moneda><Id>PES</Id><Desc>Pesos Argentinos</Desc></Moneda></ResultGet></FEParamGetTiposMonedasResult>"
                else -> error("Unexpected operation $action")
            }
            ArcaSoapResponse(200, soap(result))
        }

        val catalogs = client.referenceData()

        assertEquals(listOf("FEParamGetTiposCbte", "FEParamGetTiposDoc", "FEParamGetCondicionIvaReceptor", "FEParamGetTiposMonedas"), operations)
        assertEquals("11", catalogs.voucherTypes.single().id)
        assertEquals(LocalDate.of(2011, 3, 25), catalogs.voucherTypes.single().validFrom)
        assertEquals("80", catalogs.documentTypes.single().id)
        assertEquals("5", catalogs.receiverVatConditions.single().id)
        assertEquals(LocalDate.of(2099, 12, 31), catalogs.receiverVatConditions.single().validTo)
        assertEquals("PES", catalogs.currencies.single().id)
    }

    @Test
    fun `authorizes a service voucher and maps CAE plus observations`() {
        var requestBody = ""
        val client = client { _, action, body ->
            assertTrue(action.endsWith("FECAESolicitar"))
            requestBody = body
            ArcaSoapResponse(
                200,
                soap(
                    """<FECAESolicitarResult><FeCabResp><Resultado>A</Resultado></FeCabResp><FeDetResp><FECAEDetResponse><Resultado>A</Resultado><CAE>71234567890123</CAE><CAEFchVto>20260731</CAEFchVto><Obs><Observaciones><Code>10017</Code><Msg>Aprobado con observacion</Msg></Observaciones></Obs></FECAEDetResponse></FeDetResp></FECAESolicitarResult>"""
                )
            )
        }

        val result = client.authorize(request())

        assertEquals(FiscalAuthorizationStatus.APPROVED, result.status)
        assertEquals("71234567890123", result.authorizationCode)
        assertEquals(LocalDate.of(2026, 7, 31), result.authorizationExpiresOn)
        assertEquals("10017", result.observations.single().code)
        assertTrue(requestBody.contains("<FchServDesde>20260701</FchServDesde>"))
        assertTrue(requestBody.contains("<CondicionIVAReceptorId>5</CondicionIVAReceptorId>"))
        assertTrue(requestBody.contains("<ImpTotal>100.00</ImpTotal>"))
    }

    @Test
    fun `consult maps an existing authorization and returns null when absent`() {
        var existing = true
        val client = client { _, _, _ ->
            val result = if (existing) {
                "<FECompConsultarResult><ResultGet><Resultado>A</Resultado><CodAutorizacion>71234567890123</CodAutorizacion><FchVto>20260731</FchVto></ResultGet></FECompConsultarResult>"
            } else {
                "<FECompConsultarResult><Errors><Err><Code>10070</Code><Msg>No encontrado</Msg></Err></Errors></FECompConsultarResult>"
            }
            ArcaSoapResponse(200, soap(result))
        }
        val voucher = AuthorizedVoucherKey(sequence, 43)

        assertNotNull(client.consult(voucher))
        existing = false
        assertEquals(null, client.consult(voucher))
    }

    @Test
    fun `serializes VAT aliquots and other taxes in official WSFE order`() {
        var requestBody = ""
        val client = client { _, _, body ->
            requestBody = body
            ArcaSoapResponse(
                200,
                soap("<FECAESolicitarResult><FeDetResp><FECAEDetResponse><Resultado>A</Resultado><CAE>71234567890123</CAE><CAEFchVto>20260731</CAEFchVto></FECAEDetResponse></FeDetResp></FECAESolicitarResult>")
            )
        }

        client.authorize(
            request().copy(
                totalAmount = BigDecimal("128.80"),
                netAmount = BigDecimal("100.00"),
                vatAmount = BigDecimal("21.00"),
                otherTaxesAmount = BigDecimal("7.80"),
                vatSubtotals = listOf(FiscalVatSubtotalRequest(5, BigDecimal("100.00"), BigDecimal("21.00"))),
                taxes = listOf(
                    FiscalOtherTaxRequest(99, "Tasa & municipal", BigDecimal("150.00"), BigDecimal("5.2"), BigDecimal("7.80"))
                )
            )
        )

        assertTrue(requestBody.contains("<Tributos><Tributo><Id>99</Id><Desc>Tasa &amp; municipal</Desc><BaseImp>150.00</BaseImp><Alic>5.2</Alic><Importe>7.80</Importe></Tributo></Tributos>"))
        assertTrue(requestBody.contains("<Iva><AlicIva><Id>5</Id><BaseImp>100.00</BaseImp><Importe>21.00</Importe></AlicIva></Iva>"))
        assertTrue(requestBody.indexOf("<CondicionIVAReceptorId>") < requestBody.indexOf("<Tributos>"))
        assertTrue(requestBody.indexOf("<Tributos>") < requestBody.indexOf("<Iva>"))
    }

    private fun client(handler: (URI, String, String) -> ArcaSoapResponse): ArcaWsfeClient = ArcaWsfeClient(
        testSettings(),
        object : ArcaAccessTicketProvider {
            override fun get() = ArcaAccessTicket("token", "sign", Instant.parse("2026-07-22T03:00:00Z"))
            override fun invalidate() = Unit
        },
        ArcaSoapTransport(handler),
        clock
    )

    private fun request() = FiscalAuthorizationRequest(
        invoiceId = 10,
        idempotencyKey = "invoice-10",
        sequence = sequence,
        voucherNumber = 43,
        concept = 2,
        receiverDocumentType = 80,
        receiverDocumentNumber = "20123456789",
        receiverVatConditionId = 5,
        issueDate = LocalDate.of(2026, 7, 21),
        serviceFrom = LocalDate.of(2026, 7, 1),
        serviceTo = LocalDate.of(2026, 7, 31),
        paymentDueDate = LocalDate.of(2026, 8, 10),
        totalAmount = BigDecimal("100.00"),
        netAmount = BigDecimal("100.00")
    )

    private fun soap(content: String) =
        """<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body>$content</soap:Body></soap:Envelope>"""

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
