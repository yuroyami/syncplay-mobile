package com.cosmik.syncplay.protocol

import android.util.Log
import androidx.lifecycle.ViewModel
import com.cosmik.syncplay.protocol.SyncplayProtocolUtils.sendEmptyList
import com.cosmik.syncplay.protocol.SyncplayProtocolUtils.sendHello
import com.cosmik.syncplay.protocol.SyncplayProtocolUtils.sendJoined
import com.cosmik.syncplay.protocol.SyncplayProtocolUtils.sendReadiness
import com.cosmik.syncplay.protocol.SyncplayProtocolUtils.sendState
import com.cosmik.syncplay.toolkit.SyncplayUtils.loggy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.net.Socket
import java.net.SocketException
import java.net.UnknownHostException

@OptIn(DelicateCoroutinesApi::class)
open class SyncplayProtocol : ViewModel() {

    var paused: Boolean = true
    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    var socket: Socket = Socket()

    //ViewModel
    var ready = false
    var currentVideoPosition: Double = 0.0
    var currentVideoName: String = ""
    var currentVideoLength: Double = 0.0
    var currentVideoSize: Int = 0
    var serverHost: String = "151.80.32.178"
    var serverPort: Int = 8999
    var connected = false
    var currentUsername: String = "username_${(0..999999999999).random()}"
    var currentRoom: String = "roomname"
    var rewindThreshold = 12L

    //User Properties: User Index // Readiness // File Name // File Duration // File Size
    var userList: MutableMap<String, MutableList<String>> = mutableMapOf()
    var messageSequence: MutableList<String> = mutableListOf()

    private lateinit var syncplayBroadcaster: SyncplayBroadcaster

    fun connect(host: String, port: Int) {
        syncplayBroadcaster.onConnectionAttempt(port.toString())
        sendPacket(sendHello(currentUsername, currentRoom), host, port)
    }

