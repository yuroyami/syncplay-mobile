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
import app.utils.loggy
import app.utils.platformCallback
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.server_host_port_taken

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    private var server: SyncplayServer? = null
    private var engine: ServerNetworkEngine? = null

    /** Parent of the log and client-count collectors, cancelled on stop and on a failed start. */
    private var collectorsJob: Job? = null

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

        scope.launch {
            var newServer: SyncplayServer? = null
            var newEngine: ServerNetworkEngine? = null
            try {
                newServer = SyncplayServer(config, scope)
                server = newServer

                collectorsJob = launch {
                    launch {
                        /* Server lines are consumed by their own count. Dropping by the screen
                         * list's size mixed in the session's lines and swallowed a restarted
                         * server's log. */
                        var consumed = 0
                        newServer.serverLog.collect { entries ->
                            for (entry in entries.drop(consumed)) addEntry(entry)
                            consumed = entries.size
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

    private fun addLog(event: ServerLogEvent) {
        addEntry(ServerLogEntry(timestamp = generateTimestampMillis(), event = event))
    }
}
