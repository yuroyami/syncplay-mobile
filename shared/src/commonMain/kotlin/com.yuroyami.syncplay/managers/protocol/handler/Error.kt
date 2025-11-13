package com.yuroyami.syncplay.managers.protocol.handler

import com.yuroyami.syncplay.utils.loggy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Incoming server "Error" packet.
 *
 * Deserializes JSON of the form:
 * ```json
 * { "Error": { "message": "..." } }
 * ```
 *
 * When handled, logs the error message received from the server.
 */
@Serializable
data class Error(
    @SerialName("Error") val error: ErrorData
) : SyncplayMessage {

    /**
     * Process the error packet.
     *
     * If a message is present, logs it using [loggy].
     */
    context(packetHandler: PacketHandler)
    override suspend fun handle() {
        error.message?.let { message ->
            loggy("Server error: $message")
        }
    }

    /**
     * Error payload fields.
     *
     * @property message The error text sent by the server.
     */
    @Serializable
    data class ErrorData(
        val message: String? = null
    )
}
