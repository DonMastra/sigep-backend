package com.sigep.payments.infrastructure.fiscal

import com.sigep.payments.application.gateway.FiscalEnvironment
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigInteger
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class Pkcs12ArcaCmsSignerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `creates an attached verifiable CMS from PKCS12`() {
        val password = "test-password".toCharArray()
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name("CN=SiGEP Test, SERIALNUMBER=CUIT 30712345678")
        val certificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.ONE,
            Date.from(Instant.parse("2026-01-01T00:00:00Z")),
            Date.from(Instant.parse("2027-01-01T00:00:00Z")),
            subject,
            keyPair.public
        )
        val certificate = JcaX509CertificateConverter().getCertificate(
            certificateBuilder.build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        )
        val keyStorePath = tempDir.resolve("arca-test.p12")
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, password)
            setKeyEntry("sigep", keyPair.private, password, arrayOf(certificate))
        }
        Files.newOutputStream(keyStorePath).use { keyStore.store(it, password) }
        val settings = settings(keyStorePath, password)
        val clock = Clock.fixed(Instant.parse("2026-07-21T15:00:00Z"), ZoneOffset.UTC)
        val content = "<loginTicketRequest><service>wsfe</service></loginTicketRequest>".toByteArray(StandardCharsets.UTF_8)

        val encoded = Pkcs12ArcaCmsSigner(settings, clock).sign(content)

        val cms = CMSSignedData(Base64.getDecoder().decode(encoded))
        assertContentEquals(content, cms.signedContent.content as ByteArray)
        val signerInformation = cms.signerInfos.signers.single()
        assertTrue(signerInformation.verify(JcaSimpleSignerInfoVerifierBuilder().build(certificate)))
    }

    private fun settings(path: Path, password: CharArray) = ArcaFiscalSettings(
        environment = FiscalEnvironment.HOMOLOGATION,
        issuerCuit = "30712345678",
        wsaaEndpoint = URI("https://wsaahomo.arca.gov.ar/ws/services/LoginCms"),
        wsfeEndpoint = URI("https://wswhomo.afip.gov.ar/wsfev1/service.asmx"),
        keyStorePath = path,
        keyStorePassword = password,
        keyAlias = "sigep",
        connectTimeout = Duration.ofSeconds(5),
        requestTimeout = Duration.ofSeconds(20),
        ticketRefreshSkew = Duration.ofMinutes(5)
    )
}
