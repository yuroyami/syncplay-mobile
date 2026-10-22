package app.protocol.network

import app.preferences.Preferences
import app.preferences.set
import app.preferences.value
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Certificates that the person chose to trust by their fingerprint, one per server address.
 *
 * A room hosted in the app uses a certificate that no authority signed. The joining app shows its
 * fingerprint once, the person compares it with the one in the host's hosting panel, and the app
 * keeps the answer. A later, different certificate for the same address is refused with a
 * warning, because it can mean that someone intercepts the connection.
 */
object CertificatePins {

    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    private fun key(host: String, port: Int) = "${host.trim().lowercase()}:$port"

    /** The fingerprint that the person trusted for [host] and [port], or null. */
    fun pinnedFor(host: String, port: Int): String? = read()[key(host, port)]

    /** Trusts [fingerprint] for [host] and [port] from now on, in place of any earlier one. */
    suspend fun pin(host: String, port: Int, fingerprint: String) {
        Preferences.TLS_PINS.set(json.encodeToString(mapSerializer, read() + (key(host, port) to fingerprint)))
    }

    private fun read(): Map<String, String> =
        runCatching { json.decodeFromString(mapSerializer, Preferences.TLS_PINS.value()) }.getOrDefault(emptyMap())

    /** [fingerprint] in four lines of eight pairs, so two people can read it out and compare. */
    fun fingerprintLines(fingerprint: String): String =
        fingerprint.split(':').chunked(8).joinToString("\n") { it.joinToString(":") }

    /** The SHA-256 of a certificate's DER bytes, as colon-separated pairs of uppercase hex. */
    fun fingerprintOf(der: ByteArray): String =
        SHA256().digest(der).joinToString(":") { byte -> (byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0') }
}

/**
 * A server certificate that neither the system nor a pin trusts, from the last TLS upgrade.
 * [pinned] is the fingerprint trusted before for this address, when the certificate changed.
 */
data class UntrustedCertificate(val host: String, val port: Int, val fingerprint: String, val pinned: String?)
