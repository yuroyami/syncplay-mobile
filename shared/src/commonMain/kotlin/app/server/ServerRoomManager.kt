package app.server

import app.protocol.wire.UserEvent
import app.server.model.ControlledServerRoom
import app.server.model.RoomPasswordProvider
import app.server.model.ServerConfig.Companion.MAX_ROOM_NAME_LENGTH
import app.server.model.ServerRoom
import app.server.model.ServerWatcher
import kotlinx.serialization.json.JsonPrimitive

/** Manages room lifecycle, watcher movement between rooms, and broadcasting. */
open class ServerRoomManager {

    protected val _rooms = mutableMapOf<String, ServerRoom>()

    /**
     * Runs [action] against every watcher in every room.
     * Overridden in [PublicServerRoomManager] to confine the reach to the sender's room.
     */
    open fun broadcast(sender: ServerWatcher, action: (ServerWatcher) -> Unit) {
        for (room in _rooms.values) {
            for (watcher in room.getWatchers()) {
                action(watcher)
            }
        }
    }

    /** Runs [action] against every watcher in the sender's room. */
    fun broadcastRoom(sender: ServerWatcher, action: (ServerWatcher) -> Unit) {
        val room = sender.room ?: return
        if (room.name !in _rooms) return
        for (watcher in room.getWatchers()) {
            action(watcher)
        }
    }

    /** Moves a watcher to a new room, removing it from the old one and creating the target if needed. */
    open fun moveWatcher(watcher: ServerWatcher, roomName: String) {
        val truncated = roomName.take(MAX_ROOM_NAME_LENGTH)
        removeWatcher(watcher)
        val room = getOrCreateRoom(truncated)
        room.addWatcher(watcher)
    }

    /** Removes a watcher from its current room, deleting the room if it becomes empty. */
    fun removeWatcher(watcher: ServerWatcher) {
        val oldRoom = watcher.room ?: return
        oldRoom.removeWatcher(watcher)
        deleteRoomIfEmpty(oldRoom)
    }

    /** Returns the existing room or creates one; controlled room names yield a [ControlledServerRoom]. */
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

    private fun deleteRoomIfEmpty(room: ServerRoom) {
        if (room.isEmpty() && room.name.isNotEmpty()) {
            _rooms.remove(room.name)
        }
    }

    /** Returns a unique username, appending underscores when the requested name is already taken. */
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

    /** Returns all watchers visible to [forUser] (every room here, sender's room only when isolated). */
    open fun getAllWatchersForUser(forUser: ServerWatcher): List<ServerWatcher> {
        return _rooms.values.flatMap { it.getWatchers() }
    }

    fun getRoomCount(): Int = _rooms.size

    fun getTotalWatcherCount(): Int = _rooms.values.sumOf { it.getWatchers().size }
}

/**
 * Room manager for isolated-rooms mode: broadcasts reach only the sender's room, and a watcher
 * sees only its own room's members.
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
            val leftEvent = UserEvent(left = JsonPrimitive(true))
            broadcast(watcher) { w ->
                w.server.getClientConnection(w)?.sendUserSetting(
                    watcher.name, oldRoom, null, leftEvent
                )
            }
        }
        super.moveWatcher(watcher, roomName)
    }
}
