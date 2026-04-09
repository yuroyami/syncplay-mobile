package app.server.network

import app.server.ClientConnection
import app.server.SyncplayServer
import app.server.protocol.InboundMessageHandler
import app.utils.loggy
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * iOS Ktor-based TCP server engine.
 *
 * Uses Ktor raw sockets which work cross-platform on iOS without requiring
 * additional Swift code. Sufficient for local/LAN server hosting where
 * performance requirements are modest.
 */
actual class ServerNetworkEngine actual constructor(
    private val server: SyncplayServer,
    private val scope: CoroutineScope
) {
    private var selectorManager: SelectorManager? = null
    private var acceptJob: Job? = null
    private val clientJobs = mutableListOf<Job>()

    var isRunning: Boolean = false
        private set

    actual suspend fun startListening(port: Int) {
        selectorManager = SelectorManager(Dispatchers.IO)
        val serverSocket = aSocket(selectorManager!!).tcp().bind("0.0.0.0", port)

        isRunning = true
        loggy("Server: Listening on port $port")

        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val clientSocket = serverSocket.accept()
                val remoteAddress = clientSocket.remoteAddress.toString()
                loggy("Server: Client connected from $remoteAddress")

                val readChannel = clientSocket.openReadChannel()
                val writeChannel = clientSocket.openWriteChannel(autoFlush = true)

                val connection = ClientConnection(
                    server = server,
                    sendFn = { line ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                writeChannel.writeStringUtf8(line + "\r\n")
                            } catch (e: Exception) {
                                loggy("Server: Write failed to $remoteAddress: ${e.message}")
                            }
                        }
                    },
                    dropFn = {
                        try {
                            clientSocket.close()
                        } catch (_: Exception) {}
                    }
                )

                val job = scope.launch(Dispatchers.IO) {
                    try {
                        while (isActive) {
                            val line = readChannel.readUTF8Line() ?: break
                            if (line.isNotBlank()) {
                                InboundMessageHandler.handle(line, connection)
                            }
                        }
                    } catch (e: Exception) {
                        loggy("Server: Client error from $remoteAddress: ${e.message}")
                    } finally {
                        connection.onConnectionLost()
                        loggy("Server: Client disconnected from $remoteAddress")
                        try { clientSocket.close() } catch (_: Exception) {}
                    }
                }
                clientJobs.add(job)
            }
        }
    }

    actual fun stop() {
        isRunning = false

        acceptJob?.cancel()
        acceptJob = null

        for (job in clientJobs) {
            job.cancel()
        }
        clientJobs.clear()

        selectorManager?.close()
        selectorManager = null

        loggy("Server: Stopped")
    }
}
