package app.server

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import app.preferences.Preferences
import app.preferences.set
import app.preferences.value
import app.server.model.ServerConfig
import app.server.network.ServerNetworkEngine
import app.utils.generateTimestampMillis
import app.utils.getDeviceIpAddress
import app.utils.httpClient
import app.utils.ioDispatcher
import app.utils.loggy
import app.utils.platformCallback
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.atomicfu.atomic
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Process-lifetime owner of the hosted Syncplay server. The server deliberately does not live in
 * the screen's viewmodel, which is cleared on leaving the screen; everything here survives until
 * the process dies, and the Android foreground service keeps the process alive. Configuration
 * comes from the six server preferences, read once at start.
 */
enum class ServerStatus {
    Stopped, Starting, Running, Error
}

object ServerHostSession {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private const val LOG_CAP = 500

    val serverStatus = MutableStateFlow(ServerStatus.Stopped)

    /** One line of evidence for an error state, such as the port being taken. */
    val statusDetail = MutableStateFlow<ServerLogEvent?>(null)
    val connectedClients = MutableStateFlow(0)
    val deviceIpAddress = mutableStateOf<String?>(null)

    /** Public IP from an external service; null while loading or when unavailable. */
    val publicIpAddress = mutableStateOf<String?>(null)
    val publicIpLoading = mutableStateOf(false)

    /** Log lines for the screen, capped at [LOG_CAP], oldest dropped first. */
    val serverLogs = mutableStateListOf<ServerLogEntry>()

    /* Volatile: [scope] is ioDispatcher, so the start coroutine writes these and the stop
     * coroutine reads them on different threads. */
    @Volatile
    private var server: SyncplayServer? = null

    @Volatile
    private var engine: ServerNetworkEngine? = null

    /** Parent of the log and client-count collectors, cancelled on stop and on a failed start. */
    @Volatile
    private var collectorsJob: Job? = null

    /**
     * Which start attempt is the current one.
     *
     * Start and stop are both coroutines, so a stop can land in the middle of a start. Without
     * this the stop tore the half-built server down and set Stopped, and then the start coroutine
     * carried on and announced Running over it, leaving the panel claiming a server that had
     * already been shut down. Every stop retires the number the running start is holding.
     */
    private val startGeneration = atomic(0)

