package com.yuroyami.syncplay.managers.protocol.handler

import com.yuroyami.syncplay.utils.loggy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Error(
    @SerialName("Error") val error: ErrorData
): SyncplayMessage {

    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        error.message?.let { message ->
            loggy("Server error: $message")
        }
    }

    @Serializable
    data class ErrorData(
        val message: String? = null
    )

}