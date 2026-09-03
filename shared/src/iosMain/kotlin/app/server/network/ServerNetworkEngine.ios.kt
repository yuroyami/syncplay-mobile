package app.server.network

import app.server.ClientConnection
import app.server.SyncplayServer
import app.utils.loggy
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * iOS Ktor-based TCP server engine.
 *
 * Each accepted client gets two coroutines: a reader that hands whole lines to
 * [ClientConnection.handlePacket], and a writer draining one channel, so the server's replies
 * reach the socket in the order the server produced them. A per-line `launch` used to let a
 * later reply overtake an earlier one, and an error line raced the close that followed it.
 */
actual class ServerNetworkEngine actual constructor(
    private val server: SyncplayServer,
    private val scope: CoroutineScope
) {
    private var selectorManager: SelectorManager? = null
    private var acceptJob: Job? = null

    /** Live client coroutines. Guarded: entries are added on the accept loop and removed on whatever thread finishes one. */
    private val clientsLock = SynchronizedObject()
    private val clientJobs = mutableListOf<Job>()

    var isRunning: Boolean = false
        private set

    actual suspend fun startListening(port: Int) {
        val selector = SelectorManager(Dispatchers.IO)
        selectorManager = selector
        val serverSocket = aSocket(selector).tcp().bind("0.0.0.0", port)

        isRunning = true
        loggy("Server: Listening on port $port")

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    // One client failing to be accepted must not end the server. Before this, a
                    // single throw from accept() took the whole listener down silently.
                    val clientSocket = try {
                        serverSocket.accept()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        loggy("Server: accept failed: ${e.message}")
                        continue
                    }
                    serve(clientSocket)
                }
            } finally {
                runCatching { serverSocket.close() }
            }
        }
    }

    /** Wires one accepted socket to a reader, an ordered writer, and the shared connection handler. */
    private fun serve(clientSocket: Socket) {
        val remoteAddress = clientSocket.remoteAddress.toString()
        loggy("Server: Client connected from $remoteAddress")

        val readChannel = clientSocket.openReadChannel()
        val writeChannel = clientSocket.openWriteChannel(autoFlush = true)
        val outbound = Channel<String>(capacity = Channel.UNLIMITED)

        val connection = ClientConnection(
            server = server,
            sendFn = { line -> outbound.trySend(line) },
            // Closing the queue rather than the socket: the writer sends what is already queued,
            // so a client is told why it was dropped before the socket goes.
            dropFn = { outbound.close() }
        )

        val writer = scope.launch(Dispatchers.IO) {
            try {
                for (line in outbound) {
                    writeChannel.writeStringUtf8(line + "\r\n")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loggy("Server: Write failed to $remoteAddress: ${e.message}")
            } finally {
                runCatching { clientSocket.close() }
            }
        }

        val reader = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val line = readChannel.readUTF8Line(MAX_LINE_CHARS) ?: break
                    if (line.isNotBlank()) connection.handlePacket(line)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loggy("Server: Client error from $remoteAddress: ${e.message}")
            } finally {
                connection.onConnectionLost()
                outbound.close()
                loggy("Server: Client disconnected from $remoteAddress")
            }
        }

        track(writer)
        track(reader)
    }

    /** Remembers a client coroutine and forgets it when it ends, so the list cannot grow forever. */
    private fun track(job: Job) {
        synchronized(clientsLock) { clientJobs.add(job) }
        job.invokeOnCompletion { synchronized(clientsLock) { clientJobs.remove(job) } }
    }

    actual fun stop() {
        isRunning = false

        acceptJob?.cancel()
        acceptJob = null

        val jobs = synchronized(clientsLock) { clientJobs.toList().also { clientJobs.clear() } }
        for (job in jobs) job.cancel()

        selectorManager?.close()
        selectorManager = null

        loggy("Server: Stopped")
    }

    private companion object {
        /** Matches the Netty framers: a line with no newline in 64 KiB is not this protocol. */
        const val MAX_LINE_CHARS = 65536
    }
}
