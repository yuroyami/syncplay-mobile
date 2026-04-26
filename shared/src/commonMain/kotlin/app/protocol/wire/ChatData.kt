package app.protocol.wire

import kotlinx.serialization.Serializable

/**
 * Server-only chat payload: `{"Chat": {"username": "...", "message": "..."}}`.
 * Client-originated chat is asymmetric — a bare string — and is modelled inline on
 * `WireMessage.ChatRequest`.
 */
@Serializable
data class ChatData(
    val username: String? = null,
    val message: String? = null
)
