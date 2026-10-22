package app.protocol.network

import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/** A certificate that neither the system nor a pin trusts. [fingerprint] is its SHA-256. */
internal class UntrustedCertificateException(val fingerprint: String, cause: Throwable) :
    CertificateException("Untrusted server certificate $fingerprint", cause)

/**
 * The client's certificate check. The system's trust decides first, as always. When it refuses,
 * a certificate that the person trusted by its fingerprint for this address still passes (see
 * [CertificatePins]). Nothing else does: there is no way to trust a certificate unseen.
 *
 * A pinned certificate skips the host name check. The fingerprint names the server on its own,
 * and a self-signed certificate of a hosted room names no host at all.
 */
internal class PinningTrustManager(private val system: X509ExtendedTrustManager) : X509ExtendedTrustManager() {

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine) =
        trustOrPin(chain, engine.peerHost, engine.peerPort) { system.checkServerTrusted(chain, authType, engine) }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket) {
        val host = (socket as? SSLSocket)?.handshakeSession?.peerHost ?: socket.inetAddress?.hostAddress
        trustOrPin(chain, host, socket.port) { system.checkServerTrusted(chain, authType, socket) }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) =
        system.checkServerTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine) =
        system.checkClientTrusted(chain, authType, engine)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket) =
        system.checkClientTrusted(chain, authType, socket)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        system.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

    private inline fun trustOrPin(chain: Array<out X509Certificate>, host: String?, port: Int, systemCheck: () -> Unit) {
        try {
            systemCheck()
        } catch (refused: CertificateException) {
            val leaf = chain.firstOrNull() ?: throw refused
            val fingerprint = CertificatePins.fingerprintOf(leaf.encoded)
            if (host != null && CertificatePins.pinnedFor(host, port) == fingerprint) return
            throw UntrustedCertificateException(fingerprint, refused)
        }
    }

    companion object {
        /** Over the platform's own trust store, the one every other connection uses. */
        fun create(): PinningTrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return PinningTrustManager(factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().first())
        }
    }
}
