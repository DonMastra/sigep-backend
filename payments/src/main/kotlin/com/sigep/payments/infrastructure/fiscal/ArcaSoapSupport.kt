package com.sigep.payments.infrastructure.fiscal

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal data class ArcaSoapResponse(
    val statusCode: Int,
    val body: String
)

internal fun interface ArcaSoapTransport {
    fun post(endpoint: URI, soapAction: String, body: String): ArcaSoapResponse
}

internal class JdkArcaSoapTransport(
    connectTimeout: Duration,
    private val requestTimeout: Duration,
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES
) : ArcaSoapTransport {

    private val client = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun post(endpoint: URI, soapAction: String, body: String): ArcaSoapResponse {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(requestTimeout)
            .header("Content-Type", "text/xml; charset=utf-8")
            .header("Accept", "text/xml")
            .header("SOAPAction", "\"$soapAction\"")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ArcaTransportException("ARCA request was interrupted", exception)
        } catch (exception: Exception) {
            throw ArcaTransportException("ARCA transport failed", exception)
        }
        val responseSize = response.body().toByteArray(StandardCharsets.UTF_8).size
        if (responseSize > maxResponseBytes) {
            throw ArcaProtocolException("ARCA response exceeded the configured size limit")
        }
        return ArcaSoapResponse(response.statusCode(), response.body())
    }

    private companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 5 * 1024 * 1024
    }
}

internal open class ArcaClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal class ArcaConfigurationException(message: String, cause: Throwable? = null) :
    ArcaClientException(message, cause)

internal class ArcaTransportException(message: String, cause: Throwable? = null) :
    ArcaClientException(message, cause)

internal class ArcaProtocolException(message: String, cause: Throwable? = null) :
    ArcaClientException(message, cause)

internal object ArcaXml {

    fun parse(xml: String): Document {
        try {
            // Some compatible SOAP mocks prepend indentation or a UTF-8 BOM
            // before the XML declaration. Normalize only that prefix; parser
            // hardening against DTDs and external entities remains unchanged.
            val normalizedXml = xml.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                setExpandEntityReferences(false)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
            return factory.newDocumentBuilder().parse(normalizedXml.byteInputStream(StandardCharsets.UTF_8))
        } catch (exception: Exception) {
            throw ArcaProtocolException("ARCA returned invalid XML", exception)
        }
    }

    fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> character
                }
            )
        }
    }

    fun Document.first(localName: String): Element? =
        getElementsByTagNameNS("*", localName).item(0) as? Element

    fun Element.first(localName: String): Element? =
        getElementsByTagNameNS("*", localName).item(0) as? Element

    fun Element.child(localName: String): Element? {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && (child.localName ?: child.nodeName.substringAfter(':')) == localName) {
                return child
            }
        }
        return null
    }

    fun Element.text(localName: String): String? = child(localName)?.textContent?.trim()?.takeIf(String::isNotEmpty)

    fun Document.soapFault(): String? {
        val fault = first("Fault") ?: return null
        return fault.first("faultstring")?.textContent?.trim()
            ?: fault.first("Text")?.textContent?.trim()
            ?: "ARCA SOAP fault"
    }
}
