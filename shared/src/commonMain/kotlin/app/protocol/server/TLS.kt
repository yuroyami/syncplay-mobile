package app.protocol.server

import app.protocol.ProtocolManager
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import app.protocol.event.RoomCallback
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `TLS` packet — STARTTLS-style negotiation. The wire payload's [TLSData.startTLS] is
 * always a string per the original protocol:
 * - Client→server: `"send"` (request to start TLS).
 * - Server→client: `"true"` or `"false"` (whether the server accepts).
 */
@Serializable
data class TLS(
    @SerialName("TLS")
    val tls: TLSData
) : ServerMessage {

    override suspend fun handle(
        protocol: ProtocolManager,
        viewmodel: RoomViewmodel,
        dispatcher: NetworkManager,
        callback: RoomCallback
    ) {
        tls.startTLS?.let { startTLS ->
            // Match the python client semantics: `"true" in answer` / `"false" in answer`.
            val supported = startTLS.contains("true", ignoreCase = true)
            callback.onReceivedTLS(supported)
        }
    }

    @Serializable
    data class TLSData(
        val startTLS: String? = null
    )
}
