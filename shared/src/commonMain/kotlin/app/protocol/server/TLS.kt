package app.protocol.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TLS negotiation message. When received, triggers [PacketHandler.callback]
 * to upgrade the connection from plain TCP to TLS.
 */
@Serializable
data class TLS(
    @SerialName("TLS")
    val tls: TLSData
) : ServerMessage {

    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        tls.startTLS?.let { startTLS ->
            packetHandler.callback.onReceivedTLS(startTLS)
        }
    }

    /** @property startTLS True if the server requests a TLS upgrade. */
    @Serializable
    data class TLSData(
        val startTLS: Boolean? = null
    )
}