package com.chromaticnoob.syncplayprotocol

import android.util.Log
import androidx.lifecycle.ViewModel
import com.chromaticnoob.syncplay.room.Message
import com.chromaticnoob.syncplayprotocol.SPJsonHandler.extractJson
import com.chromaticnoob.syncplayprotocol.SPWrappers.sendHello
import com.chromaticnoob.syncplayutils.SyncplayUtils.loggy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.IOException
import java.net.Socket
import java.net.SocketException
import java.net.UnknownHostException

@OptIn(DelicateCoroutinesApi::class)
open class SyncplayProtocol : ViewModel() {

    /** This refers to the event callback interface */
    var syncplayBroadcaster: SPBroadcaster? = null

    /** Protocol-exclusive variables **/
    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0

    /** Our JSON instance */
    val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** Variables that track user status */
    var paused: Boolean = true
    var ready = false
    var connected = false

    /** Variables related to current video properties */
    var currentVideoPosition: Double = 0.0
    var currentVideoName: String = ""
    var currentVideoLength: Double = 0.0
    var currentVideoSize: Int = 0

    /** Variables related to joining info */
    var serverHost: String = "151.80.32.178"
    var serverPort: Int = 8999
    var currentUsername: String = "username_${(0..999999999999).random()}"
    var currentRoom: String = "roomname"
    var currentPassword: String? = null

    /** Variable that stores all messages that have been sent/received */
    var messageSequence: MutableList<Message> = mutableListOf()

    /** Variable that represents the room's user list */
    var userList: MutableMap<String, MutableList<String>> = mutableMapOf()

    /** Variable that defines rewind threshold **/
    var rewindThreshold = 12L /* This is as per official Syncplay, shouldn't be subject to change */

    /** Instantiating a socket to perform a TCP/IP connection later **/
    var socket: Socket = Socket()

    fun connect(host: String, port: Int, password: String? = null) {
        syncplayBroadcaster?.onConnectionAttempt(port.toString())
        sendPacket(sendHello(currentUsername, currentRoom, password), host, port)
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
                        syncplayBroadcaster?.onConnectionFailed()
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
                        syncplayBroadcaster?.onReconnected()
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
                                        extractJson(json, host, port, this@SyncplayProtocol)
                                    }
                            } catch (s: SocketException) {
                                connected = false
                                syncplayBroadcaster?.onDisconnected()
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

    /** Nothing more than a function that binds an interface between two classes
     * Here's how it works :
     *
     * a) You have 2 classes, and 1 interface (used for callback)
     *
     * b) One class should fire an interface's functions, and another should respond to them
     *
     * c) You put the following addBroadcaster() function in the class that FIRES the events
     *
     * d) You add the interface as a variable instance in the responding class, and make it call this function
     *
     * e) Voila, you get a callback/listener system between two classes easily.
     */
    open fun addBroadcaster(broadcaster: SPBroadcaster) {
        this.syncplayBroadcaster = broadcaster
    }

    open fun removeBroadcaster() {
        this.syncplayBroadcaster = null
    }
}