    fun sendPacket(json: String, host: String, port: Int) {
        val jsonEncoded = "$json\r\n".encodeToByteArray()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (!connected) {
                    try {
                        socket = Socket(host, port)
                    } catch (e: Exception) {
                        delay(2000) //Safety-interval delay.
                        syncplayBroadcaster.onConnectionFailed()
                        when (e) {
                            is UnknownHostException -> {
                                loggy("SOCKET UnknownHostException")
                            }
                            is IOException -> {
                                loggy("SOCKET IOException")
                            }
                            is SecurityException -> {
                                loggy("SOCKET SecurityException")
                            }
                            else -> {
                                e.printStackTrace()
                            }
                        }
                    }

                    if (!socket.isClosed && socket.isConnected) {
                        syncplayBroadcaster.onReconnected()
                        connected = true
                        readPacket(host, port)
                    }
                }
                val dOut = socket.outputStream
                dOut.write(jsonEncoded)
            } catch (s: SocketException) {
                loggy("Socket: ${s.stackTrace}")
            }
        }
    }

    private fun readPacket(host: String, port: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                    if (socket.isConnected) {
                        socket.getInputStream().also { input ->
                            if (!socket.isInputShutdown) {
                                try {
                                    BufferedInputStream(input).reader(Charsets.UTF_8)
                                        .forEachLine { json ->
                                            extractJson(json, host, port)
                                        }
                                } catch (s: SocketException) {
                                    connected = false
                                    syncplayBroadcaster.onDisconnected()
                                    s.printStackTrace()
                                }
                            } else {
                                Log.e("Server:", "STREAMED SHUTDOWN")
                            }
                        }
                    } else {
                        connected = false
                    }
                delay(300)
            }
        }
    }

    private fun extractJson(json: String, host: String, port: Int) {
        connected = true
        Log.e("Server:", json)
        val jsoner = gson.fromJson(json, JsonObject::class.java)
        val jsonHeader = jsoner.keySet().toList()[0]
        /********************
         * Handling Hellos *
         *******************/
        if (jsonHeader == "Hello") {
            val trueusername = jsoner
                .getAsJsonObject("Hello")
                .getAsJsonPrimitive("username").asString
            currentUsername = trueusername
            sendPacket(sendJoined(currentRoom), host, port)
            sendPacket(sendEmptyList(), host, port)
            sendPacket(
                sendReadiness(ready),
                host,
                port
            )
            connected = true
            syncplayBroadcaster.onJoined()
            //periodicNetworkCheckup(associatedViewModel.serverHost, associatedViewModel.serverPort)
        }
        /********************
         * Handling Lists *
         *******************/
        if (jsonHeader == "List") {
            val userlist =
                jsoner.getAsJsonObject("List").getAsJsonObject(currentRoom)
            val userkeys = userlist.keySet()
            var indexer = 1
            val tempUserList: MutableMap<String, MutableList<String>> = mutableMapOf()
            for (user in userkeys) {
                val userProperties: MutableList<String> = mutableListOf()
                var userindex = 0
                if (user != currentUsername) {
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

                if (userList.keys.contains(user)) {
                    if (userList[user]?.get(2) != filename) {
                        syncplayBroadcaster.onSomeoneLoadedFile(
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

            userList = tempUserList
            syncplayBroadcaster.onReceivedList()
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
                    syncplayBroadcaster.onSomeoneLeft(user)
                }
                if (json.contains("\"joined\": true")) {
                    syncplayBroadcaster.onSomeoneJoined(user)
                }
            }

            sendPacket(sendEmptyList(), host, port)
        }
        /********************
         * Handling Chats *
         *******************/
        if (jsonHeader == "Chat") {
            val chatter = JSONObject(JSONObject(json).get("Chat").toString()).optString("username")
            val message = JSONObject(JSONObject(json).get("Chat").toString()).optString("message")
            syncplayBroadcaster.onChatReceived(chatter, message)
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
                        syncplayBroadcaster.onSomeoneSeeked(
                            seeker,
                            seekedPosition
                        )
                    }
                    if ((doSeek == false) || (doSeek == null)) {
                        paused =
                            json.contains("\"paused\": true", true)
                        if (!paused) {
                            syncplayBroadcaster.onSomeonePlayed(
                                seeker
                            )
                        } else {
                            syncplayBroadcaster.onSomeonePaused(
                                seeker
                            )
                        }
                    }
                    serverIgnFly = JSONObject(
                        JSONObject(
                            JSONObject(json).get("State").toString()
                        ).get("ignoringOnTheFly").toString()
                    ).optInt("server")
                    val clienttime =
                        (System.currentTimeMillis() / 1000.0)

                    if (!(json.contains("client\":"))) {
                        clientIgnFly = 0
                        sendPacket(
                            sendState(
                                latency,
                                clienttime,
                                null, 0,
                                iChangeState = 0,
                                play = null,
                                protocol = this
                            ),
                            host,
                            port
                        )
                    } else {
                        clientIgnFly = 0
                        sendPacket(
                            sendState(
                                latency,
                                clienttime,
                                doSeek, 0,
                                0,
                                null,
                                this
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
                val threshold = rewindThreshold
                if (seeker != (currentUsername)) {
                    if (seekedPosition < (currentVideoPosition - threshold)) {
                        syncplayBroadcaster.onSomeoneBehind(seeker, seekedPosition)
                    }
                }
                //Constant Traditional Pinging if no command is received.
                val clienttime =
                    (System.currentTimeMillis() / 1000.0)
                sendPacket(
                    sendState(latency, clienttime, false, 0, 0, null, this),
                    host,
                    port
                )
            }
        }
    }

    // Fragment will call this to link its listener with this class's listener
    //Anything called to this class's broadcaster will be overridden in the fragment's implemented broadcaster.
    open fun addBroadcaster(broadcaster: SyncplayBroadcaster) {
        this.syncplayBroadcaster = broadcaster
    }
}