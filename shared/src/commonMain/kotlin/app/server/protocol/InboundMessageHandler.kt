package app.server.protocol

import app.server.ClientConnection
import app.utils.loggy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Routes incoming client JSON messages to the appropriate [ClientConnection] handler.
 *
 * This handles the quirk that Chat messages from the client come as `{"Chat": "message"}`
 * (a string value, not a JSON object), unlike all other message types.
 */
object InboundMessageHandler {

    /**
     * Parses a raw JSON line from a client and dispatches to the connection's handlers.
     */
    fun handle(jsonString: String, connection: ClientConnection) {
        try {
            val json = Json.parseToJsonElement(jsonString).jsonObject
            for ((key, value) in json) {
                when (key) {
                    "Chat" -> {
                        // Chat is special: value is a plain string, not a JsonObject
                        val message = value.jsonPrimitive.content
                        connection.handleChatString(message)
                    }
                    "Hello" -> connection.onHelloReceived(value.jsonObject)
                    "State" -> connection.onStateReceived(value.jsonObject)
                    "Set" -> connection.onSetReceived(value.jsonObject)
                    "List" -> connection.onListReceived(value.jsonObject)
                    "TLS" -> connection.onTLSReceived(value.jsonObject)
                    "Error" -> connection.onErrorReceived(value.jsonObject)
                    else -> {
                        loggy("Server: Unknown command '$key'")
                        connection.dropWithError("Unknown command: $key")
                    }
                }
            }
        } catch (e: Exception) {
            loggy("Server: Error handling message: ${e.message}")
            connection.dropWithError("Failed to parse message")
        }
    }
}
