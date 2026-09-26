package app.server.network

import app.server.ByteBudget
import app.server.ClientConnection
import app.server.ServerLimits
import app.server.SyncplayServer
import app.utils.loggy
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The TCP server engine for iOS, built on Ktor sockets. It runs the app's own Syncplay server.
 *
 * Each accepted client gets two coroutines: a reader that passes whole lines to
 * [ClientConnection.handlePacket], and a writer that drains one channel. The single writer keeps
 * the replies in the order the server produced them. Do not launch a coroutine per line: a later
 * reply can then overtake an earlier one, and an error line can race the close that follows it.
 */
actual class ServerNetworkEngine actual constructor(
    private val server: SyncplayServer,
    private val scope: CoroutineScope
) {
    private var selectorManager: SelectorManager? = null
    private var acceptJob: Job? = null

    /**
     * The listening socket, kept so [stop] can close it directly.
     *
     * [stop] cancels the accept loop without waiting for it, so the loop's own `finally` can run
     * too late. The port would then still be bound when stop returns, and an immediate restart
     * could fail with "address already in use".
     */
    private var listeningSocket: ServerSocket? = null

    /**
     * Live client coroutines, guarded by [clientsLock]. The accept loop adds entries, and
     * whichever thread finishes a job removes it.
     */
    private val clientsLock = SynchronizedObject()
    private val clientJobs = mutableListOf<Job>()

    /** Sockets being served, joined or still in the handshake. See [ServerLimits.MAX_CLIENTS]. */
    private val liveClients = atomic(0)

    var isRunning: Boolean = false
        private set

    actual suspend fun startListening(port: Int) {
        val selector = SelectorManager(Dispatchers.IO)
        selectorManager = selector
        val serverSocket = aSocket(selector).tcp().bind("0.0.0.0", port)
        listeningSocket = serverSocket

        isRunning = true
        loggy("Server: Listening on port $port")

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                var consecutiveFailures = 0
                while (isActive) {
                    // One failed accept must not end the server. Without this catch, a single
                    // throw from accept() stops the whole listener silently.
                    val clientSocket = try {
                        serverSocket.accept()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        /* A repeating failure is not the same as a single one. A listening
                         * socket in a bad state, or a process with no file descriptors left,
                         * fails at once and forever. Retrying with no pause keeps a CPU core
                         * busy and writes a log line per attempt. So wait between attempts, and
                         * give up after a limit. */
                        consecutiveFailures++
                        loggy("Server: accept failed (${consecutiveFailures}): ${e.message}")
                        if (consecutiveFailures >= MAX_CONSECUTIVE_ACCEPT_FAILURES) {
                            loggy("Server: giving up on the listener after $consecutiveFailures failed accepts")
                            break
                        }
                        delay(ACCEPT_RETRY_DELAY_MS)
                        continue
                    }
                    consecutiveFailures = 0
                    if (liveClients.value >= ServerLimits.MAX_CLIENTS) {
                        loggy("Server: refusing a client, ${ServerLimits.MAX_CLIENTS} clients already connected")
                        runCatching { clientSocket.close() }
                        continue
                    }
                    serve(clientSocket)
                }
            } finally {
                runCatching { serverSocket.close() }
            }
        }
    }

    /** Connects one accepted socket to a reader, an ordered writer and the connection handler. */
    private fun serve(clientSocket: Socket) {
        val remoteAddress = clientSocket.remoteAddress.toString()
        loggy("Server: Client connected from $remoteAddress")

        val readChannel = clientSocket.openReadChannel()
        val writeChannel = clientSocket.openWriteChannel(autoFlush = true)
        liveClients.incrementAndGet()
        // Unlimited in count, bounded in bytes: a client that stops reading is dropped.
        val outbound = Channel<String>(capacity = Channel.UNLIMITED)
        val budget = ByteBudget()

        val connection = ClientConnection(
            server = server,
            sendFn = { line ->
                if (budget.take(line.length)) {
                    outbound.trySend(line)
                } else {
                    loggy("Server: dropping $remoteAddress, too much unsent output")
                    // Cancel, not close: the queued lines are the problem, so they are not sent.
                    outbound.cancel()
                }
            },
            // Close the queue, not the socket: the writer sends what is already queued, so a
            // client learns why it was dropped before the socket closes.
            dropFn = { outbound.close() }
        )

        val writer = scope.launch(Dispatchers.IO) {
            try {
                for (line in outbound) {
                    writeChannel.writeStringUtf8(line + "\r\n")
                    budget.give(line.length)
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
                liveClients.decrementAndGet()
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

        // Before the selector, and here rather than only in the accept loop's finally, so the
        // port is free by the time this returns.
        runCatching { listeningSocket?.close() }
        listeningSocket = null

        val jobs = synchronized(clientsLock) { clientJobs.toList().also { clientJobs.clear() } }
        for (job in jobs) job.cancel()

        selectorManager?.close()
        selectorManager = null

        loggy("Server: Stopped")
    }

    private companion object {
        /** Matches the Netty framers: a line with no newline in 64 KiB is not this protocol. */
        const val MAX_LINE_CHARS = 65536

        /** Pause between accept attempts once one has failed. */
        const val ACCEPT_RETRY_DELAY_MS = 250L

        /** After this many failures in a row, the listener gives up. */
        const val MAX_CONSECUTIVE_ACCEPT_FAILURES = 20
    }
}
