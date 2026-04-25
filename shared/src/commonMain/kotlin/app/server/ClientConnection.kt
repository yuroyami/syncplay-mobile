package app.server

import SyncplayMobile.shared.BuildConfig
import app.protocol.ClientMessage
import app.protocol.ClientMessageDeserializer
import app.protocol.ClientMessageHandler
import app.protocol.ServerMessage
import app.protocol.models.PingService
import app.protocol.models.RoomFeatures
import app.protocol.syncplayJson
import app.protocol.wire.ChatData
import app.protocol.wire.ControllerAuthData
import app.protocol.wire.ErrorData
import app.protocol.wire.FileData
import app.protocol.wire.HelloData
import app.protocol.wire.IgnoringOnTheFlyData
import app.protocol.wire.ListUserData
import app.protocol.wire.NewControlledRoom
import app.protocol.wire.PingData
import app.protocol.wire.PlaylistChangeData
import app.protocol.wire.PlaylistIndexData
import app.protocol.wire.PlaystateData
import app.protocol.wire.ReadyData
import app.protocol.wire.Room
import app.protocol.wire.SetData
import app.protocol.wire.StateData
import app.protocol.wire.TLSData
import app.protocol.wire.UserEvent
import app.protocol.wire.UserSetData
import app.server.model.ServerConfig.Companion.MAX_FILENAME_LENGTH
import app.server.model.ServerRoom
import app.server.model.ServerWatcher
import app.utils.generateTimestampMillis
import app.utils.loggy
import kotlinx.serialization.SerializationException

/**
 * Server-side per-client protocol handler. Port of Python's `SyncServerProtocol`
 * (`syncplay-pc-src-master/syncplay/protocols.py`).
 *
 * Implements [ClientMessageHandler] for incoming wire messages — typed payloads from the
 * shared `app.protocol.*` hierarchy, decoded by [ClientMessageDeserializer]. Outbound
 * traffic constructs instances of [ServerMessage] (the same wire models the client
 * decodes) and serializes via [syncplayJson].
 *
 * Both directions therefore share the same Kotlinx Serialization pipeline; only the
 * end-tunnel handling differs.
 */
