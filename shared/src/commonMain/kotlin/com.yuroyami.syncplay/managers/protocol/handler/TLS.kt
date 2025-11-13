package com.yuroyami.syncplay.managers.protocol.handler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a **TLS negotiation message** exchanged between the Syncplay client and server.
 *
 * This message type is used during the initial handshake phase to indicate that the server
 * is ready to upgrade the connection to a secure TLS channel.
 *
 * When received, this message triggers the `onReceivedTLS(startTLS)` callback within
 * [PacketHandler.callback], allowing the protocol manager to begin a secure TLS handshake.
 *
 * @property tls The [TLSData] object containing TLS negotiation details.
 */
@Serializable
data class TLS(
    @SerialName("TLS")
    val tls: TLSData
) : SyncplayMessage {

    /**
     * Handles the received TLS negotiation packet.
     *
     * If the `startTLS` flag is present and `true`, notifies the protocol layer
     * through [PacketHandler.Callback.onReceivedTLS] to begin upgrading the connection from plain TCP to TLS/SSL encrypted TCP.
     */
    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        tls.startTLS?.let { startTLS ->
            packetHandler.callback.onReceivedTLS(startTLS)
        }
    }

    /**
     * Contains data relevant to a TLS negotiation message.
     *
     * @property startTLS Indicates whether the client should initiate a TLS handshake.
     * A value of `true` means the server supports and requests TLS.
     */
    @Serializable
    data class TLSData(
        val startTLS: Boolean? = null
    )
}
