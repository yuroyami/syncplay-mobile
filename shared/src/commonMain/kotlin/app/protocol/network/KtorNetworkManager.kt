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
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    /** The reader coroutine for the current socket, so teardown can actually stop it. */
    private var readerJob: Job? = null

    /**
     * Guards the handover between a finished dial and a teardown that arrived while it was still
     * dialling. Both write the same three fields from different coroutines.
     */
    private val socketLock = SynchronizedObject()

    /** False from the moment a teardown starts until the next dial claims the manager. */
    private var accepting = false

    /**
     * Opens the TCP socket and launches a reader coroutine that feeds each inbound line to
     * [handlePacket]. A failure to open throws, and [connect] turns that into onConnectionFailed.
     */
    override suspend fun connectSocket() {
        withContext(Dispatchers.IO) {
            synchronized(socketLock) { accepting = true }
            val sm = SelectorManager(Dispatchers.IO)
            /* Nothing is published until the dial returns. Assigning the selector first meant a
             * teardown landing mid-dial closed it, and then the dial finished and overwrote the
             * cleared fields with a socket bound to a dead selector, which nothing ever closed.
             *
             * The dial is also bounded here. socketTimeout is a read/write option, not a connect
             * deadline, so against a host that swallows the SYN this sat for the operating
             * system's own timeout (over a minute) while the 20 s handshake deadline tore the
             * connection state down underneath it. Netty and SwiftNIO both bound their dial. */
            val sock = try {
                withTimeout(CONNECT_TIMEOUT_MS) {
                    aSocket(sm)
                        .tcp()
                        .connect(
                            hostname = viewmodel.session.serverHost,
                            port = viewmodel.session.serverPort
                        ) {
                            socketTimeout = 10000
                        }
                }
            } catch (e: Throwable) {
                runCatching { sm.close() }
                throw e
            }
            val conn = sock.connection()

            val claimed = synchronized(socketLock) {
                if (accepting) {
                    selector = sm
                    socket = sock
                    connection = conn
                    true
                } else {
                    false
                }
            }
            if (!claimed) {
                // Torn down while we were dialling. This socket belongs to nobody.
                runCatching { sock.close() }
                runCatching { sm.close() }
                return@withContext
            }

            // The reader lives on IO, never the main dispatcher, and only reports the loss of
            // the socket it was reading: our own teardown of a previous socket is not news.
            readerJob = viewmodel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    // readLineStrict suspends until a full line arrives, draining the
                    // socket at line granularity with no artificial pacing. Pacing here
                    // (e.g. a per-line delay) lags join bursts and inflates RTT samples.
                    // The limit matches the Netty framers: a line with no newline in 64 KiB
                    // is not the Syncplay protocol.
                    while (true) {
                        val line = conn.input.readLineStrict(limit = MAX_LINE_BYTES) ?: break
                        // A line from a socket we have already replaced is not ours to act on.
                        if (socket !== sock) break
                        handlePacket(line)
                    }
                    lost(sock)
                } catch (e: CancellationException) {
                    // A cancelled reader still owns its socket; nothing else closes it.
                    runCatching { sock.close() }
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
        val (sock, sel) = synchronized(socketLock) {
            accepting = false
            val s = socket
            val l = selector
            socket = null
            connection = null
            selector = null
            s to l
        }
        // Closing the socket normally ends readLineStrict, but a reader parked on a socket that
        // never errors would otherwise outlive the connection it belongs to.
        readerJob?.cancel()
        readerJob = null
        runCatching { sock?.close() }
        // The selector owns a thread; one per connection attempt used to leak for the process life.
        runCatching { sel?.close() }
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
        /** Matches the Netty client's dial deadline. */
        const val CONNECT_TIMEOUT_MS = 10_000L

        const val MAX_LINE_BYTES = 65536L
    }
}
