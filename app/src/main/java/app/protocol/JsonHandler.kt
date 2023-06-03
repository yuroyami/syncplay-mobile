package app.protocol

import androidx.compose.runtime.mutableStateOf
import app.utils.MiscUtils.loggy
import app.wrappers.MediaFile
import app.wrappers.User
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException

object JsonHandler {
    /** Handlers that parse JSONs and control callbacks based on the incoming message from server */

    fun handleJson(json: String, protocol: SyncplayProtocol) {
        loggy("Server: $json")

        /* Second, we check what kind of JSON message we received from the first Json Object */
        val jsonElement: JsonElement?
        try {
            jsonElement = protocol.gson.fromJson(json, JsonElement::class.java)
            if (!jsonElement.isJsonObject) return /* Just making sure server doesn't send malformed JSON */
        } catch (e: JsonSyntaxException) {
            return
        }
        val message = jsonElement.asJsonObject
        when (message.keySet().toList()[0]) {
            "Hello" -> handleHello(message.getAsJsonObject("Hello"), protocol)
            "Set" -> handleSet(message.getAsJsonObject("Set"), protocol)
            "List" -> handleList(message.getAsJsonObject("List"), protocol)
            "State" -> handleState(message.getAsJsonObject("State"), protocol, json)
            "Chat" -> handleChat(message.getAsJsonObject("Chat"), protocol)
            "Error" -> handleError(message.getAsJsonObject("Error"), protocol)
            "TLS" -> handleTLS(message.getAsJsonObject("TLS"), protocol)
            else -> dropError("unknown-command-server-error")
        }
    }

    private fun handleHello(hello: JsonObject, p: SyncplayProtocol) {
        p.session.currentUsername =
            hello.getAsJsonPrimitive("username").asString /* Corrected Username */
        p.sendPacket(JsonSender.sendJoined(p.session.currentRoom))
        p.sendPacket(JsonSender.sendEmptyList())
        //p.sendPacket(JsonSender.sendReadiness(p.ready, false))
        p.syncplayBroadcaster?.onConnected()
    }

    private fun handleSet(set: JsonObject, p: SyncplayProtocol) {
        /* Sets can carry a variety of commands: "ready", "user left/joined", shared playlist commands etc */
        val command = when {
            set.has("user") -> "user"
            set.has("room") -> "room"
            set.has("controllerAuth") -> "controllerAuth"
            set.has("newControlledRoom") -> "newControlledRoom"
            set.has("ready") -> "ready"
            set.has("playlistIndex") -> "playlistIndex"
            set.has("playlistChange") -> "playlistChange"
            set.has("features") -> "features"
            else -> ""
        }

        when (command) {
            "user" -> {
                /* a "user" command can mean a user joined, left, or added a file. **/
                val user = set.getAsJsonObject("user").keySet().toList()[0]
                val content = set.getAsJsonObject("user").getAsJsonObject(user)

                /* Checking if there is an event in question */
                if (content.has("event")) {
                    val event = content["event"].asJsonObject
                    if (event.has("left")) {
                        p.syncplayBroadcaster?.onSomeoneLeft(user)
                    }
                    if (event.has("joined")) {
                        p.syncplayBroadcaster?.onSomeoneJoined(user)
                    }
                }

                /* Checking if there is a file addition in question */
                if (content.has("file")) {
                    val file = content["file"].asJsonObject
                    p.syncplayBroadcaster?.onSomeoneLoadedFile(
                        user,
                        file.get("name").asString,
                        file.get("duration").asDouble
                        //file.get("size").asString
                    )
                }
            }

            "playlistIndex" -> {
                val playlistIndex = set.getAsJsonObject("playlistIndex")
                val userMeant = playlistIndex.get("user")
                val newIndex = playlistIndex.get("index")
                if (!userMeant.isJsonNull && !newIndex.isJsonNull) {
                    p.syncplayBroadcaster?.onPlaylistIndexChanged(
                        userMeant.asString,
                        newIndex.asInt
                    )
                    p.session.sharedPlaylistIndex = newIndex.asInt
                } else return
            }

            "playlistChange" -> {
                val playlistChange = set.getAsJsonObject("playlistChange")
                val userMeant =
                    if (playlistChange.get("user").isJsonNull) "" else playlistChange.get("user").asString
                val fileArray = playlistChange.getAsJsonArray("files")
                p.session.sharedPlaylist.clear()
                for (file in fileArray) {
                    p.session.sharedPlaylist.add(file.asString)
                }
//                if (userMeant != "")
                p.syncplayBroadcaster?.onPlaylistUpdated(userMeant)
            }
        }

        /* Fetching a list of users anyway (their readiness, files..etc) */
        p.sendPacket(JsonSender.sendEmptyList())
    }

