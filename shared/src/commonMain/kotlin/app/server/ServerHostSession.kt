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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class ServerStatus {
    Stopped, Starting, Running, Error
}

/**
 * The process-lifetime owner of the hosted Syncplay server. The server does not live in the
 * screen's viewmodel on purpose, because that viewmodel is cleared when the user leaves the
 * screen. Everything here lives until the process dies, and the Android foreground service keeps
 * the process alive. The configuration comes from the server preferences, read once at start.
 */
object ServerHostSession {

    /**
     * The handler is the last guard: a failure in the server's work is logged, never handed to the
     * thread's default handler, which ends the app in an Android release build.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + ioDispatcher + CoroutineExceptionHandler { _, e -> loggy("Server: unexpected failure: $e") },
    )
    private const val LOG_CAP = 500

    val serverStatus = MutableStateFlow(ServerStatus.Stopped)

    /** The event that explains an error state, such as a port that is already taken. */
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
     * Start and stop are both coroutines, so a stop can arrive in the middle of a start. Without
     * this counter, the stop would tear down the half-built server and set Stopped, and then the
     * start coroutine would continue and announce Running. The panel would then show a server
     * that is already shut down. Every stop retires the number that the running start holds.
     */
    private val startGeneration = atomic(0)

    fun startServer() {
        // Starting counts too: a second tap during a start would build a second server and
        // orphan the first.
        if (serverStatus.value == ServerStatus.Running || serverStatus.value == ServerStatus.Starting) return

        val portInt = Preferences.SERVER_PORT.value().trim().toIntOrNull()
        if (portInt == null || portInt !in 1..65535) {
            fail(ServerLogEvent.InvalidPort(Preferences.SERVER_PORT.value().trim()))
            return
        }
        // The salt is created once and kept: a new one per start would silently invalidate every
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
                    // A stop arrived during the bind. Undo this start instead of announcing it.
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
                    // The shared client has timeouts. A bare client would hang forever offline and
                    // leak on failure.
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

    /* The session's own lines go straight to the screen list and never pass through a cursor.
     * [ServerLogEntry] still needs a sequence number, so this counter numbers them. */
    private var ownLogSeq = 0L

    private fun addLog(event: ServerLogEvent) {
        addEntry(ServerLogEntry(seq = ++ownLogSeq, timestamp = generateTimestampMillis(), event = event))
    }
}

/**
 * A reader's place in a log that rotates.
 *
 * The server keeps the last 500 lines. Past 500, the list stops growing, so a reader that counts
 * the lines it has seen gets the same number forever and shows nothing new. The sequence number
 * does not rotate, so the cursor keeps its place by that number.
 */
class ServerLogCursor {
    private var lastSeq = 0L

    fun newSince(entries: List<ServerLogEntry>): List<ServerLogEntry> {
        val fresh = entries.filter { it.seq > lastSeq }
        fresh.lastOrNull()?.let { lastSeq = it.seq }
        return fresh
    }
}
