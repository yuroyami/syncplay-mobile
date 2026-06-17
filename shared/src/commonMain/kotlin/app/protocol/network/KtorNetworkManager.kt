package app.protocol.network

import androidx.lifecycle.viewModelScope
import app.room.RoomViewmodel
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLineStrict
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cross-platform [NetworkManager] over Ktor TCP sockets. Works on every platform but does
 * NOT support TLS, so it is the fallback engine; Netty (Android) / SwiftNIO (iOS) are used
 * when encryption is required.
 */
class KtorNetworkManager(viewmodel: RoomViewmodel) : NetworkManager(viewmodel) {
    override val engine = NetworkEngine.KTOR

    private var socket: Socket? = null

    private var connection: Connection? = null

    private var input: ByteReadChannel? = null

    private var output: ByteWriteChannel? = null

    /**
     * Opens the TCP socket and launches a reader coroutine that feeds each inbound line to
     * [handlePacket]. Connection failure triggers onConnectionFailed.
     */
    override suspend fun connectSocket() {
        withContext(Dispatchers.IO) {
            try {
                socket = aSocket(SelectorManager(Dispatchers.IO))
                    .tcp()
                    .connect(
                        hostname = viewmodel.session.serverHost,
                        port = viewmodel.session.serverPort
                    ) {
                        socketTimeout = 10000
                    }

                connection = socket?.connection() ?: throw Exception("Ktor: Socket unobtainable (is null)")

                input = connection?.input
                output = connection?.output

                viewmodel.viewModelScope.launch {
                    try {
                        // readLineStrict suspends until a full line arrives, draining the
                        // socket at line granularity with no artificial pacing. Pacing here
                        // (e.g. a per-line delay) lags join bursts and inflates RTT samples.
                        while (true) {
                            val line = input?.readLineStrict() ?: break
                            handlePacket(line)
                        }
                        viewmodel.callback.onDisconnected()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        viewmodel.callback.onDisconnected()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                viewmodel.callback.onConnectionFailed()
            }
        }
    }

    /** Closes the socket (errors ignored) and clears all connection references. */
    override fun terminateExistingConnection() {
        runCatching {
            socket?.close()
        }
        socket = null
        connection = null
        input = null
        output = null
    }

    /** Writes a UTF-8 string and flushes; a write failure triggers onDisconnected. */
    override suspend fun writeActualString(s: String) {
        try {
            connection?.output?.writeStringUtf8(s)
            connection?.output?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
            viewmodel.callback.onDisconnected()
        }
    }

    override fun supportsTLS() = false

    /**
     * No-op: Ktor does not support opportunistic TLS upgrade (KTOR-6623), so encrypted
     * connections must use the Netty or SwiftNIO engine instead.
     */
    override suspend fun upgradeTls() {
        //TODO("Opportunistic TLS not yet supported by Ktor")
    }
}