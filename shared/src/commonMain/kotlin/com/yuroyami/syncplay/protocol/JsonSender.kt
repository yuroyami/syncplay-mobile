@file:OptIn(ExperimentalSerializationApi::class)
package com.yuroyami.syncplay.protocol

import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_GLOBAL_SETTINGS
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_HASH_FILENAME
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_HASH_FILESIZE
import com.yuroyami.syncplay.datastore.obtainString
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.protocol.json.DynamicLookupSerializer
import com.yuroyami.syncplay.utils.md5
import com.yuroyami.syncplay.utils.toHex
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** This class does not actually send anything but what it actually does is compose JSON strings which will be sent later */
object JsonSender {

    val anySerializer = DynamicLookupSerializer()

    fun sendHello(username: String, roomname: String, serverPassword: String): String {
        val hello = buildJsonObject {
            put("username", username)

            if (serverPassword.isNotEmpty()) {
                put("password", md5(serverPassword).toHex())
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
                put("managedRooms", true)
            })
        }

        val wrapper = buildJsonObject {
            put("Hello", hello)
        }

        return Json.encodeToString(wrapper)
    }

    fun sendJoined(roomname: String): String {
        val event: MutableMap<String, Any> = mutableMapOf()
        event["joined"] = true

        val room: MutableMap<String, String> = mutableMapOf()
        room["name"] = roomname

        val username: MutableMap<String, Any> = mutableMapOf()
        username["room"] = room
        username["event"] = event

        val user: MutableMap<String, Any> = mutableMapOf()
        user[roomname] = username

        val wrapper: MutableMap<String, MutableMap<String, Any>> = mutableMapOf()
        wrapper["Set"] = user
        return Json.encodeToString(wrapper)
    }

    fun sendReadiness(isReady: Boolean, manuallyInitiated: Boolean): String {
        val ready: MutableMap<String, Boolean> = mutableMapOf()
        ready["isReady"] = isReady
        ready["manuallyInitiated"] = manuallyInitiated

        val setting: MutableMap<String, Any> = mutableMapOf()
        setting["ready"] = ready

        val wrapper: MutableMap<String, Any> = mutableMapOf()
        wrapper["Set"] = setting

        return Json.encodeToString(wrapper)
    }

    fun sendFile(media: MediaFile): String {
        /** Checking whether file name or file size have to be hashed **/
        val nameBehavior = runBlocking { DATASTORE_GLOBAL_SETTINGS.obtainString(PREF_HASH_FILENAME, "1") }
        val sizeBehavior = runBlocking { DATASTORE_GLOBAL_SETTINGS.obtainString(PREF_HASH_FILESIZE, "1") }

        val fileproperties: MutableMap<String, Any> = mutableMapOf()
        fileproperties["duration"] = media.fileDuration
        fileproperties["name"] = when (nameBehavior) {
            "1" -> media.fileName
            "2" -> media.fileNameHashed.take(12)
            else -> ""
        }
        fileproperties["size"] = when (sizeBehavior) {
            "1" -> media.fileSize
            "2" -> media.fileSizeHashed.take(12)
            else -> ""
        }

        val file: MutableMap<String, Any> = mutableMapOf()
        file["file"] = fileproperties

        val wrapper: MutableMap<String, Any> = mutableMapOf()
        wrapper["Set"] = file

        return Json.encodeToString(wrapper)
    }

    fun sendEmptyList(): String {
        val emptylist: MutableMap<String, Any?> = mutableMapOf()
        emptylist["List"] = listOf<String>() //TODO:Check

        return Json.encodeToString(emptylist)
    }

    fun sendChat(message: String): String {
        val wrapper: MutableMap<String, Any> = mutableMapOf()
        wrapper["Chat"] = message

        return Json.encodeToString(wrapper)
    }

    fun sendState(
        servertime: Double?,
        clienttime: Double,
        doSeek: Boolean?,
        seekPosition: Long = 0,
        iChangeState: Int,
        play: Boolean?,
        protocol: SyncplayProtocol,
    ): String {

        val state: MutableMap<String, Any?> = mutableMapOf()
        val playstate: MutableMap<String, Any?> = mutableMapOf()
        if (doSeek == true) {
            playstate["position"] = seekPosition.toDouble() / 1000.0
        } else {
            playstate["position"] = protocol.currentVideoPosition.toFloat()
        }
        playstate["paused"] = protocol.paused
        playstate["doSeek"] = doSeek
        val ping: MutableMap<String, Any?> = mutableMapOf()
        if (servertime != null) {
            ping["latencyCalculation"] = servertime
        }
        ping["clientLatencyCalculation"] = clienttime
        ping["clientRtt"] = protocol.ping.value

        if (iChangeState == 1) {
            val ignore: MutableMap<String, Any?> = mutableMapOf()
            ignore["client"] = protocol.clientIgnFly
            state["ignoringOnTheFly"] = ignore
            playstate["paused"] = !play!!

        } else {
            if (protocol.serverIgnFly != 0) {
                val ignore: MutableMap<String, Any?> = mutableMapOf()
                ignore["server"] = protocol.serverIgnFly
                state["ignoringOnTheFly"] = ignore
                protocol.serverIgnFly = 0
            }
        }

        state["playstate"] = playstate
        state["ping"] = ping

        val statewrapper: MutableMap<String, Any?> = mutableMapOf()
        statewrapper["State"] = state

        return Json.encodeToString(statewrapper)
    }

    fun sendPlaylistChange(list: List<String>): String {
        val files: MutableMap<String, Any?> = mutableMapOf()
        files["files"] = list

        val playlistChange: MutableMap<String, Any?> = mutableMapOf()
        playlistChange["playlistChange"] = files

        val set: MutableMap<String, Any?> = mutableMapOf()
        set["Set"] = playlistChange

        return Json.encodeToString(set)
    }

    fun sendPlaylistIndex(i: Int): String {
        val index: MutableMap<String, Any?> = mutableMapOf()
        index["index"] = i

        val playlistIndex: MutableMap<String, Any?> = mutableMapOf()
        playlistIndex["playlistIndex"] = index

        val set: MutableMap<String, Any?> = mutableMapOf()
        set["Set"] = playlistIndex

        return Json.encodeToString(set)
    }

    fun sendTLS(): String {
        val tls: MutableMap<String, String> = mutableMapOf()
        tls["startTLS"] = "send"

        val wrapper: MutableMap<String, Any> = mutableMapOf()
        wrapper["TLS"] = tls

        return Json.encodeToString(wrapper)
    }


}