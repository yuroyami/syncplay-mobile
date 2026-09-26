package app.server.tls

import app.protocol.network.CertificatePins
import app.utils.loggy
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The host's own TLS identity for the built-in server: an EC P-256 key and a self-signed
 * certificate, made once and kept. No authority signs it, so a joining app trusts it only by its
 * fingerprint, which the hosting panel shows.
 */
internal object HostCertificate {

    /** The key and the certificate that the server shows to every client. */
    class Identity(val key: PrivateKey, val certificate: X509Certificate) {
        val fingerprint: String = CertificatePins.fingerprintOf(certificate.encoded)
    }

    private const val KEY_FILE = "host-tls-key.der"
    private const val CERTIFICATE_FILE = "host-tls-certificate.der"
    private const val SUBJECT = "Synkplay host"
    private const val VALIDITY_YEARS = 20L
    private const val ECDSA_WITH_SHA256 = "1.2.840.10045.4.3.2"
    private const val COMMON_NAME = "2.5.4.3"

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    /** The identity kept in [dir], or a new one that is written there first. */
    @Synchronized
    fun loadOrCreate(dir: File): Identity {
        val keyFile = File(dir, KEY_FILE)
        val certificateFile = File(dir, CERTIFICATE_FILE)
        if (keyFile.exists() && certificateFile.exists()) {
            runCatching { return load(keyFile.readBytes(), certificateFile.readBytes()) }
                .onFailure { loggy("Host certificate: the kept one does not load, making a new one: ${it.message}") }
        }
        val created = create()
        dir.mkdirs()
        keyFile.writeBytes(created.key.encoded)
        certificateFile.writeBytes(created.certificate.encoded)
        return created
    }

    /** A new key and a certificate for it, valid from a day ago for [VALIDITY_YEARS]. */
    fun create(now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)): Identity {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val algorithm = Der.sequence(Der.oid(ECDSA_WITH_SHA256))
        val name = Der.sequence(Der.set(Der.sequence(Der.oid(COMMON_NAME), Der.utf8String(SUBJECT))))
        val serial = ByteArray(16).also { SecureRandom().nextBytes(it) }
        // Positive, and with no leading zero byte.
        serial[0] = ((serial[0].toInt() and 0x7F) or 0x01).toByte()
        val tbs = Der.sequence(
            Der.explicit(0, Der.integer(byteArrayOf(2))),
            Der.integer(serial),
            algorithm,
            name,
            Der.sequence(Der.time(now.minusDays(1).format(stamp)), Der.time(now.plusYears(VALIDITY_YEARS).format(stamp))),
            name,
            keys.public.encoded,
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(tbs)
            sign()
        }
        val der = Der.sequence(tbs, algorithm, Der.bitString(signature))
        return Identity(keys.private, parse(der))
    }

    private fun load(key: ByteArray, certificate: ByteArray): Identity =
        Identity(KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(key)), parse(certificate))

    private fun parse(der: ByteArray): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
}
