package app.protocol

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import app.preferences.Preferences
import app.preferences.value
import app.protocol.models.RoomFeatures
import app.protocol.models.User
import app.room.models.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Session(val protocol: ProtocolManager) {
    var serverHost: String = "151.80.32.178"
    var serverPort: Int = 8997

    /**
     * The host name the user typed, kept apart from [serverHost] because the official server's
     * name is collapsed to an IP before connecting. TLS checks the certificate against this and
     * sends it as SNI; the socket still dials [serverHost].
     */
    var tlsPeerHost: String = "syncplay.pl"
    var currentUsername: String = "Anonymous${(1000..9999).random()}"
    var currentRoom: String = "roomname"
    var currentPassword: String = ""
    var currentOperatorPassword: String = ""

    /** The operator password last sent to identify with; stored as the real one once the server says yes. */
    var lastControlPasswordAttempt: String = ""

    var roomFeatures: RoomFeatures = RoomFeatures()
        set(value) {
            protocol.supportsChat.value = value.supportsChat
            protocol.supportsManagedRooms.value = value.supportsManagedRooms
            field = value
        }

    val userList = MutableStateFlow(listOf<User>())
    val messageSequence = MutableStateFlow<List<Message>>(emptyList())

    /**
     * Outgoing packets queued while disconnected, flushed on reconnection.
     * Guarded by [outboundQueueLock]: the failure path of `transmitPacket` appends from
     * arbitrary IO threads while `onConnected` drains, so the snapshot-then-clear in
     * [drainOutbound] must be atomic against concurrent appends.
     */
    private val outboundQueue = mutableListOf<String>()
    private val outboundQueueLock = Mutex()

    suspend fun queueOutbound(json: String) {
        outboundQueueLock.withLock { outboundQueue.add(json) }
    }

    /** Atomically snapshots and empties the queue. */
    suspend fun drainOutbound(): List<String> = outboundQueueLock.withLock {
        val snapshot = outboundQueue.toList()
        outboundQueue.clear()
        snapshot
    }

    val sharedPlaylist = mutableStateListOf<String>()

    /** This is the shared playlist playback index
     *  -1 = no file selected. */
    val spIndex = mutableIntStateOf(-1)

    val ready = mutableStateOf(Preferences.READY_FIRST_HAND.value())

    /** Whether all other users in the room are ready (ignores users with no file). */
    fun areAllOtherUsersReady(): Boolean {
        return userList.value
            .filter { it.name != currentUsername && it.file != null }
            .all { it.readiness }
    }

    /** Us plus every other user who is ready with a file, PC's `usersInRoomCount` to the letter. */
    fun usersInRoomCount(): Int {
        val othersReadyWithFile = userList.value.count { it.name != currentUsername && it.file != null && it.readiness }
        return 1 + othersReadyWithFile
    }

    /**
     * True when we ARE in a controlled (+) room but are NOT a controller, so we must follow the
     * controller's pace. Mirrors python's `!currentUser.canControl()`. In a normal room this is
     * false (everyone can control).
     */
    fun isInControlledRoomWithoutController(): Boolean {
        if (!roomFeatures.supportsManagedRooms) return false
        if (!currentRoom.startsWith("+")) return false
        return userList.value.firstOrNull { it.name == currentUsername }?.isController != true
    }

    /** True in a controlled room, whoever holds the password. */
    fun isControlledRoom(): Boolean = roomFeatures.supportsManagedRooms && currentRoom.startsWith("+")

    companion object {
        /** A hostile server streaming joins must not grow the roster without end. */
        const val MAX_USERS = 500

        /** The chat log keeps this many lines; older ones fall off the top. */
        const val MAX_MESSAGES = 1000
    }
}