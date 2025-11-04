package com.yuroyami.syncplay.managers.protocol.handler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TLS(
    @SerialName("TLS")
    val tls: TLSData
): SyncplayMessage {

    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        tls.startTLS?.let { startTLS ->
            packetHandler.callback.onReceivedTLS(startTLS)
        }
    }

    @Serializable
    data class TLSData(
        val startTLS: Boolean? = null
    )
}