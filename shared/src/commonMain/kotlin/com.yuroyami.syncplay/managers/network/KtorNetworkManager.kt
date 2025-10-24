package com.yuroyami.syncplay.managers.network

import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.SyncplayViewmodel
import com.yuroyami.syncplay.managers.NetworkManager
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KtorNetworkManager(viewmodel: SyncplayViewmodel) : NetworkManager(viewmodel) {
    override val engine = NetworkEngine.KTOR

    private var socket: Socket? = null
    private var connection: Connection? = null
    private var input: ByteReadChannel? = null
    private var output: ByteWriteChannel? = null

    override suspend fun connectSocket() {
        withContext(Dispatchers.IO) {
            try {
                socket = aSocket(SelectorManager(Dispatchers.IO))
                    .tcp()
                    .connect(
                        hostname = viewmodel.sessionManager.session.serverHost,
                        port = viewmodel.sessionManager.session.serverPort
                    ) {
                        socketTimeout = 10000
                    }

                connection = socket!!.connection()

                input = connection?.input
                output = connection?.output

                viewmodel.viewModelScope.launch {
                    while (true) {
                        connection?.input?.awaitContent()
                        input?.readUTF8Line()?.let {
                            handlePacket(it)
                        }

                        delay(100)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                viewmodel.callbackManager.onConnectionFailed()
            }
        }
    }

    override fun terminateExistingConnection() {
        runCatching {
            socket?.close()
        }
    }

    override suspend fun writeActualString(s: String) {
        try {
            connection?.output?.writeStringUtf8(s)
            connection?.output?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
            viewmodel.callbackManager.onDisconnected()
        }
    }

    override fun supportsTLS() = false
    override fun upgradeTls() {
        //TODO("Opportunistic TLS not yet supported by Ktor")
    }
}