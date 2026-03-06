package app.protocol.server

import app.protocol.ProtocolManager
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.room.event.RoomEventHandler
import app.utils.loggy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Incoming server "Error" packet. Logs the error message if present. */
@Serializable
data class Error(
    @SerialName("Error") val error: ErrorData
) : ServerMessage {

    override suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomEventHandler
    ) {
        error.message?.let { loggy("Server error: $it") }
    }

    @Serializable
    data class ErrorData(
        val message: String? = null
    )
}