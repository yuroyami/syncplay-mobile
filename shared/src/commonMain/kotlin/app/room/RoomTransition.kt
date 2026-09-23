package app.room

import app.protocol.Session
import app.protocol.WireMessage
import kotlinx.atomicfu.locks.synchronized

/**
 * Moves this client to the room [name]. A room is the group of people watching together. The
 * function trims and caps the name, tells the server, forgets the old roster (the list of users
 * in the room) and asks the server for the new one.
 *
 * Every step is needed. Without the roster reset and the List request, the users of the previous
 * room stay on screen until the next unrelated update.
 */
fun RoomViewmodel.switchRoom(name: String) {
    val target = name.trim().take(Session.MAX_ROOM_NAME_CHARS)
    if (target.isEmpty() || target == session.currentRoom) return

    synchronized(protocol.syncLock) {
        protocol.clearLocalStateIntents()
        session.currentRoom = target
        networkManager.sendAsync(WireMessage.roomChange(target))
        networkManager.sendAsync(WireMessage.listRequest())
    }
    // Until the List answer arrives, the local user is the only user known to be in the new room.
    session.userList.value = session.userList.value.filter { it.name == session.currentUsername }
    readiness.evaluate()
}
