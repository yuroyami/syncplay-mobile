package com.chromaticnoob.syncplayprotocol

import android.os.Handler
import android.os.HandlerThread
import androidx.lifecycle.ViewModel
import com.chromaticnoob.syncplay.room.Message
import com.chromaticnoob.syncplayprotocol.SPJsonHandler.handleJson
import com.chromaticnoob.syncplayprotocol.SPWrappers.sendHello
import com.chromaticnoob.syncplayutils.SyncplayUtils
import com.chromaticnoob.syncplayutils.SyncplayUtils.loggy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Socket
import java.net.SocketException
import java.net.UnknownHostException

open class SyncplayProtocol : ViewModel() {

    /** This refers to the event callback interface */
    var syncplayBroadcaster: SPBroadcaster? = null

    /** Protocol-exclusive variables **/
    var serverIgnFly: Int = 0
    var clientIgnFly: Int = 0
    var ping = 0.0

    /** Our JSON instance */
    val gson: Gson = GsonBuilder().create()

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
    var currentUsername: String = "Anonymous${(1000..9999).random()}"
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

    /** Instantiating the threads that will be responsible for IO socket operations **/
    private val iThread = HandlerThread("readerThread")
    private val oThread = HandlerThread("senderThread")

    /** ============================ start of protocol =====================================**/
    fun connect() {
        syncplayBroadcaster?.onConnectionAttempt()
        sendPacket(sendHello(currentUsername, currentRoom, currentPassword))
    }

    /** The packet sending is fairly simple, we create a socket if it's not connected, then
     * we try 'n' catch any exceptions that may happen during the creation of the socket, sending
     * data through the socket, etc... That's literally what takes space in this function. All that
     * matters in the function below is the socket creation and sending packets through the output
     * stream of that socket.
     */
    fun sendPacket(json: String) {
        /** First, we check the status of the thread responsible for output operations **/
        try {
            if (!oThread.isAlive) {
                oThread.start()
            }
            sendPacketCore(json)
        } catch (e: IllegalThreadStateException) {
            sendPacketCore(json)
        }

    }

    private fun sendPacketCore(json: String) {
        /** First, we add line separator to our JSON, then encode it to byte array **/
        val jsonEncoded = "$json\r\n".encodeToByteArray()

        /** Second of all, we execute our packet sending runnable through the responsible thread **/
        Handler(oThread.looper).post {
            try {
                if (!connected) {
                    try {
                        socket = Socket(serverHost, serverPort)
                    } catch (e: Exception) {
                        Thread.sleep(2000) //Safety-interval delay.
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
                loggy("Client: $json")
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
        GlobalScope.launch(Dispatchers.IO) {
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
    open fun addBroadcaster(broadcaster: SPBroadcaster) {
        this.syncplayBroadcaster = broadcaster
    }

    open fun removeBroadcaster() {
        this.syncplayBroadcaster = null
    }
}