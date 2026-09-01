package app.server

import androidx.lifecycle.ViewModel
import app.Screen

/**
 * The host screen's binding. Every piece of server state and the whole lifecycle live in
 * [ServerHostSession], a process-lifetime singleton, so leaving the screen cannot kill the
 * server; that is why there is no onCleared here.
 */
class ServerViewmodel(
    val backStack: MutableList<Screen>
) : ViewModel() {

    val serverStatus = ServerHostSession.serverStatus
    val statusDetail = ServerHostSession.statusDetail
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
