package app.room

import app.protocol.Session
import app.protocol.WireMessage
import kotlinx.atomicfu.locks.synchronized

/**
 * Moves this client to [name]: tells the server, forgets the old roster, asks for the new one.
 *
 * Changing rooms is four things, not one. Writing the new name over the old one and stopping
 * there left the previous room's people on screen until the next unrelated update, with no cap on
 * the name and nothing asking the server who is actually there.
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
    // Only we are known to be in the new room until the List answer says otherwise.
    session.userList.value = session.userList.value.filter { it.name == session.currentUsername }
    readiness.evaluate()
}
