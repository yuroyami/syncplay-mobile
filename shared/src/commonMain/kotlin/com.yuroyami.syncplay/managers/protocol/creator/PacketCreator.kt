package com.yuroyami.syncplay.managers.protocol.creator

import com.yuroyami.syncplay.managers.protocol.ProtocolManager
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.valueBlockingly
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.utils.generateTimestampMillis
import com.yuroyami.syncplay.utils.md5
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

sealed class PacketCreator {
    abstract fun build(): JsonElement

    // Packet type definitions as sealed subclasses
    class Hello : PacketCreator() {
        var username: String = ""
        var roomname: String = ""
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

    class Joined : PacketCreator() {
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

    class EmptyList : PacketCreator() {
        override fun build() = buildJsonObject {
            putJsonArray("List") {}
        }
    }

    class Readiness : PacketCreator() {
        var isReady: Boolean = false
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

    class File : PacketCreator() {
        var media: MediaFile? = null

        override fun build() = buildJsonObject {
            requireNotNull(media) { "Media file must be provided" }

            putJsonObject("Set") {
                putJsonObject("file") {
                    /* First, Checking whether file name or file size have to be hashed **/
                    val nameBehavior = valueBlockingly(DataStoreKeys.PREF_HASH_FILENAME, "1")
                    val sizeBehavior = valueBlockingly(DataStoreKeys.PREF_HASH_FILESIZE, "1")

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

    class Chat : PacketCreator() {
        var message: String = ""

        override fun build() = buildJsonObject {
            put("Chat", message)
        }
    }

    class State(private var p: ProtocolManager) : PacketCreator() {
        var serverTime: Double? = null
        val clientTime: Double
            get() = generateTimestampMillis() / 1000.0
        var doSeek: Boolean? = null
        var position: Long? = null
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

    class PlaylistChange : PacketCreator() {
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

    class PlaylistIndex : PacketCreator() {
        var index: Int = 0

        override fun build() = buildJsonObject {
            putJsonObject("Set") {
                putJsonObject("playlistIndex") {
                    put("index", index)
                }
            }
        }
    }

    class ControllerAuth : PacketCreator() {
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

    class RoomChange : PacketCreator() {
        var room: String = ""

        override fun build() = buildJsonObject {
            putJsonObject("Set") {
                putJsonObject("room") {
                    put("name", room)
                }
            }
        }
    }

    class TLS : PacketCreator() {
        override fun build() = buildJsonObject {
            putJsonObject("TLS") {
                put("startTLS", "send")
            }
        }
    }
}