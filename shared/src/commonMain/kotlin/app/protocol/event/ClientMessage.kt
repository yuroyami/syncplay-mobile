package app.protocol.event

import app.player.models.MediaFile
import app.player.models.MediaFile.Companion.hashed
import app.preferences.Preferences
import app.preferences.value
import app.protocol.ProtocolManager
import app.utils.ProtocolApi
import app.utils.generateTimestampMillis
import app.utils.md5
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Base class for outbound Syncplay protocol packets.
 *
 * Each subclass builds a JSON structure conforming to the Syncplay protocol.
 * Packets are constructed via a builder pattern and serialized with [build].
 *
 * Usage:
 * ```kotlin
 * networkManager.send<PacketOut.Chat> {
 *     message = "Hello, room!"
 * }
 * ```
 */
sealed class ClientMessage {
    abstract fun build(): JsonElement

    /** Initial handshake — identifies the client with username, room, password, and supported features. */
    @ProtocolApi
    class Hello : ClientMessage() {
        var username: String = ""
        var roomname: String = ""
        /** Will be MD5-hashed before sending. */
        var serverPassword: String = ""

        override fun build() = buildJsonObject {
            putJsonObject("Hello") {
                put("username", username)

                if (serverPassword.isNotEmpty()) {
                    put("password", md5(serverPassword).toHexString(HexFormat.Default))
                }

                put("room", buildJsonObject {
                    put("name", roomname)
                })

                put("version", "1.7.3")
                put("features", buildJsonObject {
                    put("sharedPlaylists", true)
                    put("chat", true)
                    put("featureList", true)
                    put("readiness", true)
                    put("managedRooms", true)
                })
            }
        }
    }

    /** Acknowledges room join after the server grants access. */
    @ProtocolApi
    class Joined : ClientMessage() {
        var roomname: String = ""

        override fun build() = buildJsonObject {
            putJsonObject("Set") {
                putJsonObject(roomname) {
                    putJsonObject("room") {
                        put("name", roomname)
                    }
                    putJsonObject("event") {
                        put("joined", true)
                    }
                }
            }
        }
    }

    /** Clears or acknowledges playlist state. */
    @ProtocolApi
    class EmptyList : ClientMessage() {
        override fun build() = buildJsonObject {
            putJsonArray("List") {}
        }
    }

    /** Declares whether this client is ready for synchronized playback. */
    @ProtocolApi
    class Readiness : ClientMessage() {
        var isReady: Boolean = false
        /** True if user-initiated, false if automatic. */
        var manuallyInitiated: Boolean = false

        override fun build() = buildJsonObject {
            putJsonObject("Set") {
                putJsonObject("ready") {
                    put("isReady", isReady)
                    put("manuallyInitiated", manuallyInitiated)
                }
            }
        }
    }

    /**
     * Declares the currently loaded media file to the room.
     * File name/size are sent, hashed, or omitted based on privacy preferences.
     */
    @ProtocolApi
    class File : ClientMessage() {
        var media: MediaFile? = null

        override fun build() = buildJsonObject {
            requireNotNull(media) { "Media file must be provided" }

            putJsonObject("Set") {
                putJsonObject("file") {
                    val nameBehavior = Preferences.HASH_FILENAME.value()
                    val sizeBehavior = Preferences.HASH_FILESIZE.value()

                    put("duration", media!!.fileDuration ?: -1)
                    put(
                        "name", when (nameBehavior) {
                            "1" -> media!!.fileName
                            "2" -> media!!.fileName.hashed().take(12)
                            else -> ""
                        }
                    )
                    put(
                        "size", when (sizeBehavior) {
                            "1" -> media!!.fileSize
                            "2" -> media!!.fileSize.hashed().take(12)
                            else -> ""
                        }
                    )
                }
            }
        }
    }

    /** Sends a chat message to all users in the room. */
    @ProtocolApi
    class Chat : ClientMessage() {
        var message: String = ""

        override fun build() = buildJsonObject {
            put("Chat", message)
        }
    }

    /**
     * Core synchronization packet — reports current playback position, pause state,
     * and ping timing to the server. Sent regularly to keep all clients in sync.
     */
    @ProtocolApi
    class State(private var p: ProtocolManager) : ClientMessage() {
        /** Server timestamp echoed from the last received State packet, for RTT calculation. */
        var serverTime: Double? = null
        val clientTime: Double
            get() = generateTimestampMillis() / 1000.0
        var doSeek: Boolean? = null
        /** Playback position in seconds. */
        var position: Long? = null
        /** 1 if this packet changes playback state, 0 if it's a ping-only update. */
        var changeState: Int = 0
        var play: Boolean? = null

        override fun build() = buildJsonObject {
            putJsonObject("State") {
                val positionAndPausedIsSet = position != null && play != null
                val clientIgnoreIsNotSet = p.clientIgnFly == 0 || p.serverIgnFly != 0

                if (clientIgnoreIsNotSet && positionAndPausedIsSet) {
                    val playstate = buildJsonObject {
                        put("position", (position?.toDouble() ?: 0.0))
                        put("paused", play?.let { !it })
                        if (doSeek != null) put("doSeek", doSeek)
                    }
                    put("playstate", playstate)
                }

                val ping = buildJsonObject {
                    serverTime?.let { put("latencyCalculation", it) }
                    put("clientLatencyCalculation", clientTime)
                    put("clientRtt", p.pingService.rtt)
                }
                put("ping", ping)

                if (changeState == 1) {
                    p.clientIgnFly += 1
                }

                if (p.clientIgnFly != 0 || p.serverIgnFly != 0) {
                    val ignoringOnTheFly = buildJsonObject {
                        if (p.serverIgnFly != 0) {
                            put("server", p.serverIgnFly)
                            p.serverIgnFly = 0
                        }
                        if (p.clientIgnFly != 0) {
                            put("client", p.clientIgnFly)
                        }
                    }
                    put("ignoringOnTheFly", ignoringOnTheFly)
                }
            }
        }
    }

    /** Replaces the shared playlist with a new list of files. */
    @ProtocolApi
    class PlaylistChange : ClientMessage() {
        var files: List<String> = emptyList()

        override fun build() = buildJsonObject {
            putJsonObject("Set") {
                putJsonObject("playlistChange") {
                    putJsonArray("files") {
                        for (element in files) add(element)
                    }
                }
            }
        }
    }

    /** Changes the selected item in the shared playlist for all users. */
    @ProtocolApi
    class PlaylistIndex : ClientMessage() {
        var index: Int = 0

        override fun build() = buildJsonObject {
            putJsonObject("Set") {
                putJsonObject("playlistIndex") {
                    put("index", index)
                }
            }
        }
    }

    /** Authenticates as a room operator in a managed room. */
    @ProtocolApi
    class ControllerAuth : ClientMessage() {
        var room: String = ""
        var password: String = ""

        override fun build() = buildJsonObject {
            putJsonObject("Set") {
                putJsonObject("controllerAuth") {
                    put("room", room)
                    put("password", password)
                }
            }
        }
    }

    /** Moves the client to a different room on the same server. */
    @ProtocolApi
    class RoomChange : ClientMessage() {
        var room: String = ""

        override fun build() = buildJsonObject {
            putJsonObject("Set") {
                putJsonObject("room") {
                    put("name", room)
                }
            }
        }
    }

    /** Requests the server to upgrade the connection to TLS. */
    @ProtocolApi
    class TLS : ClientMessage() {
        override fun build() = buildJsonObject {
            putJsonObject("TLS") {
                put("startTLS", "send")
            }
        }
    }
}