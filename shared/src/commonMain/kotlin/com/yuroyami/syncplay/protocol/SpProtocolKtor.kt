package com.yuroyami.syncplay.protocol

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.network.sockets.isClosed
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SpProtocolKtor : SyncplayProtocol() {
    override val engine = NetworkEngine.KTOR

    private var socket: Socket? = null
    private var connection: Connection? = null
    private var input: ByteReadChannel? = null
    private var output: ByteWriteChannel? = null

    override fun connectSocket() {
        runBlocking {
            try {
                socket = aSocket(SelectorManager(Dispatchers.Default))
                    .tcp()
                    .connect(session.serverHost, session.serverPort) {
                        socketTimeout = 10000
                    }

                connection = socket!!.connection()

                input = connection?.input
                output = connection?.output

                protoScope.launch {
                    while (true) {
                        connection?.input?.awaitContent()
                        input?.readUTF8Line()?.let {
                            jsonHandler.parse(this@SpProtocolKtor, it)
                        }

                        delay(100)
                    }
                }

            } catch (e: Exception) {
                syncplayCallback?.onConnectionFailed()
            }
        }
    }

    override fun isSocketValid() = connection?.socket?.isClosed != true && connection?.socket != null

    override fun endConnection(terminating: Boolean) {
        try {
            /* Cleaning leftovers */
            socket?.close()

            if (terminating) {
                socket?.dispose()

                protoScope.cancel("")
            }
        } catch (_: Exception) {
        }
    }

    override fun writeActualString(s: String) {
        protoScope.launch {
            try {
                connection?.output?.writeStringUtf8(s)
                connection?.output?.flush()
            } catch (e: Exception) {
                syncplayCallback?.onDisconnected()
            }
        }
    }

    override fun supportsTLS() = false
    override fun upgradeTls() {
        //TODO("Opportunistic TLS not yet supported by Ktor")
    }

}
