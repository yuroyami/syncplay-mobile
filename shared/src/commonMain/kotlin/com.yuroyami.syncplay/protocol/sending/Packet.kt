package com.yuroyami.syncplay.protocol.sending

import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.utils.CommonUtils.md5
import com.yuroyami.syncplay.utils.generateTimestampMillis
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

sealed class Packet {
    companion object {
        typealias SendablePacket = String

        inline fun <reified T : Packet> SyncplayProtocol.createPacketInstance(): T {
            return when (T::class) {
                Hello::class -> Hello() as T
                Joined::class -> Joined() as T
                EmptyList::class -> EmptyList() as T
                Readiness::class -> Readiness() as T
                File::class -> File() as T
                Chat::class -> Chat() as T
                State::class -> State(this) as T
                PlaylistChange::class -> PlaylistChange() as T
                PlaylistIndex::class -> PlaylistIndex() as T
                TLS::class -> TLS() as T
                else -> throw IllegalArgumentException("Unknown packet type: ${T::class.simpleName}")
            }
        }
    }

    abstract fun build(): String

    // Packet type definitions as sealed subclasses
    class Hello : Packet() {
        var username: String = ""
        var roomname: String = ""
        var serverPassword: String = ""

        override fun build(): String {
            val hello = buildJsonObject {
                put("username", username)

                if (serverPassword.isNotEmpty()) {
                    put("password", md5(serverPassword).toHexString(HexFormat.Default))
                }

                put("room", buildJsonObject {
                    put("name", roomname)
                })

                put("version", "1.7.0")
                put("features", buildJsonObject {
                    put("sharedPlaylists", true)
                    put("chat", true)
                    put("featureList", true)
                    put("readiness", true)
                    put("managedRooms", false)
                })
            }

            val wrapper = buildJsonObject {
                put("Hello", hello)
            }

            return Json.encodeToString(wrapper)
        }
    }

    class Joined : Packet() {
        var roomname: String = ""

        override fun build(): String {
            val event = buildJsonObject {
                put("joined", true)
            }

            val room = buildJsonObject {
                put("name", roomname)
            }

            val username = buildJsonObject {
                put("room", room)
                put("event", event)
            }

            val user = buildJsonObject {
                put(roomname, username)
            }

            val wrapper = buildJsonObject {
                put("Set", user)
            }

            return Json.encodeToString(wrapper)
        }
    }

    class EmptyList : Packet() {
        override fun build(): String {
            val emptylist = buildJsonObject {
                putJsonArray("List") {}
            }
            return Json.encodeToString(emptylist)
        }
    }

    class Readiness : Packet() {
        var isReady: Boolean = false
        var manuallyInitiated: Boolean = false

        override fun build(): String {
            val ready = buildJsonObject {
                put("isReady", isReady)
                put("manuallyInitiated", manuallyInitiated)
            }

            val setting = buildJsonObject {
                put("ready", ready)
            }

            val wrapper = buildJsonObject {
                put("Set", setting)
            }

            return Json.encodeToString(wrapper)
        }
    }

    class File : Packet() {
        var media: MediaFile? = null

        override fun build(): String {
            requireNotNull(media) { "Media file must be provided" }

            /** Checking whether file name or file size have to be hashed **/
            val nameBehavior = valueBlockingly(DataStoreKeys.PREF_HASH_FILENAME, "1")
            val sizeBehavior = valueBlockingly(DataStoreKeys.PREF_HASH_FILESIZE, "1")

            val fileproperties = buildJsonObject {
                put("duration", media!!.fileDuration)
                put("name", when (nameBehavior) {
                    "1" -> media!!.fileName
                    "2" -> media!!.fileNameHashed.take(12)
                    else -> ""
                })
                put("size", when (sizeBehavior) {
                    "1" -> media!!.fileSize
                    "2" -> media!!.fileSizeHashed.take(12)
                    else -> ""
                })
            }

            val file = buildJsonObject {
                put("file", fileproperties)
            }

            val wrapper = buildJsonObject {
                put("Set", file)
            }

            return Json.encodeToString(wrapper)
        }
    }

    class Chat : Packet() {
        var message: String = ""

        override fun build(): String {
            val wrapper = buildJsonObject {
                put("Chat", message)
            }

            return Json.encodeToString(wrapper)
        }
    }

    class State(private var p: SyncplayProtocol) : Packet() {
        var serverTime: Double? = null
        val clientTime: Double
            get() = generateTimestampMillis() / 1000.0
        var doSeek: Boolean? = null
        var position: Long? = null
        var changeState: Int = 0
        var play: Boolean? = null

        override fun build(): String {
            val state = buildJsonObject state@ {
                val positionAndPausedIsSet = position != null && play != null
                val clientIgnoreIsNotSet = p.clientIgnFly == 0 || p.serverIgnFly != 0

                if (clientIgnoreIsNotSet && positionAndPausedIsSet) {
                    val playstate = buildJsonObject {
                        put("position", (position?.toDouble() ?: 0.0) / 1000.0)
                        put("paused", play?.let { !it } )
                        if (doSeek != null) put("doSeek", doSeek)
                    }
                    put("playstate", playstate)
                }


                val ping = buildJsonObject {
                    serverTime?.let { put("latencyCalculation", it) }
                    put("clientLatencyCalculation", clientTime)
                    put("clientRtt", p.ping.value ?: 0)
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

            val statewrapper = buildJsonObject {
                put("State", state)
            }

            return Json.encodeToString(statewrapper)
        }
    }

    class PlaylistChange : Packet() {
        var files: List<String> = emptyList()

        override fun build(): String {
            val filesJson = buildJsonObject {
                putJsonArray("files") {
                    for (element in files) {
                        add(element)
                    }
                }
            }

            val playlistChange = buildJsonObject {
                put("playlistChange", filesJson)
            }

            val set = buildJsonObject {
                put("Set", playlistChange)
            }

            return Json.encodeToString(set)
        }
    }

    class PlaylistIndex : Packet() {
        var index: Int = 0

        override fun build(): String {
            val indexJson = buildJsonObject {
                put("index", index)
            }

            val playlistIndex = buildJsonObject {
                put("playlistIndex", indexJson)
            }

            val set = buildJsonObject {
                put("Set", playlistIndex)
            }

            return Json.encodeToString(set)
        }
    }

    class TLS : Packet() {
        override fun build(): String {
            val tls = buildJsonObject {
                put("startTLS", "send")
            }

            val wrapper = buildJsonObject {
                put("TLS", tls)
            }

            return Json.encodeToString(wrapper)
        }
    }
}