    fun startServer() {
        // Starting counts too: a second tap mid-start used to build a second server and orphan the first.
        if (serverStatus.value == ServerStatus.Running || serverStatus.value == ServerStatus.Starting) return

        val portInt = Preferences.SERVER_PORT.value().trim().toIntOrNull()
        if (portInt == null || portInt !in 1..65535) {
            fail(ServerLogEvent.InvalidPort(Preferences.SERVER_PORT.value().trim()))
            return
        }
        // The salt is minted once and kept: a fresh one per start would silently invalidate every
        // controlled-room password handed out by the previous run.
        val salt = Preferences.SERVER_SALT.value().ifEmpty {
            ServerConfig.generateSalt().also { minted -> scope.launch { Preferences.SERVER_SALT.set(minted) } }
        }
        val config = ServerConfig(
            port = portInt,
            password = Preferences.SERVER_PASSWORD.value(),
            isolateRooms = Preferences.SERVER_ISOLATE_ROOMS.value(),
            disableReady = Preferences.SERVER_DISABLE_READY.value(),
            disableChat = Preferences.SERVER_DISABLE_CHAT.value(),
            salt = salt,
            motd = Preferences.SERVER_MOTD.value(),
        )

        serverStatus.value = ServerStatus.Starting
        statusDetail.value = null

        val generation = startGeneration.incrementAndGet()
        scope.launch {
            var newServer: SyncplayServer? = null
            var newEngine: ServerNetworkEngine? = null
            try {
                newServer = SyncplayServer(config, scope)
                server = newServer

                collectorsJob = launch {
                    launch {
                        val cursor = ServerLogCursor()
                        newServer.serverLog.collect { entries ->
                            cursor.newSince(entries).forEach(::addEntry)
                        }
                    }
                    launch {
                        newServer.connectedClients.collect { count ->
                            connectedClients.value = count
                            if (serverStatus.value == ServerStatus.Running) platformCallback.serverClientsChanged(portInt, count)
                        }
                    }
                }

                newEngine = ServerNetworkEngine(newServer, scope)
                engine = newEngine
                newEngine.startListening(portInt)
                if (startGeneration.value != generation) {
                    // Stopped while we were binding. Undo this start rather than announcing it.
                    collectorsJob?.cancel()
                    collectorsJob = null
                    runCatching { newEngine.stop() }
                    runCatching { newServer.shutdown() }
                    server = null
                    engine = null
                    return@launch
                }
                serverStatus.value = ServerStatus.Running
                deviceIpAddress.value = getDeviceIpAddress()
                addLog(ServerLogEvent.Started(portInt))

                launch {
                    publicIpLoading.value = true
                    // The shared client has timeouts; a bare one hung forever offline and leaked on failure.
                    publicIpAddress.value = try {
                        httpClient.get("https://api.ipify.org").bodyAsText().trim().takeIf { it.isNotEmpty() }
                    } catch (_: Exception) {
                        null
                    }
                    publicIpLoading.value = false
                }
                platformCallback.serverServiceStart(portInt)
            } catch (e: Exception) {
                loggy("Server: Failed to start: ${e.stackTraceToString()}")
                // A failed start must not leave a half-built server assigned.
                collectorsJob?.cancel()
                collectorsJob = null
                runCatching { newEngine?.stop() }
                runCatching { newServer?.shutdown() }
                server = null
                engine = null
                val message = e.message.orEmpty()
                val taken = message.contains("in use", ignoreCase = true) || message.contains("EADDRINUSE", ignoreCase = true)
                fail(if (taken) ServerLogEvent.PortTaken(portInt) else ServerLogEvent.StartFailed(message))
            }
        }
    }

    fun stopServer() {
        // Retires whatever start is in flight, so a start that finishes after this does not
        // announce itself as Running.
        startGeneration.incrementAndGet()
        scope.launch {
            try {
                collectorsJob?.cancel()
                collectorsJob = null
                server?.shutdown()
                engine?.stop()
                server = null
                engine = null
                serverStatus.value = ServerStatus.Stopped
                statusDetail.value = null
                connectedClients.value = 0
                deviceIpAddress.value = null
                publicIpAddress.value = null
                publicIpLoading.value = false
                platformCallback.serverServiceStop()
                addLog(ServerLogEvent.Stopped)
            } catch (e: Exception) {
                loggy("Server: Error stopping: ${e.message}")
                addLog(ServerLogEvent.StopFailed(e.message.orEmpty()))
            }
        }
    }

    private fun fail(event: ServerLogEvent) {
        addLog(event)
        statusDetail.value = event
        serverStatus.value = ServerStatus.Error
    }

    private fun addEntry(entry: ServerLogEntry) {
        serverLogs.add(entry)
        while (serverLogs.size > LOG_CAP) serverLogs.removeAt(0)
    }

    /* The session's own lines are added straight to the screen list and never pass through a
     * cursor, but every entry carries a sequence number, so they count too. */
    private var ownLogSeq = 0L

    private fun addLog(event: ServerLogEvent) {
        addEntry(ServerLogEntry(seq = ++ownLogSeq, timestamp = generateTimestampMillis(), event = event))
    }
}

/**
 * A reader's place in a log that rotates.
 *
 * The server keeps the last 500 lines, so past 500 the list stops growing and a reader counting
 * how many it had already seen sees the same number forever and shows nothing new. The sequence
 * number does not rotate, so it is what the place is kept in.
 */
class ServerLogCursor {
    private var lastSeq = 0L

    fun newSince(entries: List<ServerLogEntry>): List<ServerLogEntry> {
        val fresh = entries.filter { it.seq > lastSeq }
        fresh.lastOrNull()?.let { lastSeq = it.seq }
        return fresh
    }
}
