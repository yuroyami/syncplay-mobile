package com.cosmik.syncplay.protocol

import com.cosmik.syncplay.toolkit.SyncplayUtils
import com.google.gson.Gson
import com.google.gson.GsonBuilder

object SPWrappers {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val basicgson: Gson = GsonBuilder().create()

    @JvmStatic
    fun sendHello(username: String, roomname: String): String {
        val hello: HashMap<String, Any> = hashMapOf()
        hello["username"] = username
        val room: HashMap<String, String> = hashMapOf()
        room["name"] = roomname
        hello["room"] = room
        hello["version"] = "1.6.9"
        val features: HashMap<String, Boolean> = hashMapOf()
        features["sharedPlaylists"] = false
        features["chat"] = true
        features["featureList"] = true
        features["readiness"] = true
        features["managedRooms"] = true
        hello["features"] = features

        val wrapper: HashMap<String, HashMap<String, Any>> = hashMapOf()
        wrapper["Hello"] = hello
        return gson.toJson(wrapper)
    }

    @JvmStatic
    fun sendJoined(roomname: String): String {
        val event: HashMap<String, Any> = hashMapOf()
        event["joined"] = true
        val room: HashMap<String, String> = hashMapOf()
        room["name"] = roomname

        val username: HashMap<String, Any> = hashMapOf()
        username["room"] = room
        username["event"] = event

        val user: HashMap<String, Any> = hashMapOf()
        user[roomname] = username

        val wrapper: HashMap<String, HashMap<String, Any>> = hashMapOf()
        wrapper["Set"] = user
        return gson.toJson(wrapper)
    }

    @JvmStatic
    fun sendReadiness(isReady: Boolean): String {
        val ready: HashMap<String, Boolean> = hashMapOf()
        ready["isReady"] = isReady
        ready["manuallyInitiated"] = true

        val setting: HashMap<String, Any> = hashMapOf()
        setting["ready"] = ready

        val wrapper: HashMap<String, Any> = hashMapOf()
        wrapper["Set"] = setting

        return gson.toJson(wrapper)
    }

    @JvmStatic
    fun sendFile(length: Double, name: String, size: Int): String {
        val fileproperties: HashMap<String, Any> = hashMapOf()
        fileproperties["duration"] = length
        fileproperties["name"] = name
        fileproperties["size"] = size


        val file: HashMap<String, Any> = hashMapOf()
        file["file"] = fileproperties

        val wrapper: HashMap<String, Any> = hashMapOf()
        wrapper["Set"] = file

        return basicgson.toJson(wrapper)
    }

    @JvmStatic
    fun sendEmptyList(): String {
        val emptylist: HashMap<String, Any?> = hashMapOf()
        emptylist["List"] = null

        return GsonBuilder().serializeNulls().create().toJson(emptylist)
    }

    @JvmStatic
    fun sendChat(message: String): String {
        val wrapper: HashMap<String, Any> = hashMapOf()
        wrapper["Chat"] = message

        return gson.toJson(wrapper)
    }

    @JvmStatic
    fun sendState(
        servertime: Double?,
        clienttime: Double,
        doSeek: Boolean?,
        seekPosition: Long = 0,
        iChangeState: Int,
        play: Boolean?,
        protocol: SyncplayProtocol
    ): String {

        val state: HashMap<String, Any?> = hashMapOf()
        val playstate: HashMap<String, Any?> = hashMapOf()
        if (doSeek == true) {
            playstate["position"] = seekPosition.toDouble() / 1000.0
        } else {
            playstate["position"] = protocol.currentVideoPosition.toFloat()
        }
        playstate["paused"] = protocol.paused
        playstate["doSeek"] = doSeek
        val ping: HashMap<String, Any?> = hashMapOf()
        if (servertime != null) {
            ping["latencyCalculation"] = servertime
        }
        ping["clientLatencyCalculation"] = clienttime
        ping["clientRtt"] = SyncplayUtils.pingIcmp("151.80.32.178", 32)

        if (iChangeState == 1) {
            val ignore: HashMap<String, Any?> = hashMapOf()
            ignore["client"] = protocol.clientIgnFly
            state["ignoringOnTheFly"] = ignore
            playstate["paused"] = !play!!

        } else {
            if (protocol.serverIgnFly != 0) {
                val ignore: HashMap<String, Any?> = hashMapOf()
                ignore["server"] = protocol.serverIgnFly
                state["ignoringOnTheFly"] = ignore
                protocol.serverIgnFly = 0
            }
        }

        state["playstate"] = playstate
        state["ping"] = ping

        val statewrapper: HashMap<String, Any?> = hashMapOf()
        statewrapper["State"] = state

        return gson.toJson(statewrapper)

    }

}