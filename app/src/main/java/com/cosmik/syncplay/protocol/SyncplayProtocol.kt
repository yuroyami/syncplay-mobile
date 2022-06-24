package com.cosmik.syncplay.protocol

import android.util.Log
import androidx.lifecycle.ViewModel
import com.cosmik.syncplay.protocol.SPJsonHandler.extractJson
import com.cosmik.syncplay.protocol.SPWrappers.sendHello
import com.cosmik.syncplay.room.Message
import com.cosmik.syncplay.toolkit.SyncplayUtils.loggy
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

    var paused: Boolean = true
    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0
    val gson: Gson = GsonBuilder().setPrettyPrinting().create()
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
    var messageSequence: MutableList<Message> = mutableListOf()

    lateinit var syncplayBroadcaster: SPBroadcaster

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
                                            extractJson(json, host, port, this@SyncplayProtocol)
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

    // Fragment will call this to link its listener with this class's listener
    //Anything called to this class's broadcaster will be overridden in the fragment's implemented broadcaster.
    open fun addBroadcaster(broadcaster: SPBroadcaster) {
        this.syncplayBroadcaster = broadcaster
    }
}