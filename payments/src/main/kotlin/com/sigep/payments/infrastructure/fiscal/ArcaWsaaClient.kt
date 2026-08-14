package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.infrastructure.fiscal.ArcaXml.escape
import com.sigep.payments.infrastructure.fiscal.ArcaXml.first
import com.sigep.payments.infrastructure.fiscal.ArcaXml.soapFault
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Date

internal data class ArcaAccessTicket(
    val token: String,
    val sign: String,
    val expiresAt: Instant
)

internal fun interface ArcaCmsSigner {
    fun sign(content: ByteArray): String
}

internal interface ArcaAccessTicketProvider {
    fun get(): ArcaAccessTicket
    fun invalidate()
}

internal class Pkcs12ArcaCmsSigner(
    settings: ArcaFiscalSettings,
    private val clock: Clock = Clock.systemUTC()
) : ArcaCmsSigner {

    private val privateKey: PrivateKey
    private val certificate: X509Certificate

    init {
        ensureProvider()
        try {
            val keyStore = KeyStore.getInstance("PKCS12")
            Files.newInputStream(settings.keyStorePath).use { input ->
                keyStore.load(input, settings.keyStorePassword)
            }
            val alias = settings.keyAlias
                ?.takeIf(keyStore::isKeyEntry)
                ?: keyStore.aliases().toList().firstOrNull(keyStore::isKeyEntry)
                ?: throw ArcaConfigurationException("The ARCA PKCS12 does not contain a private key entry")
            privateKey = keyStore.getKey(alias, settings.keyStorePassword) as? PrivateKey
                ?: throw ArcaConfigurationException("The ARCA PKCS12 private key is not readable")
            certificate = keyStore.getCertificate(alias) as? X509Certificate
                ?: throw ArcaConfigurationException("The ARCA PKCS12 does not contain an X509 certificate")
            certificate.checkValidity(Date.from(clock.instant()))
        } catch (exception: ArcaClientException) {
            throw exception
        } catch (exception: Exception) {
            throw ArcaConfigurationException("The ARCA PKCS12 could not be loaded", exception)
        }
    }

    override fun sign(content: ByteArray): String {
        try {
            certificate.checkValidity(Date.from(clock.instant()))
            val contentSigner = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .setProvider(PROVIDER)
                .build(privateKey)
            val signerInfo = JcaSignerInfoGeneratorBuilder(
                JcaDigestCalculatorProviderBuilder().setProvider(PROVIDER).build()
            ).build(contentSigner, certificate)
            val generator = CMSSignedDataGenerator().apply {
                addSignerInfoGenerator(signerInfo)
                addCertificate(JcaX509CertificateHolder(certificate))
            }
            val signed = generator.generate(CMSProcessableByteArray(content), true)
            return Base64.getEncoder().encodeToString(signed.encoded)
        } catch (exception: Exception) {
            throw ArcaConfigurationException("The WSAA access request could not be signed", exception)
        }
    }

    private fun ensureProvider() {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private companion object {
        const val PROVIDER = "BC"
        const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    }
}

internal class ArcaWsaaClient(
    private val settings: ArcaFiscalSettings,
    private val signer: ArcaCmsSigner,
    private val transport: ArcaSoapTransport,
    private val clock: Clock = Clock.systemUTC()
) {
    fun login(): ArcaAccessTicket {
        val tra = buildLoginTicketRequest()
        val cms = signer.sign(tra.toByteArray(StandardCharsets.UTF_8))
        val response = transport.post(settings.wsaaEndpoint, WSAA_SOAP_ACTION, soapEnvelope(cms))
        val document = ArcaXml.parse(response.body)
        document.soapFault()?.let { fault -> throw ArcaProtocolException("WSAA rejected the access request: $fault") }
        if (response.statusCode !in 200..299) {
            throw ArcaProtocolException("WSAA returned HTTP ${response.statusCode}")
        }
        val encodedTicket = document.first("loginCmsReturn")?.textContent?.trim()
            ?: throw ArcaProtocolException("WSAA response does not contain loginCmsReturn")
        val ticketDocument = ArcaXml.parse(encodedTicket)
        val token = ticketDocument.first("token")?.textContent?.trim()?.takeIf(String::isNotEmpty)
            ?: throw ArcaProtocolException("WSAA response does not contain Token")
        val sign = ticketDocument.first("sign")?.textContent?.trim()?.takeIf(String::isNotEmpty)
            ?: throw ArcaProtocolException("WSAA response does not contain Sign")
        val expiration = ticketDocument.first("expirationTime")?.textContent?.trim()
            ?: throw ArcaProtocolException("WSAA response does not contain expirationTime")
        return ArcaAccessTicket(token, sign, parseInstant(expiration))
    }

    internal fun buildLoginTicketRequest(): String {
        val now = clock.instant()
        val generationTime = OffsetDateTime.ofInstant(now.minusSeconds(CLOCK_SKEW_SECONDS), ZoneOffset.UTC)
        val expirationTime = OffsetDateTime.ofInstant(now.plusSeconds(REQUEST_LIFETIME_SECONDS), ZoneOffset.UTC)
        return """<?xml version="1.0" encoding="UTF-8"?>
            |<loginTicketRequest version="1.0"><header><uniqueId>${now.epochSecond}</uniqueId><generationTime>${DATE_TIME_FORMATTER.format(generationTime)}</generationTime><expirationTime>${DATE_TIME_FORMATTER.format(expirationTime)}</expirationTime></header><service>${escape(settings.serviceName)}</service></loginTicketRequest>"""
            .trimMargin()
    }

    private fun soapEnvelope(cms: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>
            |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:wsaa="http://wsaa.view.sua.dvadac.desein.afip.gov"><soapenv:Header/><soapenv:Body><wsaa:loginCms><wsaa:in0>${escape(cms)}</wsaa:in0></wsaa:loginCms></soapenv:Body></soapenv:Envelope>"""
            .trimMargin()

    private fun parseInstant(value: String): Instant = runCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { LocalDateTime.parse(value).atZone(clock.zone).toInstant() }
        .getOrElse { throw ArcaProtocolException("WSAA returned an invalid expirationTime", it) }

    private companion object {
        const val WSAA_SOAP_ACTION = "urn:LoginCms"
        const val CLOCK_SKEW_SECONDS = 10L * 60
        const val REQUEST_LIFETIME_SECONDS = 10L * 60
        val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }
}

internal class CachedArcaAccessTicketProvider(
    private val client: ArcaWsaaClient,
    private val refreshSkewSeconds: Long,
    private val clock: Clock = Clock.systemUTC()
) : ArcaAccessTicketProvider {

    @Volatile
    private var current: ArcaAccessTicket? = null

    override fun get(): ArcaAccessTicket {
        current?.takeIf(::isUsable)?.let { return it }
        return synchronized(this) {
            current?.takeIf(::isUsable) ?: client.login().also { current = it }
        }
    }

    override fun invalidate() {
        synchronized(this) { current = null }
    }

    private fun isUsable(ticket: ArcaAccessTicket): Boolean =
        ticket.expiresAt.isAfter(clock.instant().plusSeconds(refreshSkewSeconds))
}

private fun <T> java.util.Enumeration<T>.toList(): List<T> = buildList {
    while (hasMoreElements()) add(nextElement())
}
