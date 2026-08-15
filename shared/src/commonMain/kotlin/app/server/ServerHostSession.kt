package app.server

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import app.server.model.ServerConfig
import app.server.network.ServerNetworkEngine
import app.utils.getDeviceIpAddress
import app.utils.loggy
import app.utils.platformCallback
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-lifetime owner of the hosted Syncplay server.
 *
 * The server deliberately does NOT live inside [ServerViewmodel]: that ViewModel is scoped to
 * the ServerHost screen's navigation entry, so leaving the screen used to clear it and kill the
 * server instantly. All server state and coroutines live in this singleton instead; the
 * ViewModel is only a thin binding the screen reads from. Combined with the platform
 * foreground service (started via [platformCallback.serverServiceStart], which keeps the
 * Android process alive), the server keeps running until the user explicitly stops it.
 */
object ServerHostSession {

    /** Survives every screen/ViewModel teardown; lives until the process dies. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Configuration (editable by UI before starting) ---
    val port = mutableStateOf(ServerConfig.DEFAULT_PORT.toString())
    val password = mutableStateOf("")
    val motd = mutableStateOf("")
    val isolateRooms = mutableStateOf(true)
    val disableChat = mutableStateOf(false)
    val disableReady = mutableStateOf(false)

    // --- Server state ---
    val serverStatus = kotlinx.coroutines.flow.MutableStateFlow<ServerStatus>(ServerStatus.Stopped)
    val connectedClients = kotlinx.coroutines.flow.MutableStateFlow(0)
    val deviceIpAddress = mutableStateOf<String?>(null)

    /** Public IP fetched from external service, or null if unavailable/still loading. */
    val publicIpAddress = mutableStateOf<String?>(null)
    val publicIpLoading = mutableStateOf(false)

    /** Server event log entries for UI display. */
    val serverLogs = mutableStateListOf<ServerLogEntry>()

    private var server: SyncplayServer? = null
    private var engine: ServerNetworkEngine? = null

    /** Parent of the log/client-count collectors, cancelled on stop so a restarted server
     *  does not accumulate duplicate collectors on the dead instance's flows. */
    private var collectorsJob: Job? = null

    fun startServer() {
        if (serverStatus.value == ServerStatus.Running) return

        val portInt = port.value.toIntOrNull()
        if (portInt == null || portInt !in 1..65535) {
            addLog("Invalid port number")
            serverStatus.value = ServerStatus.Error
            return
        }

        val config = ServerConfig(
            port = portInt,
            password = password.value,
            isolateRooms = isolateRooms.value,
            disableReady = disableReady.value,
            disableChat = disableChat.value,
            motd = motd.value
        )

        serverStatus.value = ServerStatus.Starting

        scope.launch {
            try {
                val newServer = SyncplayServer(config, scope)
                server = newServer

                collectorsJob = launch {
                    launch {
                        newServer.serverLog.collect { entries ->
                            for (entry in entries.drop(serverLogs.size)) {
                                serverLogs.add(entry)
                            }
                        }
                    }

                    launch {
                        newServer.connectedClients.collect { count ->
                            connectedClients.value = count
                        }
                    }
                }

                val newEngine = ServerNetworkEngine(newServer, scope)
                engine = newEngine

                newEngine.startListening(portInt)
                serverStatus.value = ServerStatus.Running
                deviceIpAddress.value = getDeviceIpAddress()
                addLog("Server started on port $portInt")

                launch {
                    publicIpLoading.value = true
                    publicIpAddress.value = try {
                        val client = HttpClient()
                        val ip = client.get("https://api.ipify.org").bodyAsText().trim()
                        client.close()
                        ip
                    } catch (_: Exception) { null }
                    publicIpLoading.value = false
                }
                platformCallback.serverServiceStart(portInt)
            } catch (e: Exception) {
                loggy("Server: Failed to start: ${e.stackTraceToString()}")
                addLog("Failed to start: ${e.message}")
                serverStatus.value = ServerStatus.Error
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
                connectedClients.value = 0
                deviceIpAddress.value = null
                publicIpAddress.value = null
                publicIpLoading.value = false
                platformCallback.serverServiceStop()
                addLog("Server stopped")
            } catch (e: Exception) {
                loggy("Server: Error stopping: ${e.message}")
                addLog("Error stopping: ${e.message}")
            }
        }
    }

    private fun addLog(message: String) {
        serverLogs.add(
            ServerLogEntry(
                timestamp = app.utils.generateTimestampMillis(),
                message = message
            )
        )
    }
}
