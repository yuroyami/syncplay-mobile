package app.server.tls

import app.design.DesignHarness
import app.preferences.Preferences.TLS_PINS
import app.preferences.set
import app.protocol.network.CertificatePins
import app.protocol.network.PinningTrustManager
import app.protocol.network.UntrustedCertificateException
import app.server.SyncplayServer
import app.server.model.ServerConfig
import app.server.network.ServerNetworkEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The built-in server switches a socket to TLS when the host turned it on, and a joining app
 * trusts the host's certificate only by the fingerprint that the person accepted.
 */
class ServerTlsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val port = ServerSocket(0).use { it.localPort }

    @BeforeTest
    fun start() = runBlocking {
        DesignHarness.initDatastore()
        TLS_PINS.set("")
    }

    @AfterTest
    fun stop() = runBlocking {
        TLS_PINS.set("")
        scope.cancel()
    }

    private fun <T> withServer(offerTls: Boolean, test: () -> T): T = runBlocking {
        val engine = ServerNetworkEngine(SyncplayServer(ServerConfig(offerTls = offerTls), scope), scope)
        engine.startListening(port)
        try {
            test()
        } finally {
            engine.stop()
        }
    }

    /** A plain socket that has asked for TLS, with the server's one-line answer. */
    private fun askForTls(): Pair<Socket, String> {
        // Not inside apply: there `port` would be the socket's own port, 0 before it connects.
        val socket = Socket()
        socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
        socket.soTimeout = 5_000
        socket.getOutputStream().write("{\"TLS\": {\"startTLS\": \"send\"}}\r\n".toByteArray())
        socket.getOutputStream().flush()
        val answer = StringBuilder()
        while (true) {
            val byte = socket.getInputStream().read()
            if (byte == -1 || byte == '\n'.code) break
            answer.append(byte.toChar())
        }
        return socket to answer.toString()
    }

    private fun upgrade(socket: Socket): SSLSocket {
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(PinningTrustManager.create()), null) }
        val tls = context.socketFactory.createSocket(socket, "127.0.0.1", port, true) as SSLSocket
        tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        tls.startHandshake()
        return tls
    }

    @Test
    fun aServerWithTlsOffAnswersNo() = withServer(offerTls = false) {
        val (socket, answer) = askForTls()
        socket.close()
        assertTrue("\"false\"" in answer, answer)
    }

    @Test
    fun anUnseenCertificateIsRefusedWithItsFingerprint() = withServer(offerTls = true) {
        val (socket, answer) = askForTls()
        assertTrue("\"true\"" in answer, answer)
        val failure = assertFailsWith<SSLException> { upgrade(socket) }
        val untrusted = assertNotNull(generateSequence<Throwable>(failure) { it.cause }.filterIsInstance<UntrustedCertificateException>().firstOrNull())
        assertEquals(ServerTls.identity.fingerprint, untrusted.fingerprint)
        socket.close()
    }

    @Test
    fun aPinnedCertificateCarriesTheWholeSession() = withServer(offerTls = true) {
        runBlocking { CertificatePins.pin("127.0.0.1", port, ServerTls.identity.fingerprint) }
        val (socket, answer) = askForTls()
        assertTrue("\"true\"" in answer, answer)
        val tls = upgrade(socket)
        tls.outputStream.write("{\"Hello\": {\"username\": \"alice\", \"room\": {\"name\": \"lobby\"}, \"version\": \"1.2.255\", \"realversion\": \"1.7.3\"}}\r\n".toByteArray())
        tls.outputStream.flush()
        // The server may send other lines first, such as the new user's ready state.
        val reader = tls.inputStream.bufferedReader()
        val lines = mutableListOf<String>()
        // Reads until the socket goes quiet: the read timeout ends the loop.
        runCatching { repeat(10) { lines += reader.readLine() ?: return@runCatching } }
        assertTrue(lines.any { it.contains("\"Hello\"") }, lines.joinToString("\n"))
        tls.close()
    }
}
