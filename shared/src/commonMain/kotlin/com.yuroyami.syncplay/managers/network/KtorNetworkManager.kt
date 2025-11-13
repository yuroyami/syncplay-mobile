package com.yuroyami.syncplay.managers.network

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ktor-based implementation of NetworkManager for cross-platform TCP socket communication.
 *
 * Uses Ktor's networking APIs to provide TCP socket connectivity to Syncplay servers.
 * This implementation works across all platforms (JVM, Native, iOS, macOS) but currently
 * does not support TLS encryption.
 *
 * The manager maintains socket lifecycle, handles incoming data on a dedicated coroutine,
 * and provides UTF-8 string-based communication.
 *
 * @property viewmodel The parent RoomViewModel that owns this manager
 */
class KtorNetworkManager(viewmodel: RoomViewmodel) : NetworkManager(viewmodel) {
    override val engine = NetworkEngine.KTOR

    /**
     * The underlying TCP socket connection.
     */
    private var socket: Socket? = null

    /**
     * Ktor connection wrapper providing input/output channels.
     */
    private var connection: Connection? = null

    /**
     * Input channel for reading data from the socket.
     */
    private var input: ByteReadChannel? = null

    /**
     * Output channel for writing data to the socket.
     */
    private var output: ByteWriteChannel? = null

    /**
     * Establishes a TCP socket connection to the Syncplay server.
     *
     * Creates the socket, establishes the connection, and starts a coroutine to continuously
     * read incoming data line-by-line. Each complete line (packet) is passed to the packet handler.
     *
     * @throws Exception if connection fails (caught and triggers onConnectionFailed)
     */
    override suspend fun connectSocket() {
        withContext(Dispatchers.IO) {
            try {
                socket = aSocket(SelectorManager(Dispatchers.IO))
                    .tcp()
                    .connect(
                        hostname = viewmodel.sessionManager.session.serverHost,
                        port = viewmodel.sessionManager.session.serverPort
                    ) {
                        socketTimeout = 10000
                    }

                connection = socket!!.connection()

                input = connection?.input
                output = connection?.output

                viewmodel.viewModelScope.launch {
                    while (true) {
                        connection?.input?.awaitContent()
                        input?.readUTF8Line()?.let {
                            handlePacket(it)
                        }

                        delay(100)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                viewmodel.callbackManager.onConnectionFailed()
            }
        }
    }

    /**
     * Closes the socket connection and clears all references.
     *
     * Safely closes the socket ignoring any errors, then nullifies all
     * connection-related references to prevent memory leaks.
     */
    override fun terminateExistingConnection() {
        runCatching {
            socket?.close()
        }
        socket = null
        connection = null
        input = null
        output = null
    }

    /**
     * Writes a UTF-8 string to the socket.
     *
     * Flushes the output channel after writing to ensure immediate transmission.
     * Triggers disconnection callback on write failure.
     *
     * @param s The string to write to the socket
     */
    override suspend fun writeActualString(s: String) {
        try {
            connection?.output?.writeStringUtf8(s)
            connection?.output?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
            viewmodel.callbackManager.onDisconnected()
        }
    }

    /**
     * Indicates TLS support status.
     *
     * @return false - Ktor implementation does not currently support TLS
     */
    override fun supportsTLS() = false

    /**
     * TLS upgrade placeholder.
     *
     * Opportunistic TLS is not yet supported by this Ktor implementation.
     *
     * See:
     *
     * https://youtrack.jetbrains.com/issue/KTOR-6623
     */
    override fun upgradeTls() {
        //TODO("Opportunistic TLS not yet supported by Ktor")
    }
}