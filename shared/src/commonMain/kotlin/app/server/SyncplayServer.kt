package app.server

import app.protocol.models.RoomFeatures
import app.protocol.server.Set
import app.server.model.ControlledServerRoom
import app.server.model.NotControlledRoomException
import app.server.model.RoomPasswordProvider
import app.server.model.ServerConfig
import app.server.model.ServerConfig.Companion.MAX_FILENAME_LENGTH
import app.server.model.ServerConfig.Companion.MAX_ROOM_NAME_LENGTH
import app.server.model.ServerConfig.Companion.SERVER_STATE_INTERVAL_MS
import app.server.model.ServerWatcher
import app.utils.generateTimestampMillis
import app.utils.loggy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * Central Syncplay server, managing rooms, watchers, and broadcasting.
 * Port of Python's SyncFactory (syncplay-pc-src-master/syncplay/server.py).
 *
 * Completely independent of client-side code (RoomViewmodel, RoomCallback, etc.).
 */
class SyncplayServer(
    val config: ServerConfig,
    private val scope: CoroutineScope
) {
    private val roomManager: ServerRoomManager =
        if (config.isolateRooms) PublicServerRoomManager() else ServerRoomManager()

    /** Maps each watcher to its ClientConnection for outbound messaging. */
    private val _connections = mutableMapOf<ServerWatcher, ClientConnection>()

    /** Observable count of connected clients for UI. */
    val connectedClients = MutableStateFlow(0)

    /** Server event log for UI display. */
    val serverLog = MutableStateFlow<List<ServerLogEntry>>(emptyList())

    private var stateTimerJobs = mutableMapOf<ServerWatcher, Job>()

    fun getClientConnection(watcher: ServerWatcher): ClientConnection? = _connections[watcher]

    // --- Watcher lifecycle ---

    fun addWatcher(connection: ClientConnection, username: String, roomName: String) {
        val truncatedRoom = roomName.take(MAX_ROOM_NAME_LENGTH)
        val uniqueName = roomManager.findFreeUsername(username, config.maxUsernameLength)
        val watcher = ServerWatcher(this, uniqueName)
        watcher.version = connection.getVersion()
        watcher.features = connection.getFeatures()

        connection.watcher = watcher
        _connections[watcher] = connection

        setWatcherRoom(watcher, truncatedRoom, asJoin = true)
        connectedClients.value = _connections.size
        log("$uniqueName joined room '$truncatedRoom'")
    }

    fun setWatcherRoom(watcher: ServerWatcher, roomName: String, asJoin: Boolean = false) {
        val truncated = roomName.take(MAX_ROOM_NAME_LENGTH)
        roomManager.moveWatcher(watcher, truncated)

        if (asJoin) {
            sendJoinMessage(watcher)
        } else {
            sendRoomSwitchMessage(watcher)
        }

        val room = watcher.room ?: return
        val setByName = room.getSetBy()?.name

        // Send current playlist state to the new watcher
        val conn = _connections[watcher] ?: return
        conn.sendPlaylist(setByName ?: "", room.getPlaylist())
        room.getPlaylistIndex()?.let { conn.sendPlaylistIndex(setByName ?: "", it) }

        // If controlled room, notify about existing controllers
        if (RoomPasswordProvider.isControlledRoom(truncated)) {
            for (controller in room.getControllers()) {
                conn.sendControlledRoomAuthStatus(true, controller.name, truncated)
            }
        }

        // Start periodic state timer for this watcher
        startStateTimer(watcher)
    }

    fun removeWatcher(watcher: ServerWatcher?) {
        if (watcher == null) return
        val room = watcher.room ?: return

        stopStateTimer(watcher)
        sendLeftMessage(watcher)
        roomManager.removeWatcher(watcher)
        _connections.remove(watcher)
        connectedClients.value = _connections.size

        log("${watcher.name} disconnected")
    }

    // --- State broadcasting ---

    private fun startStateTimer(watcher: ServerWatcher) {
        stopStateTimer(watcher)
        stateTimerJobs[watcher] = scope.launch(Dispatchers.IO) {
            // Initial forced state update
            delay(100)
            sendState(watcher, doSeek = true, forcedUpdate = true)

            // Periodic state updates every SERVER_STATE_INTERVAL_MS
            while (isActive) {
                delay(SERVER_STATE_INTERVAL_MS)
                sendState(watcher, doSeek = false, forcedUpdate = false)
            }
        }
    }

    private fun stopStateTimer(watcher: ServerWatcher) {
        stateTimerJobs.remove(watcher)?.cancel()
    }

    /**
     * Sends a state update to a specific watcher.
     */
    fun sendState(watcher: ServerWatcher, doSeek: Boolean = false, forcedUpdate: Boolean = false) {
        val room = watcher.room ?: return
        val conn = _connections[watcher] ?: return

        if (!conn.isLogged) return

        val paused = room.isPaused()
        val position = room.getPosition()
        val setBy = room.getSetBy()

        conn.sendState(position, paused, doSeek, setBy, forcedUpdate)
    }

    /**
     * Forces a position update to all watchers in a room after a state change.
     * Port of Python's SyncFactory.forcePositionUpdate().
     */
    fun forcePositionUpdate(watcher: ServerWatcher, doSeek: Boolean, watcherPauseState: Boolean) {
        val room = watcher.room ?: return

        if (room.canControl(watcher)) {
            val paused = room.isPaused()
            val position = watcher.getPosition() ?: return
            room.setPosition(position, watcher)

            roomManager.broadcastRoom(watcher) { w ->
                _connections[w]?.sendState(position, paused, doSeek, watcher, true)
            }
        } else {
            val conn = _connections[watcher] ?: return
            // Send back the watcher's state first (BC compat), then the room's authoritative state
            conn.sendState(room.getPosition(), watcherPauseState, false, watcher, true)
            conn.sendState(room.getPosition(), room.isPaused(), true, room.getSetBy(), true)
        }
    }

    // --- Broadcasting messages ---

    private fun sendJoinMessage(watcher: ServerWatcher) {
        val event = Set.UserEvent(
            joined = JsonPrimitive(true),
            version = watcher.version
        )
        roomManager.broadcast(watcher) { w ->
            if (w != watcher) {
                _connections[w]?.sendUserSetting(watcher.name, watcher.room, null, event)
            }
        }
        roomManager.broadcastRoom(watcher) { w ->
            _connections[w]?.sendSetReady(watcher.name, watcher.isReady(), false)
        }
    }

    private fun sendRoomSwitchMessage(watcher: ServerWatcher) {
        roomManager.broadcast(watcher) { w ->
            _connections[w]?.sendUserSetting(watcher.name, watcher.room, null, null)
        }
        roomManager.broadcastRoom(watcher) { w ->
            _connections[w]?.sendSetReady(watcher.name, watcher.isReady(), false)
        }
    }

    private fun sendLeftMessage(watcher: ServerWatcher) {
        val event = Set.UserEvent(left = JsonPrimitive(true))
        roomManager.broadcast(watcher) { w ->
            _connections[w]?.sendUserSetting(watcher.name, watcher.room, null, event)
        }
    }

    fun sendFileUpdate(watcher: ServerWatcher) {
        val file = watcher.file ?: return
        roomManager.broadcast(watcher) { w ->
            _connections[w]?.sendUserSetting(watcher.name, watcher.room, file, null)
        }
    }

    // --- Chat ---

    fun sendChat(watcher: ServerWatcher, message: String) {
        val truncated = message.take(config.maxChatMessageLength)
        roomManager.broadcastRoom(watcher) { w ->
            _connections[w]?.sendChatMessage(watcher.name, truncated)
        }
    }

    // --- Readiness ---

    fun setReady(watcher: ServerWatcher, isReady: Boolean, manuallyInitiated: Boolean = true, username: String? = null) {
        if (username != null && username != watcher.name) {
            // Setting another user's readiness (controller feature)
            val room = watcher.room ?: return
            if (room.canControl(watcher)) {
                for (watcherToSet in room.getWatchers()) {
                    if (watcherToSet.name == username) {
                        watcherToSet.ready = isReady
                        roomManager.broadcastRoom(watcherToSet) { w ->
                            _connections[w]?.sendSetReady(watcherToSet.name, watcherToSet.isReady(), manuallyInitiated, watcher.name)
                        }
                    }
                }
            }
        } else {
            watcher.ready = isReady
            roomManager.broadcastRoom(watcher) { w ->
                _connections[w]?.sendSetReady(watcher.name, watcher.isReady(), manuallyInitiated)
            }
        }
    }

    // --- Playlist ---

    fun setPlaylist(watcher: ServerWatcher, files: List<String>) {
        val room = watcher.room ?: return
        if (room.canControl(watcher)) {
            room.setPlaylist(files, watcher)
            roomManager.broadcastRoom(watcher) { w ->
                _connections[w]?.sendPlaylist(watcher.name, files)
            }
        } else {
            // Non-controllers get the room's current playlist sent back
            _connections[watcher]?.sendPlaylist(room.name, room.getPlaylist())
            room.getPlaylistIndex()?.let {
                _connections[watcher]?.sendPlaylistIndex(room.name, it)
            }
        }
    }

    fun setPlaylistIndex(watcher: ServerWatcher, index: Int) {
        val room = watcher.room ?: return
        if (room.canControl(watcher)) {
            room.setPlaylistIndex(index, watcher)
            roomManager.broadcastRoom(watcher) { w ->
                _connections[w]?.sendPlaylistIndex(watcher.name, index)
            }
        } else {
            room.getPlaylistIndex()?.let {
                _connections[watcher]?.sendPlaylistIndex(room.name, it)
            }
        }
    }

    // --- Controlled rooms ---

    fun authRoomController(watcher: ServerWatcher, password: String, roomBaseName: String? = null) {
        val room = watcher.room ?: return
        val targetName = roomBaseName ?: room.name

        try {
            val success = RoomPasswordProvider.check(targetName, password, config.salt)
            if (success && room is ControlledServerRoom) {
                room.addController(watcher)
            }
            roomManager.broadcast(watcher) { w ->
                _connections[w]?.sendControlledRoomAuthStatus(success, watcher.name, room.name)
            }
        } catch (_: NotControlledRoomException) {
            // Not a controlled room — generate a new controlled room name
            val newName = RoomPasswordProvider.getControlledRoomName(targetName, password, config.salt)
            _connections[watcher]?.sendNewControlledRoom(newName, password)
        } catch (_: IllegalArgumentException) {
            // Invalid password format
            roomManager.broadcastRoom(watcher) { w ->
                _connections[w]?.sendControlledRoomAuthStatus(false, watcher.name, room.name)
            }
        }
    }

    // --- Features ---

    /**
     * Builds the [RoomFeatures] payload advertised to clients in our `Hello` response.
     * Mirrors python's `getFeatures()` server-side.
     */
    fun buildServerFeatures(): RoomFeatures = RoomFeatures(
        isolateRooms = config.isolateRooms,
        supportsReadiness = !config.disableReady,
        supportsManagedRooms = true,
        persistentRooms = false,
        supportsChat = !config.disableChat,
        maxChatMessageLength = config.maxChatMessageLength,
        maxUsernameLength = config.maxUsernameLength,
        maxRoomNameLength = MAX_ROOM_NAME_LENGTH,
        maxFilenameLength = MAX_FILENAME_LENGTH,
    )

    fun getAllWatchersForUser(watcher: ServerWatcher): List<ServerWatcher> {
        return roomManager.getAllWatchersForUser(watcher)
    }

    // --- Logging ---

    private fun log(message: String) {
        loggy("SyncplayServer: $message")
        val entry = ServerLogEntry(
            timestamp = generateTimestampMillis(),
            message = message
        )
        serverLog.value = serverLog.value + entry
    }

    /**
     * Shuts down the server, disconnecting all clients.
     */
    fun shutdown() {
        log("Server shutting down")
        for ((watcher, _) in _connections.toMap()) {
            stopStateTimer(watcher)
        }
        stateTimerJobs.clear()
        _connections.clear()
        connectedClients.value = 0
    }
}

data class ServerLogEntry(
    val timestamp: Long,
    val message: String
)
