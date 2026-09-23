package app.server

import app.protocol.wire.UserEvent
import app.server.model.ControlledServerRoom
import app.server.model.RoomPasswordProvider
import app.server.model.ServerConfig.Companion.MAX_ROOM_NAME_LENGTH
import app.server.model.ServerRoom
import app.server.model.ServerWatcher
import kotlinx.serialization.json.JsonPrimitive

/**
 * Manages the rooms of the built-in server: their lifecycle, watcher moves between rooms, and
 * broadcasts. A room is the group of people watching together, and a watcher is one user in it.
 */
open class ServerRoomManager {

    protected val _rooms = mutableMapOf<String, ServerRoom>()

    /**
     * Runs [action] against every watcher in every room. [PublicServerRoomManager] overrides it
     * to reach only the sender's room.
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

    /** Moves a watcher out of its old room and into [roomName], which is created when needed. */
    open fun moveWatcher(watcher: ServerWatcher, roomName: String) {
        val truncated = roomName.take(MAX_ROOM_NAME_LENGTH)
        /* A move to the watcher's current room is not a move. Done literally, the last watcher
         * in a room would leave it, the empty room would be deleted, and a fresh one would take
         * its place. The playlist, the selected index, the position and every registered
         * controller would be lost, only because a client sent its current room again. */
        if (watcher.room?.name == truncated) return
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

    /** Returns the room or creates it. A controlled room name gets a [ControlledServerRoom]. */
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
        // A set, with each name lowercased once. The membership test below runs in a loop, and a
        // list would cost a linear scan and a new lowercasing for every underscore.
        val allNames = _rooms.values
            .flatMapTo(mutableSetOf()) { room -> room.getWatchers().map { it.name.lowercase() } }

        var lowered = name.lowercase()
        if (lowered in allNames && name.endsWith("_")) {
            name = name.trimEnd('_').ifEmpty { "_" }
            lowered = name.lowercase()
        }
        while (lowered in allNames) {
            name += "_"
            lowered += "_"
        }
        return name
    }

    /** Returns the watchers that [forUser] can see. Here that is every watcher in every room. */
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
        // Same guard as the base, and it must be here too: the "left" broadcast below happens
        // before super runs. Without it, a self-move would tell the room that the watcher left,
        // and never announce them back.
        if (watcher.room?.name == roomName.take(MAX_ROOM_NAME_LENGTH)) return
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
        // The new room has not seen this watcher's file. The PC server sends it again on every
        // switch between isolated rooms.
        watcher.setFile(watcher.file)
    }
}
