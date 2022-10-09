package app.protocol

import android.content.Context
import androidx.preference.PreferenceManager
import app.utils.MiscUtils
import app.utils.MiscUtils.toHex
import app.wrappers.MediaFile
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/** This class does not actually send anything but what it actually does is compose JSON strings which will be sent later */
object JsonSender {

    private val gson: Gson = GsonBuilder().serializeNulls().create()

    fun sendHello(username: String, roomname: String, serverPassword: String?): String {
        val hello: HashMap<String, Any> = hashMapOf()
        hello["username"] = username
        if (serverPassword != null) {
            /* Syncplay servers accept passwords in MD5-Hexadecimal form.*/
            hello["password"] = MiscUtils.md5(serverPassword).toHex()
        }
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

    fun sendReadiness(isReady: Boolean, manuallyInitiated: Boolean): String {
        val ready: HashMap<String, Boolean> = hashMapOf()
        ready["isReady"] = isReady
        ready["manuallyInitiated"] = true

        val setting: HashMap<String, Any> = hashMapOf()
        setting["ready"] = ready

        val wrapper: HashMap<String, Any> = hashMapOf()
        wrapper["Set"] = setting

        return gson.toJson(wrapper)
    }

    fun sendFile(media: MediaFile, context: Context): String {
        /** Checking whether file name or file size have to be hashed **/
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val nameBehavior = sp.getString("fileinfo_behavior_name", "1")
        val sizeBehavior = sp.getString("fileinfo_behavior_size", "1")

        val fileproperties: HashMap<String, Any> = hashMapOf()
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

        val file: HashMap<String, Any> = hashMapOf()
        file["file"] = fileproperties

        val wrapper: HashMap<String, Any> = hashMapOf()
        wrapper["Set"] = file

        return gson.toJson(wrapper)
    }

    fun sendEmptyList(): String {
        val emptylist: HashMap<String, Any?> = hashMapOf()
        emptylist["List"] = listOf<String>() //TODO:Check

        return gson.toJson(emptylist)
    }

    fun sendChat(message: String): String {
        val wrapper: HashMap<String, Any> = hashMapOf()
        wrapper["Chat"] = message

        return gson.toJson(wrapper)
    }

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
        ping["clientRtt"] = protocol.ping

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

    fun sendPlaylistChange(list: List<String>): String {
        val files: HashMap<String, Any?> = hashMapOf()
        files["files"] = list

        val playlistChange: HashMap<String, Any?> = hashMapOf()
        playlistChange["playlistChange"] = files

        val set: HashMap<String, Any?> = hashMapOf()
        set["Set"] = playlistChange

        return gson.toJson(set)
    }

    fun sendPlaylistIndex(i: Int): String {
        val index: HashMap<String, Any?> = hashMapOf()
        index["index"] = i

        val playlistIndex: HashMap<String, Any?> = hashMapOf()
        playlistIndex["playlistIndex"] = index

        val set: HashMap<String, Any?> = hashMapOf()
        set["Set"] = playlistIndex

        return gson.toJson(set)
    }

    fun sendTLS(): String {
        val tls: HashMap<String, String> = hashMapOf()
        tls["startTLS"] = "send"

        val wrapper: HashMap<String, Any> = hashMapOf()
        wrapper["TLS"] = tls

        return gson.toJson(wrapper)
    }


}