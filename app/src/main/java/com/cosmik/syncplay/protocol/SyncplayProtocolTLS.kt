package com.cosmik.syncplay.protocol
//
//import android.util.Log
//import androidx.lifecycle.ViewModel
//import com.cosmik.syncplay.toolkit.SyncplayUtils
//import com.google.gson.Gson
//import com.google.gson.GsonBuilder
//import com.google.gson.JsonObject
//import kotlinx.coroutines.*
//import org.conscrypt.Conscrypt
//import org.json.JSONObject
//import java.io.BufferedInputStream
//import java.io.IOException
//import java.net.Socket
//import java.net.SocketException
//import java.net.UnknownHostException
//import java.security.Security
//import java.security.cert.X509Certificate
//import javax.net.ssl.*
//
//
//@OptIn(DelicateCoroutinesApi::class)
//open class SyncplayProtocolTLS : ViewModel() {
//
//    var paused: Boolean = true
//    private var serverIgnFly: Int = 0
//    private var clientIgnFly: Int = 0
//    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
//    private val basicgson: Gson = GsonBuilder().create()
//    var dummyfactory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
//    var sslsocket: SSLSocket = dummyfactory.createSocket() as SSLSocket
//
//    //ViewModel
//    var ready = false
//    var joinedRoom: Boolean = false
//    var currentVideoPosition: Double = 0.0
//    var currentVideoName: String = ""
//    var currentVideoLength: Double = 0.0
//    var currentVideoSize: Int = 0
//    var serverHost: String = "151.80.32.178"
//    var serverPort: Int = 8999
//    var connected = false
//    var currentUsername: String = "username_${(0..999999999999).random()}"
//    var currentRoom: String = "roomname"
//    var rewindThreshold = 12L
//
//    //User Properties: User Index // Readiness // File Name // File Duration // File Size
//    var userList: MutableMap<String, MutableList<String>> = mutableMapOf()
//    var messageSequence: MutableList<String> = mutableListOf()
//
//    private lateinit var syncplayBroadcaster: SyncplayBroadcaster
//
//    fun connect(host: String, port: Int, tls: Boolean) {
//        syncplayBroadcaster.onConnectionAttempt(port.toString())
//        sendPacket(sendHello(), host, port)
//    }
//
//    private fun sendHello(): String {
//        val hello: HashMap<String, Any> = hashMapOf()
//        hello["username"] = currentUsername
//        val room: HashMap<String, String> = hashMapOf()
//        room["name"] = currentRoom
//        hello["room"] = room
//        hello["version"] = "1.6.9"
//        val features: HashMap<String, Boolean> = hashMapOf()
//        features["sharedPlaylists"] = false
//        features["chat"] = true
//        features["featureList"] = true
//        features["readiness"] = true
//        features["managedRooms"] = true
//        hello["features"] = features
//
//        val wrapper: HashMap<String, HashMap<String, Any>> = hashMapOf()
//        wrapper["Hello"] = hello
//        return gson.toJson(wrapper)
//    }
//
//    private fun sendJoined(): String {
//        joinedRoom = true
//        val event: HashMap<String, Any> = hashMapOf()
//        event["joined"] = true
//        val room: HashMap<String, String> = hashMapOf()
//        room["name"] = currentRoom
//
//        val username: HashMap<String, Any> = hashMapOf()
//        username["room"] = room
//        username["event"] = event
//
//        val user: HashMap<String, Any> = hashMapOf()
//        user[currentUsername] = username
//
//        val wrapper: HashMap<String, HashMap<String, Any>> = hashMapOf()
//        wrapper["Set"] = user
//        return gson.toJson(wrapper)
//    }
//
//    fun sendReadiness(isReady: Boolean): String {
//        val ready: HashMap<String, Boolean> = hashMapOf()
//        ready["isReady"] = isReady
//        ready["manuallyInitiated"] = true
//
//        val setting: HashMap<String, Any> = hashMapOf()
//        setting["ready"] = ready
//
//        val wrapper: HashMap<String, Any> = hashMapOf()
//        wrapper["Set"] = setting
//
//        return gson.toJson(wrapper)
//    }
//
//    fun sendFile(): String {
//        val fileproperties: HashMap<String, Any> = hashMapOf()
//        fileproperties["duration"] = currentVideoLength
//        fileproperties["name"] = currentVideoName
//        fileproperties["size"] = currentVideoSize
//
//
//        val file: HashMap<String, Any> = hashMapOf()
//        file["file"] = fileproperties
//
//        val wrapper: HashMap<String, Any> = hashMapOf()
//        wrapper["Set"] = file
//
//        return basicgson.toJson(wrapper)
//    }
//
//    private fun sendEmptyList(): String {
//        val emptylist: HashMap<String, Any?> = hashMapOf()
//        emptylist["List"] = null
//
//        return GsonBuilder().serializeNulls().create().toJson(emptylist)
//    }
//
//
//    fun sendChat(message: String): String {
//        val wrapper: HashMap<String, Any> = hashMapOf()
//        wrapper["Chat"] = message
//
//        return gson.toJson(wrapper)
//    }
//
//    fun sendState(
//        servertime: Double?, clienttime: Double, doSeek: Boolean?, seekPosition: Long = 0,
//        iChangeState: Int, play: Boolean?
//    ): String {
//
//        val state: HashMap<String, Any?> = hashMapOf()
//        val playstate: HashMap<String, Any?> = hashMapOf()
//        if (doSeek == true) {
//            playstate["position"] = seekPosition.toDouble() / 1000.0
//        } else {
//            playstate["position"] = currentVideoPosition.toFloat()
//        }
//        playstate["paused"] = paused
//        playstate["doSeek"] = doSeek
//        val ping: HashMap<String, Any?> = hashMapOf()
//        if (servertime != null) {
//            ping["latencyCalculation"] = servertime
//        }
//        ping["clientLatencyCalculation"] = clienttime
//        ping["clientRtt"] = SyncplayUtils.pingIcmp("151.80.32.178", 32)
//
//        if (iChangeState == 1) {
//            val ignore: HashMap<String, Any?> = hashMapOf()
//            ignore["client"] = clientIgnFly
//            state["ignoringOnTheFly"] = ignore
//            playstate["paused"] = !play!!
//
//        } else {
//            if (serverIgnFly != 0) {
//                val ignore: HashMap<String, Any?> = hashMapOf()
//                ignore["server"] = serverIgnFly
//                state["ignoringOnTheFly"] = ignore
//                serverIgnFly = 0
//            }
//        }
//
//        state["playstate"] = playstate
//        state["ping"] = ping
//
//        val statewrapper: HashMap<String, Any?> = hashMapOf()
//        statewrapper["State"] = state
//
//        return gson.toJson(statewrapper)
//
//    }
//
//    fun sendPacket(json: String, host: String, port: Int) {
//        val jsonEncoded = "$json\r\n".encodeToByteArray()
//        GlobalScope.launch(Dispatchers.IO) {
//            try {
//                if (!useTLS) {
//                    if (!connected) {
//                        try {
//                            socket = Socket(host, port)
//                        } catch (e: Exception) {
//                            delay(2000) //Safety-interval delay.
//                            syncplayBroadcaster.onConnectionFailed()
//                            when (e) {
//                                is UnknownHostException -> {
//                                    Log.e("SOCKET", "UnknownHostException")
//                                }
//                                is IOException -> {
//                                    Log.e("SOCKET", "IOException")
//
//                                }
//                                is SecurityException -> {
//                                    Log.e("SOCKET", "SecurityException")
//                                }
//                                else -> {
//                                    e.printStackTrace()
//                                }
//                            }
//                        }
//
//                        if (!socket.isClosed && socket.isConnected) {
//                            syncplayBroadcaster.onReconnected()
//                            connected = true
//                            readPacket(host, port)
//                        }
//                    }
//                    val dOut = socket.outputStream
//                    dOut.write(jsonEncoded)
//                } else {
//                    if (!connected) {
//                        try {
//
//                            Security.setProperty("crypto.policy", "limited")
//                            System.setProperty("javax.net.debug", "ssl:handshake")
//                            System.setProperty(
//                                "jdk.tls.namedGroups",
//                                "secp256r1, secp384r1, secp521r1, secp160k1"
//                            )
//                            System.setProperty("javax.net.debug", "ssl:handshake")
//                            System.setProperty(
//                                "jdk.tls.client.enableStatusRequestExtension",
//                                "false"
//                            )
//                            System.setProperty("jsse.enableFFDHEExtension", "false")
//                            System.setProperty("jdk.tls.client.protocols", "TLSv1.1,TLSv1.2")
//                            Security.addProvider(Conscrypt.newProvider())
//                            Security.insertProviderAt(Conscrypt.newProvider(), 0)
//
//                            val sslContext: SSLContext =
//                                SSLContext.getInstance("TLSv1.3", Conscrypt.newProvider())
//
//                            sslContext.init(null, arrayOf<TrustManager>(object : X509TrustManager {
//                                override fun checkClientTrusted(
//                                    x509Certificates: Array<X509Certificate>,
//                                    s: String
//                                ) {
//                                    println("Skip trust check for: " + x509Certificates[0])
//                                }
//
//                                override fun checkServerTrusted(
//                                    x509Certificates: Array<X509Certificate>,
//                                    s: String
//                                ) {
//                                    println("Skip trust check for: " + x509Certificates[0])
//                                }
//
//                                override fun getAcceptedIssuers(): Array<X509Certificate?> {
//                                    return arrayOfNulls(0)
//                                }
//                            }), null)
//
//                            sslsocket =
//                                sslContext.socketFactory.createSocket(host, port) as SSLSocket
//                            sslsocket.useClientMode = true
//                            sslsocket.addHandshakeCompletedListener { evt ->
//                                println("Handshake completed: ${evt.session.protocol} - ${evt.session.cipherSuite}, ${evt.socket}")
//                            }
//
//                            sslsocket.startHandshake()
//                            if (sslsocket.isConnected) {
//                                syncplayBroadcaster.onReconnected()
//                            } else {
//                                syncplayBroadcaster.onConnectionFailed()
//                            }
//                        } catch (e: Exception) {
//                            e.printStackTrace()
//                        }
//                    }
//                    val dOut = sslsocket.outputStream
//                    dOut.write(jsonEncoded)
//                }
//            } catch (s: SocketException) {
//                Log.e("Socket", "${s.stackTrace}")
//            }
//        }
//    }
//
//    private fun readPacket(host: String, port: Int) {
//        GlobalScope.launch(Dispatchers.IO) {
//            while (true) {
//                if (!useTLS) {
//                    if (socket.isConnected) {
//                        socket.getInputStream().also { input ->
//                            if (!socket.isInputShutdown) {
//                                try {
//                                    BufferedInputStream(input).reader(Charsets.UTF_8)
//                                        .forEachLine { json ->
//                                            extractJson(json, host, port)
//                                        }
//                                } catch (s: SocketException) {
//                                    connected = false
//                                    syncplayBroadcaster.onDisconnected()
//                                    s.printStackTrace()
//                                }
//                            } else {
//                                Log.e("Server:", "STREAMED SHUTDOWN")
//                            }
//                        }
//                    } else {
//                        connected = false
//                    }
//                } else {
//                    if (sslsocket.isConnected) {
//                        sslsocket.inputStream.also { input ->
//                            if (!sslsocket.isInputShutdown) {
//                                try {
//                                    BufferedInputStream(input).reader(Charsets.UTF_8)
//                                        .forEachLine { json ->
//                                            extractJson(json, host, port)
//                                        }
//                                } catch (s: SocketException) {
//                                    connected = false
//                                    s.printStackTrace()
//                                }
//                            } else {
//                                Log.e("Server:", "STREAMED SHUTDOWN")
//                            }
//                        }
//                    } else {
//                        connected = false
//                    }
//                }
//                delay(300)
//            }
//        }
//    }
//
//    private fun extractJson(json: String, host: String, port: Int) {
//        connected = true
//        Log.e("Server:", json)
//        val jsoner = gson.fromJson(json, JsonObject::class.java)
//        val jsonHeader = jsoner.keySet().toList()[0]
//        /********************
//         * Handling Hellos *
//         *******************/
//        if (jsonHeader == "Hello") {
//            val trueusername = jsoner
//                .getAsJsonObject("Hello")
//                .getAsJsonPrimitive("username").asString
//            currentUsername = trueusername
//            sendPacket(sendJoined(), host, port)
//            sendPacket(sendEmptyList(), host, port)
//            sendPacket(
//                sendReadiness(ready),
//                host,
//                port
//            )
//            connected = true
//            syncplayBroadcaster.onJoined()
//            //periodicNetworkCheckup(associatedViewModel.serverHost, associatedViewModel.serverPort)
//        }
//        /********************
//         * Handling Lists *
//         *******************/
//        if (jsonHeader == "List") {
//            val userlist =
//                jsoner.getAsJsonObject("List").getAsJsonObject(currentRoom)
//            val userkeys = userlist.keySet()
//            var indexer = 1
//            val tempUserList: MutableMap<String, MutableList<String>> = mutableMapOf()
//            for (user in userkeys) {
//                val userProperties: MutableList<String> = mutableListOf()
//                var userindex = 0
//                if (user != currentUsername) {
//                    userindex = indexer
//                    indexer += 1
//                }
//                val readiness = if (userlist.getAsJsonObject(user).get("isReady").isJsonNull) {
//                    "null"
//                } else {
//                    userlist.getAsJsonObject(user).getAsJsonPrimitive("isReady").asString
//                }
//
//                val file = userlist
//                    .getAsJsonObject(user)
//                    .getAsJsonObject("file")
//
//                var filename = ""
//                var fileduration = ""
//                var filesize = ""
//
//                if (file.keySet().contains("name")) {
//                    filename = file.getAsJsonPrimitive("name").toString()
//                    fileduration = file.getAsJsonPrimitive("duration").toString()
//                    filesize = file.getAsJsonPrimitive("size").toString()
//                }
//
//                if (userList.keys.contains(user)) {
//                    if (userList[user]?.get(2) != filename) {
//                        syncplayBroadcaster.onSomeoneLoadedFile(
//                            user,
//                            filename,
//                            fileduration,
//                            filesize
//                        )
//                    }
//                }
//
//                userProperties.add(0, userindex.toString())
//                userProperties.add(1, readiness)
//                userProperties.add(2, filename)
//                userProperties.add(3, fileduration)
//                userProperties.add(4, filesize)
//
//                tempUserList[user] = userProperties
//            }
//
//            userList = tempUserList
//            syncplayBroadcaster.onReceivedList()
//        }
//        /********************
//         * Handling Sets *
//         *******************/
//        if (jsonHeader == "Set") {
//            if (json.contains("event", true)) {
//                val getuser = jsoner.getAsJsonObject("Set")
//                    .getAsJsonObject("user").keySet().toList()
//                val user = getuser[0]
//                if (json.contains("\"left\": true")) {
//                    syncplayBroadcaster.onSomeoneLeft(user)
//                }
//                if (json.contains("\"joined\": true")) {
//                    syncplayBroadcaster.onSomeoneJoined(user)
//                }
//            }
//
//            sendPacket(sendEmptyList(), host, port)
//        }
//        /********************
//         * Handling Chats *
//         *******************/
//        if (jsonHeader == "Chat") {
//            val chatter = JSONObject(JSONObject(json).get("Chat").toString()).optString("username")
//            val message = JSONObject(JSONObject(json).get("Chat").toString()).optString("message")
//            syncplayBroadcaster.onChatReceived(chatter, message)
//        }
//        /********************
//         * Handling States *
//         *******************/
//        if (jsonHeader == "State") {
//            val latency = JSONObject(
//                JSONObject(JSONObject(json).get("State").toString()).get("ping").toString()
//            ).optDouble("latencyCalculation")
//            if (json.contains("ignoringonthefly", true)) {
//                if ((json.contains("server\":"))) {
//                    val doSeek: Boolean? = JSONObject(
//                        JSONObject(
//                            JSONObject(json).get("State").toString()
//                        ).get("playstate").toString()
//                    ).optString("doSeek").toBooleanStrictOrNull()
//                    val seekedPosition: Double = JSONObject(
//                        JSONObject(
//                            JSONObject(json).get("State").toString()
//                        ).get("playstate").toString()
//                    ).optDouble("position")
//                    val seeker: String = JSONObject(
//                        JSONObject(
//                            JSONObject(json).get("State").toString()
//                        ).get("playstate").toString()
//                    ).optString("setBy")
//                    if (doSeek == true) {
//                        syncplayBroadcaster.onSomeoneSeeked(
//                            seeker,
//                            seekedPosition
//                        )
//                    }
//                    if ((doSeek == false) || (doSeek == null)) {
//                        paused =
//                            json.contains("\"paused\": true", true)
//                        if (!paused) {
//                            syncplayBroadcaster.onSomeonePlayed(
//                                seeker
//                            )
//                        } else {
//                            syncplayBroadcaster.onSomeonePaused(
//                                seeker
//                            )
//                        }
//                    }
//                    serverIgnFly = JSONObject(
//                        JSONObject(
//                            JSONObject(json).get("State").toString()
//                        ).get("ignoringOnTheFly").toString()
//                    ).optInt("server")
//                    val clienttime =
//                        (System.currentTimeMillis() / 1000.0)
//
//                    if (!(json.contains("client\":"))) {
//                        clientIgnFly = 0
//                        sendPacket(
//                            sendState(
//                                latency,
//                                clienttime,
//                                null, 0,
//                                iChangeState = 0,
//                                play = null
//                            ),
//                            host,
//                            port
//                        )
//                    } else {
//                        clientIgnFly = 0
//                        sendPacket(
//                            sendState(
//                                latency,
//                                clienttime,
//                                doSeek, 0,
//                                0,
//                                null
//                            ),
//                            host,
//                            port
//                        )
//                    }
//                }
//            } else {
//                val seekedPosition: Double = JSONObject(
//                    JSONObject(JSONObject(json).get("State").toString()).get("playstate").toString()
//                ).optDouble("position")
//                val seeker: String = JSONObject(
//                    JSONObject(JSONObject(json).get("State").toString()).get("playstate").toString()
//                ).optString("setBy")
//
//                //Rewind check if someone is behind
//                val threshold = rewindThreshold
//                if (seeker != (currentUsername)) {
//                    if (seekedPosition < (currentVideoPosition - threshold)) {
//                        syncplayBroadcaster.onSomeoneBehind(seeker, seekedPosition)
//                    }
//                }
//                //Constant Traditional Pinging if no command is received.
//                val clienttime =
//                    (System.currentTimeMillis() / 1000.0)
//                sendPacket(
//                    sendState(latency, clienttime, false, 0, 0, null),
//                    host,
//                    port
//                )
//            }
//        }
//    }
//
//    // Fragment will call this to link its listener with this class's listener
//    //Anything called to this class's broadcaster will be overridden in the fragment's implemented broadcaster.
//    open fun addBroadcaster(broadcaster: SyncplayBroadcaster) {
//        this.syncplayBroadcaster = broadcaster
//    }
//}