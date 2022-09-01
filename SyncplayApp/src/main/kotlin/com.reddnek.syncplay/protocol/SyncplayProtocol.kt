package com.reddnek.syncplay.protocol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.reddnek.syncplay.protocol.JsonHandler.handleJson
import com.reddnek.syncplay.protocol.JsonSender.sendHello
import com.reddnek.syncplay.utils.SyncplayUtils
import com.reddnek.syncplay.wrappers.MediaFile
import com.reddnek.syncplay.wrappers.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Socket
import java.net.SocketException
import java.net.UnknownHostException

open class SyncplayProtocol : ViewModel() {

    /** This refers to the event callback interface */
    var syncplayBroadcaster: ProtocolBroadcaster? = null

    /** Protocol-exclusive variables - should never change these initial values **/
    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0
    var ping = 0.0
    var rewindThreshold = 12L /* This is as per official Syncplay, shouldn't be subject to change */


    /** Variables that track user status */
    var paused: Boolean = true
    var ready = false
    var connected = false

    /** Variables related to current video properties */
    var file: MediaFile? = null
    var currentVideoPosition: Double = 0.0

    /** A protocol instance is always defined and accompanied by a session **/
    var session = Session()

    /** Our JSON instance */
    val gson: Gson = GsonBuilder().create()

    /** Instantiating a socket to perform a TCP/IP connection later **/
    var socket: Socket = Socket()

    /** ============================ start of protocol =====================================**/
    fun connect() {
        syncplayBroadcaster?.onConnectionAttempt()
        sendPacket(sendHello(session.currentUsername, session.currentRoom, session.currentPassword))
    }


    fun sendPacket(json: String) {
        /** First, we add line separator to our JSON, then encode it to byte array **/
        val jsonEncoded = "$json\r\n".encodeToByteArray()

        /** Second of all, we execute our packet sending runnable through the responsible thread **/
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!connected) {
                    try {
                        socket = Socket(session.serverHost, session.serverPort)
                    } catch (e: Exception) {
                        delay(2000) //Safety-interval delay.
                        syncplayBroadcaster?.onConnectionFailed()
                        when (e) {
                            is UnknownHostException -> {
                                SyncplayUtils.loggy("SOCKET UnknownHostException")
                            }
                            is IOException -> {
                                SyncplayUtils.loggy("SOCKET IOException")
                            }
                            is SecurityException -> {
                                SyncplayUtils.loggy("SOCKET SecurityException")
                            }
                            else -> {
                                e.printStackTrace()
                            }
                        }
                    }
                    if (!socket.isClosed && socket.isConnected) {
                        syncplayBroadcaster?.onReconnected()
                        connected = true
                        readPacket()
                    }
                }
                socket.outputStream.write(jsonEncoded)
            } catch (s: SocketException) {
                s.printStackTrace()
                disconnected()
            }
        }
    }

    /** I believe there is no straight forward way to both check a socket's status and read lines at the
     * same time. So I will stick to this good ol' way which allows to never miss a line.
     */
    private fun readPacket() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    if (socket.isConnected && !socket.isClosed) {
                        try {
                            socket.getInputStream().bufferedReader(Charsets.UTF_8).forEachLine {
                                handleJson(it, this@SyncplayProtocol)
                            }
                        } catch (e: SocketException) {
                            disconnected()
                        }
                    }
                    delay(10)
                } catch (e: SocketException) {
                    disconnected()
                }
            }
        }
    }

    private fun disconnected() {
        connected = false
        syncplayBroadcaster?.onDisconnected()
    }

    /** Binding function for the callback interface between the the protocol and activity */
    open fun addBroadcaster(broadcaster: ProtocolBroadcaster) {
        this.syncplayBroadcaster = broadcaster
    }

    open fun removeBroadcaster() {
        this.syncplayBroadcaster = null
    }
}