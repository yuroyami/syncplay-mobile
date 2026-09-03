package app.protocol.network

import androidx.lifecycle.viewModelScope
import app.room.RoomViewmodel
import app.utils.loggy
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.utils.io.readLineStrict
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.protocol.models.ConnectionState

/**
 * Cross-platform [NetworkManager] over Ktor TCP sockets. Works on every platform but does
 * NOT support TLS, so it is the fallback engine; Netty (Android) / SwiftNIO (iOS) are used
 * when encryption is required.
 */
class KtorNetworkManager(viewmodel: RoomViewmodel) : NetworkManager(viewmodel) {
    override val engine = NetworkEngine.KTOR

    private var selector: SelectorManager? = null

    private var socket: Socket? = null

    private var connection: Connection? = null

    /**
     * Opens the TCP socket and launches a reader coroutine that feeds each inbound line to
     * [handlePacket]. A failure to open throws, and [connect] turns that into onConnectionFailed.
     */
    override suspend fun connectSocket() {
        withContext(Dispatchers.IO) {
            val sm = SelectorManager(Dispatchers.IO)
            selector = sm
            val sock = aSocket(sm)
                .tcp()
                .connect(
                    hostname = viewmodel.session.serverHost,
                    port = viewmodel.session.serverPort
                ) {
                    socketTimeout = 10000
                }
            socket = sock
            val conn = sock.connection()
            connection = conn

            // The reader lives on IO, never the main dispatcher, and only reports the loss of
            // the socket it was reading: our own teardown of a previous socket is not news.
            viewmodel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    // readLineStrict suspends until a full line arrives, draining the
                    // socket at line granularity with no artificial pacing. Pacing here
                    // (e.g. a per-line delay) lags join bursts and inflates RTT samples.
                    // The limit matches the Netty framers: a line with no newline in 64 KiB
                    // is not the Syncplay protocol.
                    while (true) {
                        val line = conn.input.readLineStrict(limit = MAX_LINE_BYTES) ?: break
                        handlePacket(line)
                    }
                    lost(sock)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    loggy("Ktor reader ended: ${e.message}")
                    lost(sock)
                }
            }
        }
    }

    /** A socket closed under us: a failed handshake or a dropped session, depending on where we were. */
    private fun lost(sock: Socket) {
        if (socket !== sock) return
        socket = null
        connection = null
        when (state.value) {
            ConnectionState.CONNECTING -> viewmodel.callback.onConnectionFailed()
            ConnectionState.CONNECTED -> viewmodel.callback.onDisconnected()
            else -> Unit
        }
    }

    /** Closes the socket (errors ignored) and the selector behind it, clearing every reference. */
    override fun terminateExistingConnection() {
        val sock = socket
        socket = null
        connection = null
        runCatching { sock?.close() }
        // The selector owns a thread; one per connection attempt used to leak for the process life.
        runCatching { selector?.close() }
        selector = null
    }

    /** Writes a UTF-8 string and flushes; a failure throws so the caller can retry or queue. */
    override suspend fun writeActualString(s: String) {
        val out = connection?.output ?: throw SocketGoneException()
        out.writeStringUtf8(s)
        out.flush()
    }

    override fun supportsTLS() = false

    /**
     * No-op: Ktor does not support opportunistic TLS upgrade (KTOR-6623), so encrypted
     * connections must use the Netty or SwiftNIO engine instead.
     */
    override suspend fun upgradeTls() {
        //TODO("Opportunistic TLS not yet supported by Ktor")
    }

    private companion object {
        const val MAX_LINE_BYTES = 65536L
    }
}
