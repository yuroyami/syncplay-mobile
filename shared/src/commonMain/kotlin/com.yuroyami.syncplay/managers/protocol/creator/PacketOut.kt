package com.yuroyami.syncplay.managers.protocol.creator

import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.DatastoreManager.Companion.value
import com.yuroyami.syncplay.managers.protocol.ProtocolManager
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.utils.generateTimestampMillis
import com.yuroyami.syncplay.utils.md5
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Base class for creating Syncplay protocol packets to send to the server.
 *
 * Each packet type is represented as a sealed subclass that builds a JSON structure
 * conforming to the Syncplay protocol specification. Packets are created using a
 * builder pattern where properties are set, then [build] is called to generate the
 * final JSON.
 *
 * ## Usage Example
 * ```kotlin
 * networkManager.send<PacketOut.Chat> {
 *     message = "Hello, room!"
 * }
 * ```
 */
sealed class PacketOut {
    /**
     * Builds the JSON representation of this packet.
     *
     * @return JsonElement ready for serialization and transmission
     */
    abstract fun build(): JsonElement

    /**
     * Initial handshake packet sent when connecting to a Syncplay server.
     *
     * Identifies the client with username, room name, password (if required),
     * version, and supported features.
     */
    class Hello : PacketOut() {
        /** Username to connect with */
        var username: String = ""

        /** Room name to join */
        var roomname: String = ""

        /** Server password (if required), will be MD5 hashed */
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

    /**
     * Packet confirming successful room join.
     *
     * Sent as acknowledgment after the server grants room access.
     */
    class Joined : PacketOut() {
        /** Name of the room that was joined */
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

    /**
     * Empty list packet to clear or acknowledge playlist state.
     */
    class EmptyList : PacketOut() {
        override fun build() = buildJsonObject {
            putJsonArray("List") {}
        }
    }

    /**
     * Packet to set the user's ready state for playback synchronization.
     *
     * Users marked as "ready" will sync playback; unready users are excluded.
     */
    class Readiness : PacketOut() {
        /** Whether the user is ready for synchronized playback */
        var isReady: Boolean = false

        /** Whether this ready state change was user-initiated (true) or automatic (false) */
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
     * Packet declaring the currently loaded media file.
     *
     * Notifies other users in the room about the file being played, including
     * its name, size, and duration. File name/size can be hashed based on
     * privacy preferences.
     */
    class File : PacketOut() {
        /** The media file to declare, must not be null */
        var media: MediaFile? = null

        override fun build() = buildJsonObject {
            requireNotNull(media) { "Media file must be provided" }

            putJsonObject("Set") {
                putJsonObject("file") {
                    /* First, Checking whether file name or file size have to be hashed **/
                    val nameBehavior = value(DataStoreKeys.PREF_HASH_FILENAME, "1")
                    val sizeBehavior = value(DataStoreKeys.PREF_HASH_FILESIZE, "1")

                    /* Now, we put the values in */
                    put("duration", media!!.fileDuration)
                    put(
                        "name", when (nameBehavior) {
                            "1" -> media!!.fileName
                            "2" -> media!!.fileNameHashed.take(12)
                            else -> ""
                        }
                    )
                    put(
                        "size", when (sizeBehavior) {
                            "1" -> media!!.fileSize
                            "2" -> media!!.fileSizeHashed.take(12)
                            else -> ""
                        }
                    )
                }
            }
        }
    }

    /**
     * Chat message packet.
     *
     * Sends a text message to all users in the room.
     */
    class Chat : PacketOut() {
        /** The chat message content */
        var message: String = ""

        override fun build() = buildJsonObject {
            put("Chat", message)
        }
    }

    /**
     * Playback state packet containing position, paused state, and ping information.
     *
     * This is the core synchronization packet sent regularly to keep all users in sync.
     * Includes latency measurements for accurate timing calculations.
     *
     * @property p Protocol manager for accessing server state and ping service
     */
    class State(private var p: ProtocolManager) : PacketOut() {
        /** Server timestamp from last received State packet (for latency calculation) */
        var serverTime: Double? = null

        /** Current client timestamp in seconds since epoch */
        val clientTime: Double
            get() = generateTimestampMillis() / 1000.0

        /** Whether this state change includes a seek operation */
        var doSeek: Boolean? = null

        /** Playback position in seconds */
        var position: Long? = null

        /** Whether this is a state-changing packet (1) or just a ping (0) */
        var changeState: Int = 0

        /** Whether playback is active (true = playing, false = paused) */
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
                    p.clientIgnFly += 1 //Important!
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

    /**
     * Packet to update the shared playlist contents.
     *
     * Replaces the entire playlist with the provided list of files.
     */
    class PlaylistChange : PacketOut() {
        /** List of file paths/URLs to set as the new playlist */
        var files: List<String> = emptyList()

        override fun build() = buildJsonObject {
            putJsonObject("Set") {
                putJsonObject("playlistChange") {
                    putJsonArray("files") {
                        for (element in files) {
                            add(element)
                        }
                    }
                }
            }
        }
    }

    /**
     * Packet to change the currently selected item in the shared playlist.
     *
     * Causes all users to switch to the specified playlist item.
     */
    class PlaylistIndex : PacketOut() {
        /** The playlist index to select (0-based) */
        var index: Int = 0

        override fun build() = buildJsonObject {
            putJsonObject("Set") {
                putJsonObject("playlistIndex") {
                    put("index", index)
                }
            }
        }
    }

    /**
     * Packet to authenticate as a room operator (controller) in a managed room.
     *
     * Provides the room name and operator password to gain elevated privileges.
     */
    class ControllerAuth : PacketOut() {
        /** Name of the managed room */
        var room: String = ""

        /** Operator password for authentication */
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

    /**
     * Packet to request a room change.
     *
     * Moves the client to a different room on the same server.
     */
    class RoomChange : PacketOut() {
        /** Name of the room to switch to */
        var room: String = ""

        override fun build() = buildJsonObject {
            putJsonObject("Set") {
                putJsonObject("room") {
                    put("name", room)
                }
            }
        }
    }

    /**
     * TLS negotiation packet.
     *
     * Requests the server to upgrade the connection to TLS encryption.
     * Sent during the initial handshake if TLS support is being checked.
     */
    class TLS : PacketOut() {
        override fun build() = buildJsonObject {
            putJsonObject("TLS") {
                put("startTLS", "send")
            }
        }
    }
}