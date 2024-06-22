@file:OptIn(ExperimentalSerializationApi::class)
package com.yuroyami.syncplay.protocol

import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_HASH_FILENAME
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_HASH_FILESIZE
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.utils.md5
import com.yuroyami.syncplay.utils.toHex
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** This class does not actually send anything but what it actually does is compose JSON strings which will be sent later */
object JsonSender {

    fun sendHello(username: String, roomname: String, serverPassword: String): String {
        val hello = buildJsonObject {
            put("username", username)

            if (serverPassword.isNotEmpty()) {
                put("password", md5(serverPassword).toHex().lowercase())
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

    fun sendJoined(roomname: String): String {
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

    fun sendReadiness(isReady: Boolean, manuallyInitiated: Boolean): String {
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

    fun sendFile(media: MediaFile): String {
        /** Checking whether file name or file size have to be hashed **/
        val nameBehavior = valueBlockingly(PREF_HASH_FILENAME, "1")
        val sizeBehavior = valueBlockingly(PREF_HASH_FILESIZE, "1")

        val fileproperties = buildJsonObject {
            put("duration", media.fileDuration)
            put("name", when (nameBehavior) {
                "1" -> media.fileName
                "2" -> media.fileNameHashed.take(12)
                else -> ""
            })
            put("size", when (sizeBehavior) {
                "1" -> media.fileSize
                "2" -> media.fileSizeHashed.take(12)
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

    fun sendEmptyList(): String {
        val emptylist = buildJsonObject {
            putJsonArray("List") {
                //TODO: Check equivalent in Python code
            }
        }
        return Json.encodeToString(emptylist)
    }

    fun sendChat(message: String): String {
        val wrapper = buildJsonObject {
            put("Chat", message)
        }

        return Json.encodeToString(wrapper)
    }

    fun sendState(
        servertime: Double?,
        clienttime: Double,
        doSeek: Boolean?,
        seekPosition: Long = 0,
        iChangeState: Int,
        play: Boolean?
    ): String {
        val state = buildJsonObject {
            val playstate = buildJsonObject {
                if (doSeek == true) {
                    put("position", seekPosition.toDouble() / 1000.0)
                } else {
                    put("position", viewmodel!!.p.currentVideoPosition.toFloat())
                }
                put("paused", viewmodel!!.p.paused)
                put("doSeek", doSeek)
            }

            val ping = buildJsonObject {
                servertime?.let { put("latencyCalculation", it) }
                put("clientLatencyCalculation", clienttime)
                put("clientRtt", viewmodel!!.p.ping.value ?: 0)
            }

            if (iChangeState == 1) {
                val ignore = buildJsonObject {
                    put("client", viewmodel!!.p.clientIgnFly)
                }
                put("ignoringOnTheFly", ignore)
                put("playstate", buildJsonObject {
                    put("paused", !(play ?: false))
                })
            } else {
                if (viewmodel!!.p.serverIgnFly != 0) {
                    val ignore = buildJsonObject {
                        put("server", viewmodel!!.p.serverIgnFly)
                    }
                    put("ignoringOnTheFly", ignore)
                    viewmodel!!.p.serverIgnFly = 0
                }
            }

            put("playstate", playstate)
            put("ping", ping)
        }

        val statewrapper = buildJsonObject {
            put("State", state)
        }

        return Json.encodeToString(statewrapper)
    }


    fun sendPlaylistChange(list: List<String>): String {
        val files = buildJsonObject {
            putJsonArray("files") {
                for (element in list) {
                    add(element)
                }
            }
        }

        val playlistChange = buildJsonObject {
            put("playlistChange", files)
        }

        val set = buildJsonObject {
            put("Set", playlistChange)
        }

        return Json.encodeToString(set)
    }

    fun sendPlaylistIndex(i: Int): String {
        val index = buildJsonObject {
            put("index", i)
        }

        val playlistIndex = buildJsonObject {
            put("playlistIndex", index)
        }

        val set = buildJsonObject {
            put("Set", playlistIndex)
        }

        return Json.encodeToString(set)
    }

    fun sendTLS(): String {
        val tls = buildJsonObject {
            put("startTLS", "send")
        }

        val wrapper = buildJsonObject {
            put("TLS", tls)
        }

        return Json.encodeToString(wrapper)
    }

}