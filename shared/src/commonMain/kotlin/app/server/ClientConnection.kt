package app.server

import app.protocol.models.PingService
import app.protocol.models.RoomFeatures
import app.protocol.server.Chat
import app.protocol.server.Error
import app.protocol.server.FileData
import app.protocol.server.Hello
import app.protocol.server.IgnoringOnTheFlyData
import app.protocol.server.ListResponse
import app.protocol.server.PingData
import app.protocol.server.PlaystateData
import app.protocol.server.Room
import app.protocol.server.ServerMessage
import app.protocol.server.Set
import app.protocol.server.State
import app.protocol.server.TLS
import app.protocol.syncplayJson
import app.server.model.ServerConfig.Companion.MAX_FILENAME_LENGTH
import app.server.model.ServerRoom
import app.server.model.ServerWatcher
import app.server.protocol.incoming.IncomingMessageDeserializer
import app.utils.generateTimestampMillis
import app.utils.loggy
import kotlinx.serialization.SerializationException
import SyncplayMobile.shared.BuildConfig

/**
 * Server-side per-client protocol handler. Port of Python's `SyncServerProtocol`
 * (`syncplay-pc-src-master/syncplay/protocols.py`).
 *
 * Inbound traffic is decoded by [IncomingMessageDeserializer] into the typed
 * [app.server.protocol.incoming.IncomingMessage] hierarchy, then dispatched here via
 * `handlePacket`. Outbound traffic constructs instances of the same
 * `app.protocol.server.*` data classes the client decodes — the wire models, Kotlinx
 * Serialization plumbing, and `JsonContentPolymorphicSerializer` dispatch pattern are all
 * shared between the two sides.
 */
