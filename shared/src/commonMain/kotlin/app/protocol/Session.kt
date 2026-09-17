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
    var serverHost: String = OFFICIAL_SERVER_ADDRESS
    var serverPort: Int = 8997

    /**
     * The host name the user typed, kept apart from [serverHost] because the official server's
     * name is collapsed to an IP before connecting. TLS checks the certificate against this and
     * sends it as SNI; the socket still dials [serverHost].
     */
    var tlsPeerHost: String = OFFICIAL_SERVER_NAME

    /**
     * An address to dial when [serverHost] cannot be reached, or null when there is nothing else
     * to try. Only the official server has one: it is dialled by name, and this is the address it
     * answered on when the app was built, for a network whose DNS is the thing that is broken.
     */
    var fallbackHost: String? = null
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
            protocol.supportsSharedPlaylists.value = value.supportsSharedPlaylists
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
        outboundQueueLock.withLock {
            outboundQueue.add(json)
            // The one collection here that used to have no ceiling, and it grows fastest exactly
            // when the network is worst: every failed write appends. A long outage with an active
            // chat or playlist used to build a backlog that was then fired at the server in one
            // burst on reconnect, which is a good way to be dropped again. The oldest entries go
            // first; a chat line from ten minutes ago is not worth the reconnection.
            while (outboundQueue.size > MAX_QUEUED_OUTBOUND) outboundQueue.removeAt(0)
        }
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

        /** Packets held for replay while disconnected. Older ones fall off the front. */
        const val MAX_QUEUED_OUTBOUND = 200

        /**
         * PC's MAX_ROOM_NAME_LENGTH, and where the server cuts. A managed name is the base plus
         * a 14-character hash, so anything that caps lower than this silently breaks one.
         */
        const val MAX_ROOM_NAME_CHARS = 35

        /**
         * An absolute ceiling on a peer-supplied name, well above any honest server's limit.
         *
         * Deliberately not the protocol's 16: a server that already has an "alice" hands the
         * next one "alice_", and it keeps appending past 16 for duplicates. Cutting at 16 makes
         * a viewer stop recognising their own name in the room's own messages.
         */
        const val MAX_USERNAME_CHARS = 64
    }
}