    private fun handleList(list: JsonObject, p: SyncplayProtocol) {
        val userlist = list.getAsJsonObject(p.session.currentRoom)
        val userkeys = userlist.keySet() /* Getting user list */
        p.session.userList.clear() /* Clearing the list before populating it again */

        var indexer = 1 /* This indexer is used to put the main user ahead of others in the list */
        for (user in userkeys) {
            val USER = User(
                name = user,
                index = run {
                    var userindex = 0
                    if (user != p.session.currentUsername) {
                        userindex = indexer
                        indexer += 1
                    }
                    userindex
                },
                readiness = if (userlist.getAsJsonObject(user).get("isReady").isJsonNull) {
                    false
                } else {
                    userlist.getAsJsonObject(user).getAsJsonPrimitive("isReady").asBoolean
                },
                file = run {
                    val file = userlist.getAsJsonObject(user).getAsJsonObject("file")

                    var mediaFile: MediaFile? = MediaFile()

                    if (file.keySet().contains("name")) {
                        mediaFile?.fileName = file.getAsJsonPrimitive("name").toString()
                        mediaFile?.fileDuration = file.getAsJsonPrimitive("duration").asDouble
                        mediaFile?.fileSize = file.getAsJsonPrimitive("size").asString
                    } else {
                        mediaFile = null
                    }

                    mediaFile
                }
            )

            p.session.userList.add(USER)
        }
        p.syncplayBroadcaster?.onReceivedList()
    }

    private fun handleState(state: JsonObject, protocol: SyncplayProtocol, json: String) {

        val clienttime = (System.currentTimeMillis() / 1000.0)
        val latency = state.getAsJsonObject("ping").get("latencyCalculation").asDouble

        if (state.has("ignoringOnTheFly")) {
            if ((json.contains("server\":"))) {
                val playstate = state.getAsJsonObject("playstate")
                val doSeekElement = playstate.get("doSeek")
                val doSeek: Boolean? =
                    if (doSeekElement.isJsonNull) null else doSeekElement.asBoolean
                val seekedPosition = playstate.get("position").asDouble
                val seeker: String =
                    if (playstate.get("setBy").isJsonNull) "" else playstate.get("setBy").asString
                if (doSeek == true) {
                    protocol.syncplayBroadcaster?.onSomeoneSeeked(seeker, seekedPosition)
                }
                if (doSeek == false || doSeek == null) {
                    protocol.paused = json.contains("\"paused\": true", true)
                    if (!protocol.paused) {
                        protocol.syncplayBroadcaster?.onSomeonePlayed(seeker)
                    } else {
                        protocol.syncplayBroadcaster?.onSomeonePaused(seeker)
                    }
                }

                protocol.serverIgnFly =
                    state.getAsJsonObject("ignoringOnTheFly").get("server").asInt
                protocol.clientIgnFly = 0

                if (!(json.contains("client\":"))) {
                    protocol.sendPacket(
                        JsonSender.sendState(
                            latency,
                            clienttime,
                            null,
                            0,
                            iChangeState = 0,
                            play = null,
                            protocol = protocol
                        )
                    )
                } else {
                    protocol.sendPacket(
                        JsonSender.sendState(
                            latency,
                            clienttime,
                            doSeek,
                            0,
                            0,
                            null,
                            protocol
                        )
                    )
                }
            }
        } else {
            val playstate = state.getAsJsonObject("playstate")
            val position = playstate.get("position").asDouble
            val positionOf: String =
                if (!playstate.get("setBy").isJsonNull) playstate.get("setBy").asString else ""

            /* Rewind check if someone is behind */
            if (positionOf != (protocol.session.currentUsername) && positionOf != "") {
                if (position < (protocol.currentVideoPosition - protocol.rewindThreshold)) {
                    protocol.syncplayBroadcaster?.onSomeoneBehind(positionOf, position)
                }
            }

            /* Constant Traditional Pinging if no command is received. */
            protocol.sendPacket(
                JsonSender.sendState(
                    latency,
                    clienttime,
                    false,
                    0,
                    0,
                    null,
                    protocol
                )
            )
        }

    }

    private fun handleChat(chat: JsonObject, p: SyncplayProtocol) {
        p.syncplayBroadcaster?.onChatReceived(
            chat.get("username").asString,
            chat.get("message").asString
        )
    }

    private fun handleTLS(tls: JsonObject, p: SyncplayProtocol) {
        p.syncplayBroadcaster?.onReceivedTLS(tls.get("startTLS").asBoolean)
    }

    /** The following functions are not made yet cuz I see them almost unnecessary ATM */
    private fun handleError(error: JsonObject, p: SyncplayProtocol) {
    }


    private fun dropError(error: String) {
    }
}