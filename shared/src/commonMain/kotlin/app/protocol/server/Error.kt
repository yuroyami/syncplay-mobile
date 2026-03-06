package app.protocol.server

import app.utils.loggy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Incoming server "Error" packet. Logs the error message if present. */
@Serializable
data class Error(
    @SerialName("Error") val error: ErrorData
) : ServerMessage {

    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        error.message?.let { loggy("Server error: $it") }
    }

    @Serializable
    data class ErrorData(
        val message: String? = null
    )
}