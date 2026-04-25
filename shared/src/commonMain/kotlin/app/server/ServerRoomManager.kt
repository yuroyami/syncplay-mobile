package app.server

import app.protocol.server.Set
import app.server.model.ControlledServerRoom
import app.server.model.RoomPasswordProvider
import app.server.model.ServerConfig.Companion.MAX_ROOM_NAME_LENGTH
import app.server.model.ServerRoom
import app.server.model.ServerWatcher
import kotlinx.serialization.json.JsonPrimitive

/**
 * Manages room lifecycle, watcher movement between rooms, and broadcasting.
 * Port of Python's RoomManager class (syncplay-pc-src-master/syncplay/server.py).
 */
open class ServerRoomManager {

    protected val _rooms = mutableMapOf<String, ServerRoom>()

    /**
     * Broadcasts an action to all watchers across all rooms.
     * Override in [PublicServerRoomManager] for room-isolated behavior.
     */
    open fun broadcast(sender: ServerWatcher, action: (ServerWatcher) -> Unit) {
        for (room in _rooms.values) {
            for (watcher in room.getWatchers()) {
                action(watcher)
            }
        }
    }

    /**
     * Broadcasts an action to all watchers in the sender's room.
     */
    fun broadcastRoom(sender: ServerWatcher, action: (ServerWatcher) -> Unit) {
        val room = sender.room ?: return
        if (room.name !in _rooms) return
        for (watcher in room.getWatchers()) {
            action(watcher)
        }
    }

    /**
     * Moves a watcher to a new room, removing from the old one and creating if needed.
     */
    open fun moveWatcher(watcher: ServerWatcher, roomName: String) {
        val truncated = roomName.take(MAX_ROOM_NAME_LENGTH)
        removeWatcher(watcher)
        val room = getOrCreateRoom(truncated)
        room.addWatcher(watcher)
    }

    /**
     * Removes a watcher from their current room, deleting the room if empty.
     */
    fun removeWatcher(watcher: ServerWatcher) {
        val oldRoom = watcher.room ?: return
        oldRoom.removeWatcher(watcher)
        deleteRoomIfEmpty(oldRoom)
    }

    /**
     * Gets an existing room or creates a new one. Controlled room names get ControlledServerRoom.
     */
    private fun getOrCreateRoom(roomName: String): ServerRoom {
        _rooms[roomName]?.let { return it }

        val room = if (RoomPasswordProvider.isControlledRoom(roomName)) {
            ControlledServerRoom(roomName)
        } else {
            ServerRoom(roomName)
        }
        _rooms[roomName] = room
        return room
    }

    /**
     * Deletes a room if it has no watchers.
     */
    private fun deleteRoomIfEmpty(room: ServerRoom) {
        if (room.isEmpty() && room.name.isNotEmpty()) {
            _rooms.remove(room.name)
        }
    }

    /**
     * Finds a unique username by appending underscores if the name is already taken.
     * Port of Python's RoomManager.findFreeUsername().
     */
    fun findFreeUsername(username: String, maxLength: Int): String {
        var name = username.take(maxLength)
        val allNames = _rooms.values
            .flatMap { it.getWatchers() }
            .map { it.name.lowercase() }

        if (name.lowercase() in allNames && name.endsWith("_")) {
            name = name.trimEnd('_').ifEmpty { "_" }
        }
        while (name.lowercase() in allNames) {
            name += "_"
        }
        return name
    }

    /**
     * Returns all watchers accessible to a given user.
     */
    open fun getAllWatchersForUser(forUser: ServerWatcher): List<ServerWatcher> {
        return _rooms.values.flatMap { it.getWatchers() }
    }

    fun getRoomCount(): Int = _rooms.size

    fun getTotalWatcherCount(): Int = _rooms.values.sumOf { it.getWatchers().size }
}

/**
 * Room manager for isolated rooms mode.
 * Broadcast only reaches watchers in the sender's room.
 * Port of Python's PublicRoomManager.
 */
class PublicServerRoomManager : ServerRoomManager() {

    override fun broadcast(sender: ServerWatcher, action: (ServerWatcher) -> Unit) {
        broadcastRoom(sender, action)
    }

    override fun getAllWatchersForUser(forUser: ServerWatcher): List<ServerWatcher> {
        return forUser.room?.getWatchers() ?: emptyList()
    }

    override fun moveWatcher(watcher: ServerWatcher, roomName: String) {
        val oldRoom = watcher.room
        if (oldRoom != null) {
            val leftEvent = Set.UserEvent(left = JsonPrimitive(true))
            broadcast(watcher) { w ->
                w.server.getClientConnection(w)?.sendUserSetting(
                    watcher.name, oldRoom, null, leftEvent
                )
            }
        }
        super.moveWatcher(watcher, roomName)
    }
}