class ClientConnection(
    val server: SyncplayServer,
    private val sendFn: (String) -> Unit,
    private val dropFn: () -> Unit
) : ClientMessageHandler {

    var watcher: ServerWatcher? = null
    private var version: String? = null
    private var features: RoomFeatures? = null
    private var logged: Boolean = false
    var clientIgnoringOnTheFly: Int = 0
    var serverIgnoringOnTheFly: Int = 0

    private val pingService = PingService()
    private var clientLatencyCalculation: Double = 0.0
    private var clientLatencyCalculationArrivalTime: Double = 0.0

    val isLogged: Boolean get() = logged

    fun getFeatures(): RoomFeatures? = features
    fun getVersion(): String? = version

    /** Encodes a typed [ServerMessage] and writes it to the wire. */
    private inline fun <reified T : ServerMessage> sendTyped(message: T) {
        sendFn(syncplayJson.encodeToString(message))
    }

    fun dropWithError(error: String) {
        loggy("Server: Dropping client - $error")
        sendTyped(ServerMessage.Error(ErrorData(message = error)))
        dropFn()
    }

    fun onConnectionLost() {
        server.removeWatcher(watcher)
    }

    /**
     * Decodes a raw wire-JSON line via [ClientMessageDeserializer] and dispatches to the
     * matching `on…` method through [ClientMessage.dispatch]. Mirrors the client-side
     * `NetworkManager.handlePacket` pipeline.
     */
    suspend fun handlePacket(jsonString: String) {
        if (BuildConfig.DEBUG_SYNCPLAY_PROTOCOL) loggy("**CLIENT** $jsonString")
        try {
            val message = syncplayJson.decodeFromString(ClientMessageDeserializer, jsonString)
            message.dispatch(this)
        } catch (e: SerializationException) {
            loggy("Server: failed to decode line '$jsonString' — ${e.message}")
            dropWithError("Failed to parse message")
        }
    }

    // -----------------------------------------------------------
    // ClientMessageHandler — inbound dispatch
    // -----------------------------------------------------------

    override suspend fun onHello(message: ClientMessage.Hello) {
        val data = message.data
        val username = data.username?.trim()
        val roomName = data.room?.name?.trim()
        val clientVersion = data.realversion ?: data.version

        if (username.isNullOrEmpty() || roomName.isNullOrEmpty() || clientVersion == null) {
            dropWithError("Hello command does not have enough parameters")
            return
        }

        if (!checkPassword(data.password)) return

        version = clientVersion
        features = data.features

        server.addWatcher(this, username, roomName)
        logged = true
        sendHello(clientVersion)
    }

    override suspend fun onState(message: ClientMessage.State) {
        if (!requireLogged()) return
        val state = message.data

        state.ignoringOnTheFly?.let { ignore ->
            ignore.server?.let { srv ->
                if (serverIgnoringOnTheFly == srv) serverIgnoringOnTheFly = 0
            }
            ignore.client?.let { cl -> clientIgnoringOnTheFly = cl }
        }

        val playstate = state.playstate
        val position = playstate?.position ?: 0.0
        val paused = playstate?.paused
        val doSeek = playstate?.doSeek

        state.ping?.let { ping ->
            val latencyCalc = ping.latencyCalculation ?: 0.0
            val clientRtt = ping.clientRtt ?: 0.0
            clientLatencyCalculation = ping.clientLatencyCalculation ?: 0.0
            clientLatencyCalculationArrivalTime = currentTimeSeconds()
            pingService.receiveMessage(latencyCalc.toLong(), clientRtt)
        }

        if (serverIgnoringOnTheFly == 0) {
            watcher?.updateState(position, paused, doSeek, pingService.forwardDelay)
        }
    }

    override suspend fun onSet(message: ClientMessage.Set) {
        if (!requireLogged()) return
        val w = watcher ?: return
        val set = message.data

        set.room?.let { server.setWatcherRoom(w, it.name) }
        set.file?.let { w.setFile(truncateFileName(it)) }
        set.ready?.let { handleReady(w, it) }
        set.controllerAuth?.let { handleControllerAuth(w, it) }
        set.playlistChange?.let { server.setPlaylist(w, it.files ?: emptyList()) }
        set.playlistIndex?.let { server.setPlaylistIndex(w, it.index ?: 0) }
        set.features?.let { features = it }
    }

    override suspend fun onList(message: ClientMessage.List) {
        if (!requireLogged()) return
        sendList()
    }

    /** Plain-string Chat from the client (asymmetric: `{"Chat": "message"}`). */
    override suspend fun onChat(message: ClientMessage.Chat) {
        if (!requireLogged()) return
        if (!server.config.disableChat) {
            server.sendChat(watcher ?: return, message.message)
        }
    }

    override suspend fun onTLS(message: ClientMessage.TLS) {
        // Mobile server has no TLS-cert support — always answers "false".
        sendTyped(ServerMessage.TLS(TLSData(startTLS = "false")))
    }

    override suspend fun onError(message: ClientMessage.Error) {
        dropWithError(message.data.message ?: "Unknown error")
    }

    private fun handleReady(w: ServerWatcher, ready: ReadyData) {
        server.setReady(
            watcher = w,
            isReady = ready.isReady ?: false,
            manuallyInitiated = ready.manuallyInitiated ?: false,
            username = ready.username
        )
    }

    private fun handleControllerAuth(w: ServerWatcher, auth: ControllerAuthData) {
        val password = auth.password?.takeIf { it.isNotEmpty() } ?: return
        server.authRoomController(w, password, auth.room)
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
    // Outbound — typed `ServerMessage`s encoded via `syncplayJson`
    // -----------------------------------------------------------

    fun sendHello(clientVersion: String) {
        val w = watcher ?: return
        val room = w.room ?: return

        sendTyped(
            ServerMessage.Hello(
                HelloData(
                    username = w.name,
                    room = Room(name = room.name),
                    version = clientVersion,
                    realversion = "1.7.3",
                    features = server.buildServerFeatures(),
                    motd = server.config.motd
                )
            )
        )
    }

    /**
     * Writes a `State` packet to the wire, applying the same `ignoringOnTheFly` /
     * forced-update bookkeeping as the python reference server.
     */
    fun sendState(
        position: Double,
        paused: Boolean,
        doSeek: Boolean,
        setBy: ServerWatcher?,
        forced: Boolean
    ) {
        val processingTime = if (clientLatencyCalculationArrivalTime > 0) {
            currentTimeSeconds() - clientLatencyCalculationArrivalTime
        } else 0.0

        if (forced) {
            serverIgnoringOnTheFly += 1
        }

        val clientLatCalc = if (clientLatencyCalculation > 0) clientLatencyCalculation else null
        if (clientLatencyCalculation > 0) clientLatencyCalculation = 0.0

        val shouldSend = serverIgnoringOnTheFly == 0 || forced

        if (shouldSend) {
            val ignoring = if (serverIgnoringOnTheFly != 0 || clientIgnoringOnTheFly != 0) {
                IgnoringOnTheFlyData(
                    server = serverIgnoringOnTheFly.takeIf { it != 0 },
                    client = clientIgnoringOnTheFly.takeIf { it != 0 }
                )
            } else null

            sendTyped(
                ServerMessage.State(
                    StateData(
                        playstate = PlaystateData(
                            position = position,
                            paused = paused,
                            doSeek = doSeek,
                            setBy = setBy?.name
                        ),
                        ping = PingData(
                            latencyCalculation = currentTimeSeconds(),
                            serverRtt = pingService.rtt,
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

    /** Broadcasts a user state change (join/leave/file/room) in a `Set.user` envelope. */
    fun sendUserSetting(
        username: String,
        room: ServerRoom?,
        file: FileData?,
        event: UserEvent?
    ) {
        sendTyped(
            ServerMessage.Set(
                SetData(
                    user = mapOf(
                        username to UserSetData(
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
            ServerMessage.Set(
                SetData(
                    ready = ReadyData(
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
        sendTyped(ServerMessage.Set(SetData(playlistChange = PlaylistChangeData(user = username, files = files))))
    }

    fun sendPlaylistIndex(username: String, index: Int) {
        sendTyped(ServerMessage.Set(SetData(playlistIndex = PlaylistIndexData(user = username, index = index))))
    }

    fun sendNewControlledRoom(roomName: String, password: String) {
        sendTyped(ServerMessage.Set(SetData(newControlledRoom = NewControlledRoom(password = password, roomName = roomName))))
    }

    fun sendControlledRoomAuthStatus(success: Boolean, username: String, roomName: String) {
        sendTyped(
            ServerMessage.Set(
                SetData(
                    controllerAuth = ControllerAuthData(
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
                wt.name to ListUserData(
                    position = wt.getPosition() ?: 0.0,
                    isReady = wt.isReady(),
                    file = wt.file,
                    controller = wt.isController(),
                    features = wt.features
                )
            }
        }
        sendTyped(ServerMessage.List(rooms = byRoom))
    }

    fun sendChatMessage(senderName: String, message: String) {
        sendTyped(ServerMessage.Chat(ChatData(username = senderName, message = message)))
    }

    private fun requireLogged(): Boolean {
        if (!logged) {
            dropWithError("Not authenticated")
            return false
        }
        return true
    }

    private fun currentTimeSeconds(): Double = generateTimestampMillis() / 1000.0
}
