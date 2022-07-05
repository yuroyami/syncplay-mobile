package com.chromaticnoob.syncplayprotocol

import com.chromaticnoob.syncplayutils.SyncplayUtils.loggy
import com.google.gson.JsonObject
import org.json.JSONObject

object SPJsonHandler {

    /** Probably the most important function. This breaks down the JSONs received from the server
     * and behaves in accordance with the protocol as per the the JSONs received. */

    @JvmStatic
    fun extractJson(json: String, host: String, port: Int, protocol: SyncplayProtocol) {
        protocol.connected = true
        loggy("Server: $json")
        val jsoner = protocol.gson.fromJson(json, JsonObject::class.java)
        val jsonHeader = jsoner.keySet().toList()[0]
        /********************
         * Handling Hellos *
         *******************/
        if (jsonHeader == "Hello") {
            val trueusername = jsoner
                .getAsJsonObject("Hello")
                .getAsJsonPrimitive("username").asString
            protocol.currentUsername = trueusername
            protocol.sendPacket(SPWrappers.sendJoined(protocol.currentRoom), host, port)
            protocol.sendPacket(SPWrappers.sendEmptyList(), host, port)
            protocol.sendPacket(
                SPWrappers.sendReadiness(protocol.ready),
                host,
                port
            )
            protocol.connected = true
            protocol.syncplayBroadcaster?.onJoined()
        }
        /********************
         * Handling Lists *
         *******************/
        if (jsonHeader == "List") {
            val userlist =
                jsoner.getAsJsonObject("List").getAsJsonObject(protocol.currentRoom)
            val userkeys = userlist.keySet()
            var indexer = 1
            val tempUserList: MutableMap<String, MutableList<String>> = mutableMapOf()
            for (user in userkeys) {
                val userProperties: MutableList<String> = mutableListOf()
                var userindex = 0
                if (user != protocol.currentUsername) {
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

                if (protocol.userList.keys.contains(user)) {
                    if (protocol.userList[user]?.get(2) != filename) {
                        protocol.syncplayBroadcaster?.onSomeoneLoadedFile(
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

            protocol.userList = tempUserList
            protocol.syncplayBroadcaster?.onReceivedList()
        }
        /********************
         * Handling Sets *
         *******************/
        if (jsonHeader == "Set") {
            if (json.contains("event", true)) {
                val getuser = jsoner.getAsJsonObject("Set")
                    .getAsJsonObject("user").keySet().toList()
                val user = getuser[0]
                if (json.contains("\"left\": true")) {
                    protocol.syncplayBroadcaster?.onSomeoneLeft(user)
                }
                if (json.contains("\"joined\": true")) {
                    protocol.syncplayBroadcaster?.onSomeoneJoined(user)
                }
            }

            protocol.sendPacket(SPWrappers.sendEmptyList(), host, port)
        }
        /********************
         * Handling Chats *
         *******************/
        if (jsonHeader == "Chat") {
            val chatter = JSONObject(JSONObject(json).get("Chat").toString()).optString("username")
            val message = JSONObject(JSONObject(json).get("Chat").toString()).optString("message")
            protocol.syncplayBroadcaster?.onChatReceived(chatter, message)
        }
        /********************
         * Handling States *
         *******************/
        if (jsonHeader == "State") {
            val latency = JSONObject(
                JSONObject(JSONObject(json).get("State").toString()).get("ping").toString()
            ).optDouble("latencyCalculation")
            if (json.contains("ignoringonthefly", true)) {
                if ((json.contains("server\":"))) {
                    val doSeek: Boolean? = JSONObject(
                        JSONObject(
                            JSONObject(json).get("State").toString()
                        ).get("playstate").toString()
                    ).optString("doSeek").toBooleanStrictOrNull()
                    val seekedPosition: Double = JSONObject(
                        JSONObject(
                            JSONObject(json).get("State").toString()
                        ).get("playstate").toString()
                    ).optDouble("position")
                    val seeker: String = JSONObject(
                        JSONObject(
                            JSONObject(json).get("State").toString()
                        ).get("playstate").toString()
                    ).optString("setBy")
                    if (doSeek == true) {
                        protocol.syncplayBroadcaster?.onSomeoneSeeked(
                            seeker,
                            seekedPosition
                        )
                    }
                    if ((doSeek == false) || (doSeek == null)) {
                        protocol.paused =
                            json.contains("\"paused\": true", true)
                        if (!protocol.paused) {
                            protocol.syncplayBroadcaster?.onSomeonePlayed(
                                seeker
                            )
                        } else {
                            protocol.syncplayBroadcaster?.onSomeonePaused(
                                seeker
                            )
                        }
                    }
                    protocol.serverIgnFly = JSONObject(
                        JSONObject(
                            JSONObject(json).get("State").toString()
                        ).get("ignoringOnTheFly").toString()
                    ).optInt("server")
                    val clienttime =
                        (System.currentTimeMillis() / 1000.0)

                    if (!(json.contains("client\":"))) {
                        protocol.clientIgnFly = 0
                        protocol.sendPacket(
                            SPWrappers.sendState(
                                latency,
                                clienttime,
                                null, 0,
                                iChangeState = 0,
                                play = null,
                                protocol = protocol
                            ),
                            host,
                            port
                        )
                    } else {
                        protocol.clientIgnFly = 0
                        protocol.sendPacket(
                            SPWrappers.sendState(
                                latency,
                                clienttime,
                                doSeek, 0,
                                0,
                                null,
                                protocol
                            ),
                            host,
                            port
                        )
                    }
                }
            } else {
                val seekedPosition: Double = JSONObject(
                    JSONObject(JSONObject(json).get("State").toString()).get("playstate").toString()
                ).optDouble("position")
                val seeker: String = JSONObject(
                    JSONObject(JSONObject(json).get("State").toString()).get("playstate").toString()
                ).optString("setBy")

                //Rewind check if someone is behind
                val threshold = protocol.rewindThreshold
                if (seeker != (protocol.currentUsername)) {
                    if (seekedPosition < (protocol.currentVideoPosition - threshold)) {
                        protocol.syncplayBroadcaster?.onSomeoneBehind(seeker, seekedPosition)
                    }
                }
                //Constant Traditional Pinging if no command is received.
                val clienttime =
                    (System.currentTimeMillis() / 1000.0)
                protocol.sendPacket(
                    SPWrappers.sendState(
                        latency,
                        clienttime,
                        false,
                        0,
                        0,
                        null,
                        protocol
                    ),
                    host,
                    port
                )
            }
        }
    }

}