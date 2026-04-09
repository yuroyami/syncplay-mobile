package app.protocol

import kotlinx.serialization.json.JsonObject

/**
 * Common protocol contract for the Syncplay JSON-over-TCP protocol.
 *
 * Both the client-side (receiving from server) and the server-side (receiving from client)
 * must handle the same 7 message types. This interface defines that contract, while each
 * side provides its own implementation with different domain logic.
 *
 * The JSON wire format is the shared protocol — both sides produce and consume identical
 * JSON shapes. Message routing is based on the top-level JSON key.
 */
interface SyncplayProtocolHandler {
    fun onHelloReceived(data: JsonObject)
    fun onStateReceived(data: JsonObject)
    fun onSetReceived(data: JsonObject)
    fun onListReceived(data: JsonObject)
    fun onChatReceived(data: JsonObject)
    fun onTLSReceived(data: JsonObject)
    fun onErrorReceived(data: JsonObject)

    /**
     * Routes a raw JSON string to the appropriate handler based on the top-level key.
     * Both client and server use the same routing logic.
     */
    fun routeMessage(json: JsonObject) {
        for (key in json.keys) {
            val value = json[key] as? JsonObject ?: continue
            when (key) {
                "Hello" -> onHelloReceived(value)
                "State" -> onStateReceived(value)
                "Set" -> onSetReceived(value)
                "List" -> onListReceived(value)
                "Chat" -> onChatReceived(value)
                "TLS" -> onTLSReceived(value)
                "Error" -> onErrorReceived(value)
            }
        }
    }
}
