package com.yuroyami.syncplay.protocol

class SpProtocolApple /*: SyncplayProtocol()*/ {

    /** The implementation of the iOS network client is done in pure Swift
     * using SwiftNIO due to its extreme resemblance with Java's Netty.
     * It's also as stable and completely reliable, unlike Ktor, which
     * keeps causing crashes (I suspect it still doesn't know how to
     * do memory management yet, or perhaps I haven't implemented it
     * correctly). Let alone the fact that it doesn't even support startTLS,
     * which is required by Syncplay servers for establishing TLS connections.
     * I also dislike the "suspending lambda" nature of Ktor.
     *
     * However, if you really wanna take a look at the Ktor implementation,
     * it's commented out below. It's compilable and works, but as I said,
     * it's not stable. After using SwiftNIO, I removed all Ktor references.
     */
//    private var socket: Socket? = null
//    private var connection: Connection? = null
//    private var input: ByteReadChannel? = null
//    private var output: ByteWriteChannel? = null
//
//    override fun connectSocket() {
//        runBlocking {
//            try {
//                socket = aSocket(SelectorManager(Dispatchers.IO))
//                    .tcp()
//                    .connect(session.serverHost, session.serverPort) {
//                        socketTimeout = 10000
//                    }
//
//                connection = socket!!.connection()
//
//                input = connection?.input
//                output = connection?.output
//
//                protoScope.launch {
//                    while (true) {
//                        //connection?.input?.awaitContent()
//                        input?.readUTF8Line()?.let {
//                            launch {
//                                handleJson(this@SpProtocolApple, it)
//                            }
//                        }
//                        delay(100)
//                    }
//                }
//
//            } catch (e: Exception) {
//                syncplayCallback?.onConnectionFailed()
//            }
//        }
//    }
//
//    override fun isSocketValid() = connection?.socket?.isClosed != true && connection?.socket != null
//
//    override fun endConnection(terminating: Boolean) {
//        try {
//            /* Cleaning leftovers */
//            socket?.close()
//
//            if (terminating) {
//                socket?.dispose()
//
//                protoScope.cancel("")
//            }
//        } catch (_: Exception) {
//        }
//    }
//
//    override fun writeActualString(s: String) {
//        protoScope.launch {
//            try {
//                connection?.output?.writeStringUtf8(s)
//                connection?.output?.flush()
//            } catch(e: Exception) {
//                syncplayCallback?.onDisconnected()
//            }
//        }
//    }
//
//    override fun upgradeTls() {
//        //TODO("Opportunistic TLS not yet supported by Ktor")
//    }
}