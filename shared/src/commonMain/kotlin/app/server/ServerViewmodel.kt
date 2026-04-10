package app.server

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.Screen
import app.server.model.ServerConfig
import app.server.network.ServerNetworkEngine
import app.utils.getDeviceIpAddress
import app.utils.loggy
import app.utils.platformCallback
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the server hosting screen.
 *
 * Completely independent of [app.room.RoomViewmodel] and client-side state.
 * Manages server lifecycle, configuration, and observable state for the UI.
 */
class ServerViewmodel(
    val backStack: MutableList<Screen>
) : ViewModel() {

    // --- Configuration (editable by UI before starting) ---
    val port = mutableStateOf(ServerConfig.DEFAULT_PORT.toString())
    val password = mutableStateOf("")
    val motd = mutableStateOf("")
    val isolateRooms = mutableStateOf(true)
    val disableChat = mutableStateOf(false)
    val disableReady = mutableStateOf(false)

    // --- Server state ---
    val serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)
    val connectedClients = MutableStateFlow(0)
    val deviceIpAddress = mutableStateOf<String?>(null)
    /** Public IP fetched from external service, or null if unavailable/still loading. */
    val publicIpAddress = mutableStateOf<String?>(null)
    val publicIpLoading = mutableStateOf(false)

    /** Server event log entries for UI display. */
    val serverLogs = mutableStateListOf<ServerLogEntry>()

    private var _server: SyncplayServer? = null
    private var _engine: ServerNetworkEngine? = null

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

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val server = SyncplayServer(config, viewModelScope)
                _server = server

                // Observe server logs
                launch {
                    server.serverLog.collect { entries ->
                        for (entry in entries.drop(serverLogs.size)) {
                            serverLogs.add(entry)
                        }
                    }
                }

                // Observe connected client count
                launch {
                    server.connectedClients.collect { count ->
                        connectedClients.value = count
                    }
                }

                val engine = ServerNetworkEngine(server, viewModelScope)
                _engine = engine

                engine.startListening(portInt)
                serverStatus.value = ServerStatus.Running
                deviceIpAddress.value = getDeviceIpAddress()
                addLog("Server started on port $portInt")

                // Fetch public IP in the background
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _server?.shutdown()
                _engine?.stop()
                _server = null
                _engine = null
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

    override fun onCleared() {
        super.onCleared()
        _server?.shutdown()
        _engine?.stop()
        platformCallback.serverServiceStop()
    }
}

enum class ServerStatus {
    Stopped, Starting, Running, Error
}
