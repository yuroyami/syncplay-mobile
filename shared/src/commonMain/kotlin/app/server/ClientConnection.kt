package app.server

import app.protocol.SyncplayProtocolHandler
import app.protocol.models.PingService
import app.server.model.ServerConfig.Companion.MAX_FILENAME_LENGTH
import app.server.model.ServerRoom
import app.server.model.ServerWatcher
import app.server.protocol.OutboundMessageBuilder
import app.utils.generateTimestampMillis
import app.utils.loggy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles the protocol for a single connected client on the server side.
 * Port of Python's SyncServerProtocol (syncplay-pc-src-master/syncplay/protocols.py).
 *
 * Implements [SyncplayProtocolHandler] — the common protocol contract shared with the client side.
 */
class ClientConnection(
    val server: SyncplayServer,
    private val sendFn: (String) -> Unit,
    private val dropFn: () -> Unit
) : SyncplayProtocolHandler {

    var watcher: ServerWatcher? = null
    private var _version: String? = null
    private var _features: JsonObject? = null
    private var _logged: Boolean = false
    var clientIgnoringOnTheFly: Int = 0
    var serverIgnoringOnTheFly: Int = 0

    private val _pingService = PingService()
    private var _clientLatencyCalculation: Double = 0.0
    private var _clientLatencyCalculationArrivalTime: Double = 0.0

    val isLogged: Boolean get() = _logged

    private fun sendMessage(json: JsonObject) {
        val line = Json.encodeToString(json)
        sendFn(line)
    }

    fun dropWithError(error: String) {
        loggy("Server: Dropping client - $error")
        sendMessage(OutboundMessageBuilder.buildError(error))
        dropFn()
    }

    private fun drop() {
        dropFn()
    }

    fun onConnectionLost() {
        server.removeWatcher(watcher)
    }

    fun getFeatures(): JsonObject? = _features
    fun getVersion(): String? = _version

    // --- SyncplayProtocolHandler implementation ---

    override fun onHelloReceived(data: JsonObject) {
        val username = data["username"]?.jsonPrimitive?.content?.trim()
        val serverPassword = data["password"]?.jsonPrimitive?.content
        val roomName = data["room"]?.jsonObject?.get("name")?.jsonPrimitive?.content?.trim()
        val version = data["realversion"]?.jsonPrimitive?.content
            ?: data["version"]?.jsonPrimitive?.content
        val features = data["features"]?.jsonObject

        if (username.isNullOrEmpty() || roomName.isNullOrEmpty() || version == null) {
            dropWithError("Hello command does not have enough parameters")
            return
        }

        if (!checkPassword(serverPassword)) return

        _version = version
        _features = features

        server.addWatcher(this, username, roomName)
        _logged = true
        sendHello(version)
    }

    private fun checkPassword(clientPassword: String?): Boolean {
        val serverHash = server.config.hashedPassword
        if (serverHash.isEmpty()) return true

        if (clientPassword.isNullOrEmpty()) {
            dropWithError("Password required but not provided")
            return false
        }
        if (clientPassword != serverHash) {
            dropWithError("Wrong password supplied")
            return false
        }
        return true
    }

    fun sendHello(clientVersion: String) {
        val w = watcher ?: return
        val room = w.room ?: return

        val features = server.getFeaturesJson()
        val motd = server.config.motd

        sendMessage(
            OutboundMessageBuilder.buildHelloResponse(
                username = w.name,
                roomName = room.name,
                clientVersion = clientVersion,
                features = features,
                motd = motd
            )
        )
    }

    override fun onStateReceived(data: JsonObject) {
        if (!requireLogged()) return

        var position: Double? = null
        var paused: Boolean? = null
        var doSeek: Boolean? = null

        data["ignoringOnTheFly"]?.jsonObject?.let { ignore ->
            ignore["server"]?.jsonPrimitive?.intOrNull?.let { serverVal ->
                if (serverIgnoringOnTheFly == serverVal) {
                    serverIgnoringOnTheFly = 0
                }
            }
            ignore["client"]?.jsonPrimitive?.intOrNull?.let { clientVal ->
                clientIgnoringOnTheFly = clientVal
            }
        }

        data["playstate"]?.jsonObject?.let { playstate ->
            position = playstate["position"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            paused = playstate["paused"]?.jsonPrimitive?.booleanOrNull
            doSeek = playstate["doSeek"]?.jsonPrimitive?.booleanOrNull
        }

        data["ping"]?.jsonObject?.let { ping ->
            val latencyCalc = ping["latencyCalculation"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val clientRtt = ping["clientRtt"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            _clientLatencyCalculation = ping["clientLatencyCalculation"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            _clientLatencyCalculationArrivalTime = currentTimeSeconds()
            _pingService.receiveMessage(latencyCalc.toLong(), clientRtt)
        }

        if (serverIgnoringOnTheFly == 0) {
            watcher?.updateState(position, paused, doSeek, _pingService.forwardDelay)
        }
    }

    override fun onSetReceived(data: JsonObject) {
        if (!requireLogged()) return
        val w = watcher ?: return

        for ((command, values) in data) {
            when (command) {
                "room" -> {
                    val roomName = values.jsonObject["name"]?.jsonPrimitive?.content
                    if (roomName != null) {
                        server.setWatcherRoom(w, roomName)
                    }
                }
                "file" -> {
                    w.setFile(values.jsonObject)
                }
                "controllerAuth" -> {
                    val password = values.jsonObject["password"]?.jsonPrimitive?.content
                    val room = values.jsonObject["room"]?.jsonPrimitive?.content
                    if (password != null) {
                        server.authRoomController(w, password, room)
                    }
                }
                "ready" -> {
                    val isReady = values.jsonObject["isReady"]?.jsonPrimitive?.boolean ?: false
                    val manuallyInitiated = values.jsonObject["manuallyInitiated"]?.jsonPrimitive?.booleanOrNull ?: false
                    val username = values.jsonObject["username"]?.jsonPrimitive?.content
                    server.setReady(w, isReady, manuallyInitiated, username)
                }
                "playlistChange" -> {
                    val files = values.jsonObject["files"]?.jsonArray
                        ?.map { it.jsonPrimitive.content } ?: emptyList()
                    server.setPlaylist(w, files)
                }
                "playlistIndex" -> {
                    val index = values.jsonObject["index"]?.jsonPrimitive?.intOrNull ?: 0
                    server.setPlaylistIndex(w, index)
                }
                "features" -> {
                    _features = values.jsonObject
                }
            }
        }
    }

    override fun onListReceived(data: JsonObject) {
        if (!requireLogged()) return
        sendList()
    }

    override fun onChatReceived(data: JsonObject) {
        if (!requireLogged()) return
        // Chat comes as {"Chat": "message"} from client, but the JsonObject routing
        // won't catch plain strings. We handle this specially.
    }

    /**
     * Special handler for chat since the client sends `{"Chat": "message"}` (a string, not object).
     * Called directly from handleMessage before routeMessage.
     */
    fun handleChatString(message: String) {
        if (!requireLogged()) return
        if (!server.config.disableChat) {
            server.sendChat(watcher ?: return, message)
        }
    }

    override fun onTLSReceived(data: JsonObject) {
        // Mobile server does not support TLS. Always respond false.
        sendMessage(OutboundMessageBuilder.buildTLSResponse(supported = false))
    }

    override fun onErrorReceived(data: JsonObject) {
        val message = data["message"]?.jsonPrimitive?.content ?: "Unknown error"
        dropWithError(message)
    }

    // --- Outbound messages ---

    fun sendState(position: Double, paused: Boolean, doSeek: Boolean, setBy: ServerWatcher?, forced: Boolean) {
        val processingTime = if (_clientLatencyCalculationArrivalTime > 0) {
            currentTimeSeconds() - _clientLatencyCalculationArrivalTime
        } else 0.0

        if (forced) {
            serverIgnoringOnTheFly += 1
        }

        val clientLatCalc = if (_clientLatencyCalculation > 0) _clientLatencyCalculation else null
        if (_clientLatencyCalculation > 0) _clientLatencyCalculation = 0.0

        val shouldSend = serverIgnoringOnTheFly == 0 || forced

        if (shouldSend) {
            sendMessage(
                OutboundMessageBuilder.buildStateResponse(
                    position = position,
                    paused = paused,
                    doSeek = doSeek,
                    setByName = setBy?.name,
                    latencyCalculation = currentTimeSeconds(),
                    serverRtt = _pingService.rtt,
                    clientLatencyCalculation = clientLatCalc,
                    processingTime = processingTime,
                    serverIgnoringOnTheFly = serverIgnoringOnTheFly,
                    clientIgnoringOnTheFly = clientIgnoringOnTheFly
                )
            )
        }

        if (clientIgnoringOnTheFly != 0 && !forced) {
            clientIgnoringOnTheFly = 0
        }
    }

    fun sendUserSetting(username: String, room: ServerRoom?, file: JsonObject?, event: Map<String, Any>?) {
        sendMessage(OutboundMessageBuilder.buildSetUser(username, room, file, event))
    }

    fun sendSetReady(username: String, isReady: Boolean?, manuallyInitiated: Boolean, setByUsername: String? = null) {
        sendMessage(OutboundMessageBuilder.buildSetReady(username, isReady, manuallyInitiated, setByUsername))
    }

    fun sendPlaylist(username: String, files: List<String>) {
        sendMessage(OutboundMessageBuilder.buildPlaylistChange(username, files))
    }

    fun sendPlaylistIndex(username: String, index: Int) {
        sendMessage(OutboundMessageBuilder.buildPlaylistIndex(username, index))
    }

    fun sendNewControlledRoom(roomName: String, password: String) {
        sendMessage(OutboundMessageBuilder.buildNewControlledRoom(roomName, password))
    }

    fun sendControlledRoomAuthStatus(success: Boolean, username: String, roomName: String) {
        sendMessage(OutboundMessageBuilder.buildControllerAuth(success, username, roomName))
    }

    fun sendList() {
        val w = watcher ?: return
        val watchers = server.getAllWatchersForUser(w)
        sendMessage(OutboundMessageBuilder.buildListResponse(watchers))
    }

    fun sendChatMessage(messageDict: JsonObject) {
        sendMessage(buildJsonObjectWrapper("Chat", messageDict))
    }

    private fun buildJsonObjectWrapper(key: String, value: JsonObject): JsonObject {
        return kotlinx.serialization.json.buildJsonObject { put(key, value) }
    }

    private fun requireLogged(): Boolean {
        if (!_logged) {
            dropWithError("Not authenticated")
            return false
        }
        return true
    }

    private fun currentTimeSeconds(): Double = generateTimestampMillis() / 1000.0
}
