package com.chromaticnoob.syncplayprotocol

import com.chromaticnoob.syncplayutils.SyncplayUtils.loggy
import com.google.gson.JsonElement
import com.google.gson.JsonObject

object SPJsonHandler {
    /** Handlers that parse JSONs and control callbacks based on the incoming message from server */

    @JvmStatic
    fun handleJson(json: String, protocol: SyncplayProtocol) {
        /* First, we tell our protocol that we're connected if we're receiving JSONs */
        protocol.connected = true
        loggy("Server: $json")
        /* Second, we check what kind of JSON message we received from the first Json Object */
        val jsonElement = protocol.gson.fromJson(json, JsonElement::class.java)
        if (!jsonElement.isJsonObject) return /* Just making sure server doesn't send malformed JSON */
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

    @JvmStatic
    private fun handleHello(hello: JsonObject, p: SyncplayProtocol) {
        p.currentUsername = hello.getAsJsonPrimitive("username").asString /* Corrected Username */
        p.sendPacket(SPWrappers.sendJoined(p.currentRoom))
        p.sendPacket(SPWrappers.sendEmptyList())
        p.sendPacket(SPWrappers.sendReadiness(p.ready, false))
        p.connected = true
        p.syncplayBroadcaster?.onJoined()
    }

    /** TODO: FIXME **/
    @JvmStatic
    private fun handleSet(set: JsonObject, p: SyncplayProtocol) {
        val stringSet = set.toString()
        if (stringSet.contains("event", true)) {
            val getuser = set.getAsJsonObject("user").keySet().toList()
            val user = getuser[0]
            if (stringSet.contains("\"left\": true")) {
                p.syncplayBroadcaster?.onSomeoneLeft(user)
            }
            if (stringSet.contains("\"joined\": true")) {
                p.syncplayBroadcaster?.onSomeoneJoined(user)
            }
        }
        p.sendPacket(SPWrappers.sendEmptyList())
    }

    /** TODO: FIXME **/
    @JvmStatic
    private fun handleList(list: JsonObject, p: SyncplayProtocol) {
        val userlist = list.getAsJsonObject(p.currentRoom)
        val userkeys = userlist.keySet()
        var indexer = 1
        val tempUserList: MutableMap<String, MutableList<String>> = mutableMapOf()
        for (user in userkeys) {
            val userProperties: MutableList<String> = mutableListOf()
            var userindex = 0
            if (user != p.currentUsername) {
                userindex = indexer
                indexer += 1
            }
            val readiness = if (userlist.getAsJsonObject(user).get("isReady").isJsonNull) {
                "null"
            } else {
                userlist.getAsJsonObject(user).getAsJsonPrimitive("isReady").asString
            }

            val file = userlist
                .getAsJsonObject(user)
                .getAsJsonObject("file")

            var filename = ""
            var fileduration = ""
            var filesize = ""

            if (file.keySet().contains("name")) {
                filename = file.getAsJsonPrimitive("name").toString()
                fileduration = file.getAsJsonPrimitive("duration").toString()
                filesize = file.getAsJsonPrimitive("size").toString()
            }

            if (p.userList.keys.contains(user)) {
                if (p.userList[user]?.get(2) != filename) {
                    p.syncplayBroadcaster?.onSomeoneLoadedFile(
                        user,
                        filename,
                        fileduration,
                        filesize
                    )
                }
            }

            userProperties.add(0, userindex.toString())
            userProperties.add(1, readiness)
            userProperties.add(2, filename)
            userProperties.add(3, fileduration)
            userProperties.add(4, filesize)

            tempUserList[user] = userProperties
        }

        p.userList = tempUserList
        p.syncplayBroadcaster?.onReceivedList()
    }

    @JvmStatic
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
                val seeker: String = playstate.get("setBy").asString
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
                        SPWrappers.sendState(
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
                        SPWrappers.sendState(
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
            val positionOf: String = playstate.get("setBy").toString()

            /* Rewind check if someone is behind */
            if (positionOf != (protocol.currentUsername)) {
                if (position < (protocol.currentVideoPosition - protocol.rewindThreshold)) {
                    protocol.syncplayBroadcaster?.onSomeoneBehind(positionOf, position)
                }
            }

            /* Constant Traditional Pinging if no command is received. */
            protocol.sendPacket(
                SPWrappers.sendState(
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

    @JvmStatic
    private fun handleChat(chat: JsonObject, p: SyncplayProtocol) {
        p.syncplayBroadcaster?.onChatReceived(
            chat.get("username").asString,
            chat.get("message").asString
        )
    }

    /** The following functions are not made yet cuz I see them almost unnecessary ATM */
    @JvmStatic
    private fun handleError(error: JsonObject, p: SyncplayProtocol) {
    }

    @JvmStatic
    private fun handleTLS(hello: JsonObject, p: SyncplayProtocol) {
    }

    @JvmStatic
    private fun dropError(error: String) {
    }
}