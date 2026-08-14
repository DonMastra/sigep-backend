package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.AuthorizedVoucherKey
import com.sigep.payments.application.gateway.FiscalAuthorizationRequest
import com.sigep.payments.application.gateway.FiscalAuthorizationResult
import com.sigep.payments.application.gateway.FiscalAuthorizationStatus
import com.sigep.payments.application.gateway.FiscalCatalogEntry
import com.sigep.payments.application.gateway.FiscalObservation
import com.sigep.payments.application.gateway.FiscalReferenceData
import com.sigep.payments.application.gateway.VoucherSequenceKey
import com.sigep.payments.infrastructure.fiscal.ArcaXml.escape
import com.sigep.payments.infrastructure.fiscal.ArcaXml.first
import com.sigep.payments.infrastructure.fiscal.ArcaXml.soapFault
import com.sigep.payments.infrastructure.fiscal.ArcaXml.text
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal data class ArcaWsfeHealth(
    val available: Boolean,
    val message: String
)

internal class ArcaWsfeClient(
    private val settings: ArcaFiscalSettings,
    private val tickets: ArcaAccessTicketProvider,
    private val transport: ArcaSoapTransport,
    private val clock: Clock = Clock.systemUTC()
) {
    fun health(): ArcaWsfeHealth = try {
        val document = call(OP_DUMMY, "<$OP_DUMMY xmlns=\"$WSFE_NAMESPACE\" />", authenticated = false)
        val result = document.first("FEDummyResult")
            ?: throw ArcaProtocolException("WSFE FEDummy response is incomplete")
        val components = listOf("AppServer", "DbServer", "AuthServer").associateWith { result.text(it) }
        val available = components.values.all { it.equals("OK", ignoreCase = true) }
        ArcaWsfeHealth(available, if (available) "WSFE services report OK" else "One or more WSFE services are unavailable")
    } catch (_: Exception) {
        ArcaWsfeHealth(false, "WSFE health check failed")
    }

    fun referenceData(): FiscalReferenceData {
        val ticket = tickets.get()
        return FiscalReferenceData(
            voucherTypes = catalog(ticket, OP_VOUCHER_TYPES, "FEParamGetTiposCbteResult", "CbteTipo"),
            documentTypes = catalog(ticket, OP_DOCUMENT_TYPES, "FEParamGetTiposDocResult", "DocTipo"),
            receiverVatConditions = catalog(
                ticket,
                OP_RECEIVER_VAT_CONDITIONS,
                "FEParamGetCondicionIvaReceptorResult",
                "CondicionIvaReceptor"
            ),
            currencies = catalog(ticket, OP_CURRENCIES, "FEParamGetTiposMonedasResult", "Moneda"),
            retrievedAt = LocalDateTime.now(clock)
        )
    }

    fun lastAuthorized(sequence: VoucherSequenceKey): Long {
        ensureIssuer(sequence.issuerCuit)
        val ticket = tickets.get()
        val body = """<$OP_LAST xmlns="$WSFE_NAMESPACE">${auth(ticket)}<PtoVta>${sequence.pointOfSale}</PtoVta><CbteTipo>${sequence.voucherType}</CbteTipo></$OP_LAST>"""
        val document = call(OP_LAST, body)
        val result = document.first("FECompUltimoAutorizadoResult")
            ?: throw ArcaProtocolException("WSFE last-authorized response is incomplete")
        throwIfErrors(document, "WSFE rejected the last-authorized query")
        return result.text("CbteNro")?.toLongOrNull()
            ?: throw ArcaProtocolException("WSFE last-authorized response does not contain CbteNro")
    }

    fun authorize(request: FiscalAuthorizationRequest): FiscalAuthorizationResult {
        ensureIssuer(request.sequence.issuerCuit)
        val ticket = tickets.get()
        val document = call(OP_AUTHORIZE, authorizeBody(ticket, request))
        val detail = document.first("FECAEDetResponse") ?: document.first("FEDetResponse")
        val errors = issues(document, "Errors", setOf("Err"))
        val observations = issues(document, "Events", setOf("Evt")) +
            (detail?.let { issues(it, "Obs", setOf("Obs", "Observaciones")) } ?: emptyList())
        if (detail == null) {
            if (errors.isNotEmpty()) return rejected(request.voucherKey(), errors, observations)
            throw ArcaProtocolException("WSFE authorization response does not contain a detail result")
        }
        val resultCode = detail.text("Resultado")
            ?: throw ArcaProtocolException("WSFE authorization response does not contain Resultado")
        val providerRequestId = providerRequestId(request.voucherKey())
        return if (resultCode.equals(APPROVED, ignoreCase = true)) {
            val cae = detail.text("CAE")
                ?: throw ArcaProtocolException("WSFE approved a voucher without CAE")
            val expiration = detail.text("CAEFchVto")?.toArcaDate()
                ?: throw ArcaProtocolException("WSFE approved a voucher without CAEFchVto")
            FiscalAuthorizationResult(
                status = FiscalAuthorizationStatus.APPROVED,
                voucher = request.voucherKey(),
                authorizationCode = cae,
                authorizationExpiresOn = expiration,
                observations = observations,
                errors = errors,
                providerRequestId = providerRequestId,
                processedAt = LocalDateTime.now(clock)
            )
        } else {
            rejected(request.voucherKey(), errors.ifEmpty {
                listOf(FiscalObservation("WSFE_REJECTED", "WSFE rejected the voucher"))
            }, observations)
        }
    }

    fun consult(voucher: AuthorizedVoucherKey): FiscalAuthorizationResult? {
        ensureIssuer(voucher.sequence.issuerCuit)
        val ticket = tickets.get()
        val body = """<$OP_CONSULT xmlns="$WSFE_NAMESPACE">${auth(ticket)}<FeCompConsReq><CbteTipo>${voucher.sequence.voucherType}</CbteTipo><CbteNro>${voucher.voucherNumber}</CbteNro><PtoVta>${voucher.sequence.pointOfSale}</PtoVta></FeCompConsReq></$OP_CONSULT>"""
        val document = call(OP_CONSULT, body)
        val result = document.first("ResultGet") ?: return null
        val authorizationCode = result.text("CodAutorizacion")?.takeIf(String::isNotEmpty) ?: return null
        val expiration = result.text("FchVto")?.toArcaDate()
        val observations = issues(document, "Events", setOf("Evt"))
        return FiscalAuthorizationResult(
            status = FiscalAuthorizationStatus.APPROVED,
            voucher = voucher,
            authorizationCode = authorizationCode,
            authorizationExpiresOn = expiration,
            observations = observations,
            errors = emptyList(),
            providerRequestId = providerRequestId(voucher),
            processedAt = LocalDateTime.now(clock)
        )
    }

    private fun authorizeBody(ticket: ArcaAccessTicket, request: FiscalAuthorizationRequest): String {
        val serviceDates = if (request.concept == PRODUCT_CONCEPT) "" else buildString {
            append("<FchServDesde>${request.serviceFrom?.toArcaDateValue()}</FchServDesde>")
            append("<FchServHasta>${request.serviceTo?.toArcaDateValue()}</FchServHasta>")
            append("<FchVtoPago>${request.paymentDueDate?.toArcaDateValue()}</FchVtoPago>")
        }
        val taxes = request.taxes.takeIf { it.isNotEmpty() }?.joinToString(
            prefix = "<Tributos>",
            postfix = "</Tributos>",
            separator = ""
        ) {
            "<Tributo><Id>${it.id}</Id><Desc>${escape(it.description)}</Desc><BaseImp>${it.baseAmount.money()}</BaseImp><Alic>${it.rate.rate()}</Alic><Importe>${it.amount.money()}</Importe></Tributo>"
        }.orEmpty()
        val vat = request.vatSubtotals.takeIf { it.isNotEmpty() }?.joinToString(
            prefix = "<Iva>",
            postfix = "</Iva>",
            separator = ""
        ) {
            "<AlicIva><Id>${it.id}</Id><BaseImp>${it.baseAmount.money()}</BaseImp><Importe>${it.amount.money()}</Importe></AlicIva>"
        }.orEmpty()
        return """<$OP_AUTHORIZE xmlns="$WSFE_NAMESPACE">${auth(ticket)}<FeCAEReq><FeCabReq><CantReg>1</CantReg><PtoVta>${request.sequence.pointOfSale}</PtoVta><CbteTipo>${request.sequence.voucherType}</CbteTipo></FeCabReq><FeDetReq><FECAEDetRequest><Concepto>${request.concept}</Concepto><DocTipo>${request.receiverDocumentType}</DocTipo><DocNro>${escape(request.receiverDocumentNumber)}</DocNro><CbteDesde>${request.voucherNumber}</CbteDesde><CbteHasta>${request.voucherNumber}</CbteHasta><CbteFch>${request.issueDate.toArcaDateValue()}</CbteFch><ImpTotal>${request.totalAmount.money()}</ImpTotal><ImpTotConc>${request.nonTaxedAmount.money()}</ImpTotConc><ImpNeto>${request.netAmount.money()}</ImpNeto><ImpOpEx>${request.exemptAmount.money()}</ImpOpEx><ImpTrib>${request.otherTaxesAmount.money()}</ImpTrib><ImpIVA>${request.vatAmount.money()}</ImpIVA>$serviceDates<MonId>${escape(request.currency)}</MonId><MonCotiz>${request.exchangeRate.rate()}</MonCotiz><CondicionIVAReceptorId>${request.receiverVatConditionId}</CondicionIVAReceptorId>$taxes$vat</FECAEDetRequest></FeDetReq></FeCAEReq></$OP_AUTHORIZE>"""
    }

    private fun auth(ticket: ArcaAccessTicket): String =
        "<Auth><Token>${escape(ticket.token)}</Token><Sign>${escape(ticket.sign)}</Sign><Cuit>${settings.issuerCuit}</Cuit></Auth>"

    private fun catalog(
        ticket: ArcaAccessTicket,
        operation: String,
        resultName: String,
        itemName: String
    ): List<FiscalCatalogEntry> {
        val body = "<$operation xmlns=\"$WSFE_NAMESPACE\">${auth(ticket)}</$operation>"
        val document = call(operation, body)
        throwIfErrors(document, "WSFE rejected the $operation query")
        val result = document.first(resultName)
            ?: throw ArcaProtocolException("WSFE $operation response is incomplete")
        val nodes = result.getElementsByTagNameNS("*", itemName)
        return buildList {
            for (index in 0 until nodes.length) {
                val item = nodes.item(index) as? Element ?: continue
                val id = item.text("Id") ?: continue
                val description = item.text("Desc") ?: continue
                add(
                    FiscalCatalogEntry(
                        id = id,
                        description = description,
                        validFrom = item.text("FchDesde")?.toArcaDate(),
                        validTo = item.text("FchHasta")?.toArcaDate()
                    )
                )
            }
        }.distinctBy(FiscalCatalogEntry::id)
    }

    private fun call(operation: String, body: String, authenticated: Boolean = true): Document {
        val envelope = """<?xml version="1.0" encoding="UTF-8"?><soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Header/><soap:Body>$body</soap:Body></soap:Envelope>"""
        val response = transport.post(settings.wsfeEndpoint, "$WSFE_NAMESPACE$operation", envelope)
        val document = ArcaXml.parse(response.body)
        document.soapFault()?.let { fault ->
            if (authenticated && fault.contains("token", ignoreCase = true)) tickets.invalidate()
            throw ArcaProtocolException("WSFE SOAP fault: $fault")
        }
        if (response.statusCode !in 200..299) {
            throw ArcaProtocolException("WSFE returned HTTP ${response.statusCode}")
        }
        return document
    }

    private fun throwIfErrors(document: Document, prefix: String) {
        val errors = issues(document, "Errors", setOf("Err"))
        if (errors.isNotEmpty()) {
            throw ArcaProtocolException("$prefix (${errors.joinToString { it.code }})")
        }
    }

    private fun issues(root: Document, containerName: String, itemNames: Set<String>): List<FiscalObservation> =
        root.first(containerName)?.let { issues(it, null, itemNames) } ?: emptyList()

    private fun issues(root: Element, containerName: String?, itemNames: Set<String>): List<FiscalObservation> {
        val container = containerName?.let { name -> root.first(name) } ?: root
        val observations = mutableListOf<FiscalObservation>()
        val containerLocalName = container.localName ?: container.nodeName.substringAfter(':')
        if (containerLocalName in itemNames) {
            val code = container.text("Code")
            val message = container.text("Msg")
            if (code != null && message != null) {
                observations += FiscalObservation(code, message.take(MAX_PROVIDER_MESSAGE_LENGTH))
            }
        }
        val nodes = container.getElementsByTagNameNS("*", "*")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val localName = element.localName ?: element.nodeName.substringAfter(':')
            if (localName !in itemNames) continue
            val code = element.text("Code") ?: continue
            val message = element.text("Msg") ?: continue
            observations += FiscalObservation(code, message.take(MAX_PROVIDER_MESSAGE_LENGTH))
        }
        return observations.distinct()
    }

    private fun rejected(
        voucher: AuthorizedVoucherKey,
        errors: List<FiscalObservation>,
        observations: List<FiscalObservation>
    ) = FiscalAuthorizationResult(
        status = FiscalAuthorizationStatus.REJECTED,
        voucher = voucher,
        observations = observations,
        errors = errors,
        providerRequestId = providerRequestId(voucher),
        processedAt = LocalDateTime.now(clock)
    )

    private fun ensureIssuer(issuerCuit: String) {
        require(issuerCuit == settings.issuerCuit) { "Invoice issuer does not match the configured ARCA representative" }
    }

    private fun providerRequestId(voucher: AuthorizedVoucherKey): String =
        "arca:${voucher.sequence.pointOfSale}:${voucher.sequence.voucherType}:${voucher.voucherNumber}"

    private fun FiscalAuthorizationRequest.voucherKey() = AuthorizedVoucherKey(sequence, voucherNumber)
    private fun String.toArcaDate(): LocalDate = try {
        LocalDate.parse(this, ARCA_DATE)
    } catch (exception: Exception) {
        throw ArcaProtocolException("WSFE returned an invalid fiscal date", exception)
    }

    private fun LocalDate.toArcaDateValue(): String = format(ARCA_DATE)
    private fun BigDecimal.money(): String = setScale(2, RoundingMode.HALF_EVEN).toPlainString()
    private fun BigDecimal.rate(): String = stripTrailingZeros().toPlainString()

    private companion object {
        const val WSFE_NAMESPACE = "http://ar.gov.afip.dif.FEV1/"
        const val OP_DUMMY = "FEDummy"
        const val OP_LAST = "FECompUltimoAutorizado"
        const val OP_AUTHORIZE = "FECAESolicitar"
        const val OP_CONSULT = "FECompConsultar"
        const val OP_VOUCHER_TYPES = "FEParamGetTiposCbte"
        const val OP_DOCUMENT_TYPES = "FEParamGetTiposDoc"
        const val OP_RECEIVER_VAT_CONDITIONS = "FEParamGetCondicionIvaReceptor"
        const val OP_CURRENCIES = "FEParamGetTiposMonedas"
        const val APPROVED = "A"
        const val PRODUCT_CONCEPT = 1
        const val MAX_PROVIDER_MESSAGE_LENGTH = 500
        val ARCA_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }
}
