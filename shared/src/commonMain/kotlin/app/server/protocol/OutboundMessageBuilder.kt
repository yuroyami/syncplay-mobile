package app.server.protocol

import app.server.model.ServerRoom
import app.server.model.ServerWatcher
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds server-to-client JSON messages that match the shapes the client's
 * ServerMessage data classes expect to deserialize.
 *
 * Each function corresponds to a protocol message type from the original
 * Syncplay server (syncplay-pc-src-master/syncplay/protocols.py SyncServerProtocol).
 */
object OutboundMessageBuilder {

    /**
     * Builds the Hello response sent after successful authentication.
     * Client deserializes this as `app.protocol.server.Hello`.
     */
    fun buildHelloResponse(
        username: String,
        roomName: String,
        clientVersion: String?,
        features: Map<String, JsonElement>,
        motd: String
    ): JsonObject = buildJsonObject {
        putJsonObject("Hello") {
            put("username", username)
            putJsonObject("room") {
                put("name", roomName)
            }
            clientVersion?.let { put("version", it) }
            put("realversion", "1.7.3")
            putJsonObject("features") {
                for ((k, v) in features) put(k, v)
            }
            put("motd", motd)
        }
    }

    /**
     * Builds a State update broadcast to clients.
     * Client deserializes this as `app.protocol.server.State`.
     */
    fun buildStateResponse(
        position: Double,
        paused: Boolean,
        doSeek: Boolean,
        setByName: String?,
        latencyCalculation: Double,
        serverRtt: Double,
        clientLatencyCalculation: Double?,
        processingTime: Double,
        serverIgnoringOnTheFly: Int,
        clientIgnoringOnTheFly: Int
    ): JsonObject = buildJsonObject {
        putJsonObject("State") {
            putJsonObject("playstate") {
                put("position", position)
                put("paused", paused)
                put("doSeek", doSeek)
                put("setBy", setByName)
            }
            putJsonObject("ping") {
                put("latencyCalculation", latencyCalculation)
                put("serverRtt", serverRtt)
                if (clientLatencyCalculation != null) {
                    put("clientLatencyCalculation", clientLatencyCalculation + processingTime)
                }
            }
            if (serverIgnoringOnTheFly != 0 || clientIgnoringOnTheFly != 0) {
                putJsonObject("ignoringOnTheFly") {
                    if (serverIgnoringOnTheFly != 0) {
                        put("server", serverIgnoringOnTheFly)
                    }
                    if (clientIgnoringOnTheFly != 0) {
                        put("client", clientIgnoringOnTheFly)
                    }
                }
            }
        }
    }

    /**
     * Builds a Set/user message for join, leave, file update, or room change events.
     * Client deserializes this as `app.protocol.server.Set`.
     */
    fun buildSetUser(
        username: String,
        room: ServerRoom?,
        file: JsonObject?,
        event: Map<String, Any>?
    ): JsonObject = buildJsonObject {
        putJsonObject("Set") {
            putJsonObject("user") {
                putJsonObject(username) {
                    if (room != null) {
                        putJsonObject("room") {
                            put("name", room.name)
                        }
                    }
                    if (file != null) {
                        put("file", file)
                    }
                    if (event != null) {
                        putJsonObject("event") {
                            for ((k, v) in event) {
                                when (v) {
                                    is Boolean -> put(k, v)
                                    is String -> put(k, v)
                                    is Number -> put(k, v.toDouble())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Builds a Set/ready message for readiness state changes.
     */
    fun buildSetReady(
        username: String,
        isReady: Boolean?,
        manuallyInitiated: Boolean,
        setByUsername: String? = null
    ): JsonObject = buildJsonObject {
        putJsonObject("Set") {
            putJsonObject("ready") {
                put("username", username)
                if (isReady != null) put("isReady", isReady)
                put("manuallyInitiated", manuallyInitiated)
                setByUsername?.let { put("setBy", it) }
            }
        }
    }

    /**
     * Builds a Set/playlistChange message.
     */
    fun buildPlaylistChange(user: String, files: List<String>): JsonObject = buildJsonObject {
        putJsonObject("Set") {
            putJsonObject("playlistChange") {
                put("user", user)
                putJsonArray("files") {
                    for (f in files) add(JsonPrimitive(f))
                }
            }
        }
    }

    /**
     * Builds a Set/playlistIndex message.
     */
    fun buildPlaylistIndex(user: String, index: Int): JsonObject = buildJsonObject {
        putJsonObject("Set") {
            putJsonObject("playlistIndex") {
                put("user", user)
                put("index", index)
            }
        }
    }

    /**
     * Builds a Set/newControlledRoom message.
     */
    fun buildNewControlledRoom(roomName: String, password: String): JsonObject = buildJsonObject {
        putJsonObject("Set") {
            putJsonObject("newControlledRoom") {
                put("password", password)
                put("roomName", roomName)
            }
        }
    }

    /**
     * Builds a Set/controllerAuth response message.
     */
    fun buildControllerAuth(success: Boolean, username: String, roomName: String): JsonObject = buildJsonObject {
        putJsonObject("Set") {
            putJsonObject("controllerAuth") {
                put("user", username)
                put("room", roomName)
                put("success", success)
            }
        }
    }

    /**
     * Builds a Set/features update message.
     */
    fun buildFeaturesUpdate(features: JsonObject): JsonObject = buildJsonObject {
        putJsonObject("Set") {
            put("features", features)
        }
    }

    /**
     * Builds a List response containing all rooms and their watchers.
     * Client deserializes this as `app.protocol.server.ListResponse`.
     */
    fun buildListResponse(watchers: List<ServerWatcher>): JsonObject {
        // Group watchers by room to avoid duplicate JSON keys
        val grouped = watchers.filter { it.room != null }.groupBy { it.room!!.name }

        return buildJsonObject {
            putJsonObject("List") {
                for ((roomName, roomWatchers) in grouped) {
                    putJsonObject(roomName) {
                        for (watcher in roomWatchers) {
                            putJsonObject(watcher.name) {
                                put("position", 0)
                                put("file", watcher.file ?: JsonObject(emptyMap()))
                                put("controller", watcher.isController())
                                put("isReady", watcher.isReady()?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("features", watcher.features ?: JsonObject(emptyMap()))
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Builds a Chat message broadcast.
     * Client deserializes this as `app.protocol.server.Chat`.
     */
    fun buildChatMessage(username: String, message: String): JsonObject = buildJsonObject {
        putJsonObject("Chat") {
            put("username", username)
            put("message", message)
        }
    }

    /**
     * Builds an Error message.
     * Client deserializes this as `app.protocol.server.Error`.
     */
    fun buildError(message: String): JsonObject = buildJsonObject {
        putJsonObject("Error") {
            put("message", message)
        }
    }

    /**
     * Builds a TLS response (mobile server always responds false — no TLS support).
     */
    fun buildTLSResponse(supported: Boolean = false): JsonObject = buildJsonObject {
        putJsonObject("TLS") {
            put("startTLS", if (supported) "true" else "false")
        }
    }
}
