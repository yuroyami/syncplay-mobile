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

class Session(val protocol: ProtocolManager) {
    var serverHost: String = "151.80.32.178"
    var serverPort: Int = 8997
    var currentUsername: String = "Anonymous${(1000..9999).random()}"
    var currentRoom: String = "roomname"
    var currentPassword: String = ""
    var currentOperatorPassword: String = ""

    var roomFeatures: RoomFeatures = RoomFeatures()
        set(value) {
            protocol.supportsChat.value = value.supportsChat
            protocol.supportsManagedRooms.value = value.supportsManagedRooms
            field = value
        }

    val userList = MutableStateFlow(listOf<User>())
    val messageSequence = mutableStateListOf<Message>()

    /** Outgoing packets queued while disconnected, flushed on reconnection. */
    val outboundQueue = mutableListOf<String>()

    val sharedPlaylist = mutableStateListOf<String>()

    /** This is the shared playlist playback index
     *  -1 = no file selected. */
    val spIndex = mutableIntStateOf(-1)

    val ready = mutableStateOf(Preferences.READY_FIRST_HAND.value())
    val protoPing = MutableStateFlow<Int?>(null)
}