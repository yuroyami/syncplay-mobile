package com.yuroyami.syncplay.managers

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.yuroyami.syncplay.AbstractManager
import com.yuroyami.syncplay.models.Message
import com.yuroyami.syncplay.models.RoomFeatures
import com.yuroyami.syncplay.models.User
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Manages the current Syncplay room session state.
 *
 * A session encapsulates all information about an active room connection, including:
 * - Server connection details (host, port, credentials)
 * - Room properties (name, features, operator status)
 * - User list and their states
 * - Message history
 * - Shared playlist
 * - Ready status and network latency
 *
 * The protocol always operates with exactly one session at a time. When changing rooms,
 * the session is replaced and the protocol relaunches with the new session.
 *
 * @property viewmodel The parent RoomViewModel that owns this manager
 */
class SessionManager(val viewmodel: RoomViewmodel) : AbstractManager(viewmodel) {

    /**
     * The current active session instance.
     * Contains all state for the connected room.
     */
    var session: Session = Session()

    /**
     * Whether the server supports chat functionality.
     * Derived from [Session.roomFeatures].
     */
    val supportsChat = MutableStateFlow(true)

    /**
     * Whether the server supports managed room features (room operators, permissions).
     * Derived from [Session.roomFeatures].
     */
    val supportsManagedRooms = MutableStateFlow(false)

    /**
     * Whether the current room is a managed room.
     * Managed rooms have designated operators who are the only ones who can operate the room sync.
     */
    val isManagedRoom = MutableStateFlow(false)

    /**
     * Resets the session by creating a new empty session instance.
     * Called when leaving a room or disconnecting.
     */
    override fun invalidate() {
        session = Session() //invalidates the session and replaces it with a new empty one
    }

    /**
     * Represents a single Syncplay room session with all its state.
     *
     * Contains connection details, user data, messages, playlist, and synchronization state.
     */
    inner class Session {
        /********** Connection Information **********/

        /** Server hostname or IP address. Defaults to official Syncplay server. */
        var serverHost: String = "151.80.32.178"

        /** Server port number. Default is 8997 (standard Syncplay port). */
        var serverPort: Int = 8997

        /** Current user's display name. Defaults to random anonymous username. */
        var currentUsername: String = "Anonymous${(1000..9999).random()}"

        /** Name of the room to join or currently in. */
        var currentRoom: String = "roomname"

        /** Password for joining the room, if required. */
        var currentPassword: String = ""

        /** Password for identifying as a room operator, if the room is managed. */
        var currentOperatorPassword: String = ""

        /**
         * Features supported by the current room/server.
         * Updates [supportsChat] and [supportsManagedRooms] when set.
         */
        var roomFeatures: RoomFeatures = RoomFeatures()
            set(value) {
                supportsChat.value = value.supportsChat
                supportsManagedRooms.value = value.supportsManagedRooms
                field = value
            }

        /********** Room State **********/

        /**
         * List of all users currently in the room.
         * Each user includes their name, ready state, and current media file.
         */
        val userList = MutableStateFlow(listOf<User>())

        /**
         * Complete history of chat messages sent and received in this session.
         * Includes system messages, user chat, and error notifications.
         */
        val messageSequence = mutableStateListOf<Message>()

        /**
         * Queue of outbound messages awaiting transmission.
         *
         * When connection is lost, outgoing protocol messages (JSON) are queued here
         * instead of being sent. Upon reconnection, the queue is processed and cleared.
         */
        val outboundQueue = mutableListOf<String>()

        /********** Shared Playlist **********/

        /**
         * The room's shared playlist containing media file paths or identifiers.
         * Empty if shared playlist is disabled or not in use.
         */
        val sharedPlaylist = mutableStateListOf<String>()

        /**
         * Index of the currently playing file in the shared playlist.
         * Value of -1 indicates no file is selected or playlist is not in use.
         */
        val spIndex = mutableIntStateOf(-1)

        /********** Synchronization State **********/

        /**
         * Whether the local user is marked as "ready" for playback synchronization.
         * Initialized based on user preference for automatic ready state.
         */
        val ready = mutableStateOf(viewmodel.setReadyDirectly)

        /**
         * Protocol-level ping latency with the server in milliseconds.
         * Null when not connected or ping unavailable.
         * Note: This is different from ICMP ping shown in the UI.
         */
        val protoPing = MutableStateFlow<Int?>(null)
    }
}