class ClientConnection(
    val server: SyncplayServer,
    private val sendFn: (String) -> Unit,
    private val dropFn: () -> Unit
) {

    var watcher: ServerWatcher? = null
    private var _version: String? = null
    private var _features: RoomFeatures? = null
    private var _logged: Boolean = false
    var clientIgnoringOnTheFly: Int = 0
    var serverIgnoringOnTheFly: Int = 0

    private val _pingService = PingService()
    private var _clientLatencyCalculation: Double = 0.0
    private var _clientLatencyCalculationArrivalTime: Double = 0.0

    val isLogged: Boolean get() = _logged

    /** Encodes a typed [ServerMessage] and writes it to the wire. */
    private inline fun <reified T : ServerMessage> sendTyped(message: T) {
        sendFn(syncplayJson.encodeToString(message))
    }

    fun dropWithError(error: String) {
        loggy("Server: Dropping client - $error")
        sendTyped(Error(Error.ErrorData(message = error)))
        dropFn()
    }

    fun onConnectionLost() {
        server.removeWatcher(watcher)
    }

    fun getFeatures(): RoomFeatures? = _features
    fun getVersion(): String? = _version

    /**
     * Routes a raw wire-JSON line through [IncomingMessageDeserializer] to the typed
     * [app.server.protocol.incoming.IncomingMessage] hierarchy and dispatches its `handle()`.
     *
     * Mirrors the client-side `NetworkManager.handlePacket` pipeline — same shape, same
     * Kotlinx Serialization plumbing, just rooting at the server end.
     */
    suspend fun handlePacket(jsonString: String) {
        if (BuildConfig.DEBUG_SYNCPLAY_PROTOCOL) loggy("**CLIENT** $jsonString")
        try {
            syncplayJson.decodeFromString(
                deserializer = IncomingMessageDeserializer,
                string = jsonString
            ).handle(this)
        } catch (e: SerializationException) {
            loggy("Server: failed to decode line '$jsonString' — ${e.message}")
            dropWithError("Failed to parse message")
        }
    }

    // -----------------------------------------------------------
    // INBOUND — entry points from `IncomingMessage.handle(connection)`
    // -----------------------------------------------------------

    /**
     * Validated `Hello` payload from [app.server.protocol.incoming.IncomingHello].
     * Authenticates the client, registers the watcher, and sends the `Hello` response.
     */
    fun acceptHello(
        username: String,
        roomName: String,
        clientVersion: String,
        clientPassword: String?,
        features: RoomFeatures
    ) {
        if (!checkPassword(clientPassword)) return

        _version = clientVersion
        _features = features

        server.addWatcher(this, username, roomName)
        _logged = true
        sendHello(clientVersion)
    }

    /** Updates the watcher's pause/position/seek state and ping timing. */
    fun handleIncomingState(state: State.StateData) {
        if (!requireLogged()) return

        state.ignoringOnTheFly?.let { ignore ->
            ignore.server?.let { serverVal ->
                if (serverIgnoringOnTheFly == serverVal) {
                    serverIgnoringOnTheFly = 0
                }
            }
            ignore.client?.let { clientVal ->
                clientIgnoringOnTheFly = clientVal
            }
        }

        val playstate = state.playstate
        val position = playstate?.position ?: 0.0
        val paused = playstate?.paused
        val doSeek = playstate?.doSeek

        state.ping?.let { ping ->
            val latencyCalc = ping.latencyCalculation ?: 0.0
            val clientRtt = ping.clientRtt ?: 0.0
            _clientLatencyCalculation = ping.clientLatencyCalculation ?: 0.0
            _clientLatencyCalculationArrivalTime = currentTimeSeconds()
            _pingService.receiveMessage(latencyCalc.toLong(), clientRtt)
        }

        if (serverIgnoringOnTheFly == 0) {
            watcher?.updateState(position, paused, doSeek, _pingService.forwardDelay)
        }
    }

    fun handleSetRoom(room: Room) {
        if (!requireLogged()) return
        val w = watcher ?: return
        server.setWatcherRoom(w, room.name)
    }

    fun handleSetFile(file: FileData) {
        if (!requireLogged()) return
        val w = watcher ?: return
        w.setFile(truncateFileName(file))
    }

    fun handleSetReady(ready: Set.ReadyData) {
        if (!requireLogged()) return
        val w = watcher ?: return
        server.setReady(
            watcher = w,
            isReady = ready.isReady ?: false,
            manuallyInitiated = ready.manuallyInitiated ?: false,
            username = ready.username
        )
    }

    fun handleSetControllerAuth(auth: Set.ControllerAuthResponse) {
        if (!requireLogged()) return
        val w = watcher ?: return
        val password = auth.password?.takeIf { it.isNotEmpty() } ?: return
        server.authRoomController(w, password, auth.room)
    }

    fun handleSetPlaylistChange(change: Set.PlaylistChangeData) {
        if (!requireLogged()) return
        val w = watcher ?: return
        server.setPlaylist(w, change.files ?: emptyList())
    }

    fun handleSetPlaylistIndex(index: Set.PlaylistIndexData) {
        if (!requireLogged()) return
        val w = watcher ?: return
        server.setPlaylistIndex(w, index.index ?: 0)
    }

    fun handleSetFeatures(features: RoomFeatures) {
        _features = features
    }

    fun handleListRequest() {
        if (!requireLogged()) return
        sendList()
    }

    /** Plain-string Chat from the client (asymmetric: `{"Chat": "message"}`). */
    fun handleChatString(message: String) {
        if (!requireLogged()) return
        if (!server.config.disableChat) {
            server.sendChat(watcher ?: return, message)
        }
    }

    fun handleTlsRequest(tls: TLS.TLSData) {
        // Mobile server has no TLS-cert support — always answers "false".
        sendTyped(TLS(TLS.TLSData(startTLS = "false")))
    }

    fun handleClientError(message: String?) {
        dropWithError(message ?: "Unknown error")
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

    private fun truncateFileName(file: FileData): FileData {
        val name = file.name ?: return file
        if (name.length <= MAX_FILENAME_LENGTH) return file
        return file.copy(name = name.take(MAX_FILENAME_LENGTH))
    }

    // -----------------------------------------------------------
    // OUTBOUND — typed messages encoded via `syncplayJson`
    // -----------------------------------------------------------

    fun sendHello(clientVersion: String) {
        val w = watcher ?: return
        val room = w.room ?: return

        val features = server.buildServerFeatures()
        val motd = server.config.motd

        sendTyped(
            Hello(
                Hello.HelloData(
                    username = w.name,
                    room = Room(name = room.name),
                    version = clientVersion,
                    realversion = "1.7.3",
                    features = features,
                    motd = motd
                )
            )
        )
    }

    /**
     * Writes a `State` packet to the wire, applying the same
     * `ignoringOnTheFly` / forced-update bookkeeping as the python reference server.
     */
    fun sendState(
        position: Double,
        paused: Boolean,
        doSeek: Boolean,
        setBy: ServerWatcher?,
        forced: Boolean
    ) {
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
            val ignoring = if (serverIgnoringOnTheFly != 0 || clientIgnoringOnTheFly != 0) {
                IgnoringOnTheFlyData(
                    server = serverIgnoringOnTheFly.takeIf { it != 0 },
                    client = clientIgnoringOnTheFly.takeIf { it != 0 }
                )
            } else null

            sendTyped(
                State(
                    State.StateData(
                        playstate = PlaystateData(
                            position = position,
                            paused = paused,
                            doSeek = doSeek,
                            setBy = setBy?.name
                        ),
                        ping = PingData(
                            latencyCalculation = currentTimeSeconds(),
                            serverRtt = _pingService.rtt,
                            clientLatencyCalculation = clientLatCalc?.let { it + processingTime }
                        ),
                        ignoringOnTheFly = ignoring
                    )
                )
            )
        }

        if (clientIgnoringOnTheFly != 0 && !forced) {
            clientIgnoringOnTheFly = 0
        }
    }

    /**
     * Broadcasts a user state change (join/leave/file/room) inside a `Set.user` envelope.
     */
    fun sendUserSetting(
        username: String,
        room: ServerRoom?,
        file: FileData?,
        event: Set.UserEvent?
    ) {
        sendTyped(
            Set(
                Set.SetData(
                    user = mapOf(
                        username to Set.UserSetData(
                            room = room?.let { Room(it.name) },
                            file = file,
                            event = event
                        )
                    )
                )
            )
        )
    }

    fun sendSetReady(
        username: String,
        isReady: Boolean?,
        manuallyInitiated: Boolean,
        setByUsername: String? = null
    ) {
        sendTyped(
            Set(
                Set.SetData(
                    ready = Set.ReadyData(
                        username = username,
                        isReady = isReady,
                        manuallyInitiated = manuallyInitiated,
                        setBy = setByUsername
                    )
                )
            )
        )
    }

    fun sendPlaylist(username: String, files: List<String>) {
        sendTyped(
            Set(
                Set.SetData(
                    playlistChange = Set.PlaylistChangeData(user = username, files = files)
                )
            )
        )
    }

    fun sendPlaylistIndex(username: String, index: Int) {
        sendTyped(
            Set(
                Set.SetData(
                    playlistIndex = Set.PlaylistIndexData(user = username, index = index)
                )
            )
        )
    }

    fun sendNewControlledRoom(roomName: String, password: String) {
        sendTyped(
            Set(
                Set.SetData(
                    newControlledRoom = Set.NewControlledRoom(password = password, roomName = roomName)
                )
            )
        )
    }

    fun sendControlledRoomAuthStatus(success: Boolean, username: String, roomName: String) {
        sendTyped(
            Set(
                Set.SetData(
                    controllerAuth = Set.ControllerAuthResponse(
                        user = username,
                        room = roomName,
                        success = success
                    )
                )
            )
        )
    }

    /** Builds the full per-room user listing in response to a `List` request. */
    fun sendList() {
        val w = watcher ?: return
        val watchers = server.getAllWatchersForUser(w)
        val grouped = watchers.filter { it.room != null }.groupBy { it.room!!.name }

        val byRoom = grouped.mapValues { (_, roomWatchers) ->
            roomWatchers.associate { wt ->
                wt.name to ListResponse.UserData(
                    position = wt.getPosition() ?: 0.0,
                    isReady = wt.isReady(),
                    file = wt.file,
                    controller = wt.isController(),
                    features = wt.features
                )
            }
        }
        sendTyped(ListResponse(list = byRoom))
    }

    fun sendChatMessage(senderName: String, message: String) {
        sendTyped(Chat(Chat.ChatData(username = senderName, message = message)))
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
