package app.server.tls

import app.protocol.network.CertificatePins
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The host's certificate is a valid, self-signed X.509 certificate, made once and kept. */
class HostCertificateTest {

    private val dir = createTempDirectory("synkplay-host-tls").toFile()

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    @Test
    fun aNewCertificateIsValidAndSignedByItsOwnKey() {
        val identity = HostCertificate.create()
        val certificate = identity.certificate
        certificate.checkValidity()
        certificate.verify(certificate.publicKey)
        assertEquals("CN=Synkplay host", certificate.subjectX500Principal.name)
        assertEquals(certificate.subjectX500Principal, certificate.issuerX500Principal)
        assertEquals(3, certificate.version)
        assertTrue(certificate.serialNumber.signum() > 0)
        assertEquals(CertificatePins.fingerprintOf(certificate.encoded), identity.fingerprint)
        assertTrue(Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}").matches(identity.fingerprint), identity.fingerprint)
    }

    @Test
    fun theKeptCertificateComesBackAndABrokenOneIsReplaced() {
        val first = HostCertificate.loadOrCreate(dir)
        assertEquals(first.fingerprint, HostCertificate.loadOrCreate(dir).fingerprint)

        File(dir, "host-tls-certificate.der").writeText("not a certificate")
        val replaced = HostCertificate.loadOrCreate(dir)
        assertNotEquals(first.fingerprint, replaced.fingerprint)
        assertEquals(replaced.fingerprint, HostCertificate.loadOrCreate(dir).fingerprint)
    }

    @Test
    fun theFingerprintReadsInFourLinesOfEightPairs() {
        val lines = CertificatePins.fingerprintLines(HostCertificate.create().fingerprint).lines()
        assertEquals(4, lines.size)
        assertTrue(lines.all { it.split(':').size == 8 })
    }
}
