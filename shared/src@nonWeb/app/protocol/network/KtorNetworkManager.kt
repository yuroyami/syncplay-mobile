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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import app.protocol.models.ConnectionState

/**
 * [NetworkManager] over Ktor TCP sockets, for Android, iOS and desktop (not the browser). A
 * network manager carries the Syncplay protocol lines between the room and the server. This one
 * does not support TLS, so it is the fallback network engine. Netty (Android and desktop) and
 * SwiftNIO (iOS) handle encrypted connections.
 */
class KtorNetworkManager(viewmodel: RoomViewmodel) : NetworkManager(viewmodel) {
    override val engine = NetworkEngine.KTOR

    private var selector: SelectorManager? = null

    private var socket: Socket? = null

    private var connection: Connection? = null

    /** The reader coroutine for the current socket, kept so that teardown can stop it. */
    private var readerJob: Job? = null

    /**
     * Guards the handover between a finished dial and a teardown that arrived during the dial.
     * Both write the same four fields from different coroutines.
     */
    private val socketLock = SynchronizedObject()

    /**
     * Which dial owns the manager.
     *
     * A counter, not a flag. With a flag, a slow dial can still claim the fields after a teardown
     * cleared them and a second dial filled them in. Nothing then references the second dial's
     * socket and selector, and the selector owns a thread. Every dial takes a number and
     * publishes only if it is still the current one.
     */
    private var dialSerial = 0

    /**
     * Opens the TCP socket and launches a reader coroutine that passes each inbound line to
     * [handlePacket]. A failure to open throws, and [connect] turns that into onConnectionFailed.
     */
    override suspend fun connectSocket() {
        withContext(Dispatchers.IO) {
            val serial = synchronized(socketLock) { ++dialSerial }
            val sm = SelectorManager(Dispatchers.IO)
            /* Publish nothing until the dial returns. If the selector is assigned first, a
             * teardown during the dial closes it. The dial then finishes and fills the cleared
             * fields with a socket bound to a dead selector, which nothing ever closes.
             *
             * The dial also has its own time limit here. socketTimeout is a read/write option, not
             * a connect deadline. Against a host that drops the SYN, the dial would wait for the
             * operating system's own timeout (over a minute), while the 20 s handshake deadline
             * tears the connection state down under it. Netty and SwiftNIO also limit their
             * dial. */
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

            // The reader runs on IO, never on the main dispatcher, and reports only the loss of the
            // socket that it reads: closing a previous socket ourselves is expected. It starts
            // lazily, so it can be published under the same lock as the socket it reads.
            val reader = viewmodel.viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    // readLineStrict suspends until a full line arrives, so the socket is read
                    // line by line with no artificial pacing. Pacing here (for example a per-line
                    // delay) slows join bursts and inflates the RTT (round-trip time) samples.
                    // The limit matches the Netty framers: a line with no newline in 64 KiB
                    // is not the Syncplay protocol.
                    while (true) {
                        val line = conn.input.readLineStrict(limit = MAX_LINE_BYTES) ?: break
                        // Stop on a line from a socket that a newer connection has replaced.
                        if (socket !== sock) break
                        handlePacket(line)
                    }
                    lost(sock)
                } catch (e: CancellationException) {
                    // Close the socket here too. The teardown that cancels this reader also closes
                    // it, but a reader cancelled in any other way would leave it open.
                    runCatching { sock.close() }
                    throw e
                } catch (e: Exception) {
                    loggy("Ktor reader ended: ${e.message}")
                    lost(sock)
                }
            }

            val claimed = synchronized(socketLock) {
                if (dialSerial == serial) {
                    selector = sm
                    socket = sock
                    connection = conn
                    readerJob = reader
                    true
                } else {
                    false
                }
            }
            if (!claimed) {
                // A teardown or a newer dial replaced this one during the dial, so nothing owns
                // this socket.
                reader.cancel()
                runCatching { sock.close() }
                runCatching { sm.close() }
                // Throw, do not return: connect() reads a normal return as a live socket, sends
                // Hello into nothing, and waits in CONNECTING until the handshake deadline. The
                // fallback dial can act on a throw.
                throw SocketGoneException()
            }
            reader.start()
        }
    }

    /**
     * Handles a socket that closed from the other side: a failed handshake or a dropped session,
     * depending on the connection state.
     */
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
        val (sock, sel, reader) = synchronized(socketLock) {
            // Retires whatever dial is in flight, so it cannot publish over this teardown.
            dialSerial++
            val s = socket
            val l = selector
            val r = readerJob
            socket = null
            connection = null
            selector = null
            readerJob = null
            Triple(s, l, r)
        }
        // Closing the socket normally ends readLineStrict, but a reader waiting on a socket that
        // never errors would otherwise outlive its connection.
        reader?.cancel()
        runCatching { sock?.close() }
        // The selector owns a thread. Without this close, every connection attempt leaks one
        // thread for the life of the process.
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
     * No-op: Ktor does not support an opportunistic TLS upgrade (KTOR-6623), so encrypted
     * connections must use the Netty or SwiftNIO network engine.
     */
    override suspend fun upgradeTls() {
    }

    private companion object {
        /** Matches the Netty client's dial deadline. */
        const val CONNECT_TIMEOUT_MS = 10_000L

        const val MAX_LINE_BYTES = 65536L
    }
}
