package app.server

import androidx.lifecycle.ViewModel
import app.Screen

/**
 * ViewModel for the server hosting screen. Deliberately a THIN BINDING: every piece of server
 * state and the whole lifecycle live in [ServerHostSession] (a process-lifetime singleton), so
 * the hosted server survives leaving this screen. This ViewModel is cleared whenever the user
 * navigates away, and clearing it must not touch the server, hence the absence of onCleared().
 */
class ServerViewmodel(
    val backStack: MutableList<Screen>
) : ViewModel() {

    // --- Configuration (editable by UI before starting) ---
    val port = ServerHostSession.port
    val password = ServerHostSession.password
    val motd = ServerHostSession.motd
    val isolateRooms = ServerHostSession.isolateRooms
    val disableChat = ServerHostSession.disableChat
    val disableReady = ServerHostSession.disableReady

    // --- Server state ---
    val serverStatus = ServerHostSession.serverStatus
    val connectedClients = ServerHostSession.connectedClients
    val deviceIpAddress = ServerHostSession.deviceIpAddress
    val publicIpAddress = ServerHostSession.publicIpAddress
    val publicIpLoading = ServerHostSession.publicIpLoading
    val serverLogs = ServerHostSession.serverLogs

    fun startServer() = ServerHostSession.startServer()

    fun stopServer() = ServerHostSession.stopServer()
}

enum class ServerStatus {
    Stopped, Starting, Running, Error
}
