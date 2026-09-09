package app.protocol.network

import app.protocol.models.ConnectionState
import app.utils.loggy
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event

/**
 * The Syncplay protocol over a WebSocket, because a browser tab cannot open a TCP socket.
 *
 * The framing is untouched: one JSON object per line, CRLF-terminated, exactly what every other
 * transport writes. Only the pipe differs. A WebSocket delivers whole messages rather than a byte
 * stream, but a peer is free to pack several lines into one message, so inbound text is split on
 * newlines and each piece handed over separately.
 *
 * **What this can talk to.** Nothing today. `syncplay.pl` speaks TCP and has no WebSocket
 * endpoint, so a web client reaches a Syncplay server one of two ways: a bridge that translates
 * WebSocket to TCP, or a WebSocket listener added beside the TCP one in this app's own built-in
 * server, which is the better answer because it needs no hosted infrastructure.
 *
 * **Encryption.** Not this class's to negotiate. A page served over https must use `wss`, and the
 * browser does the TLS itself before a single byte of Syncplay protocol moves; a page served over
 * plain http gets `ws`. That is why [supportsTLS] is false and [upgradeTls] does nothing: there is
 * no opportunistic upgrade to perform, the transport is already whatever the page's scheme forced.
 */
class WebSocketNetworkManager(viewmodel: app.room.RoomViewmodel) : NetworkManager(viewmodel) {

    override val engine = NetworkEngine.WEBSOCKET

    private var socket: WebSocket? = null

    /** Anything written before the socket finished opening, in the order it was written. */
    private val backlog = mutableListOf<String>()

    override suspend fun connectSocket() {
        terminateExistingConnection()

        val secure = runCatching { window.location.protocol == "https:" }.getOrDefault(false)
        val scheme = if (secure) "wss" else "ws"
        val url = "$scheme://${viewmodel.session.serverHost}:${viewmodel.session.serverPort}"

        val opened = CompletableDeferred<Unit>()
        val ws = WebSocket(url)

        ws.onopen = { _: Event ->
            // Whatever the protocol layer wrote while the handshake was still in flight.
            backlog.forEach { line -> runCatching { ws.send(line) } }
            backlog.clear()
            opened.complete(Unit)
        }
        ws.onmessage = { event ->
            val payload = event.asDynamicText()
            if (payload != null) {
                // One message may carry more than one protocol line.
                payload.split("\n").forEach { line ->
                    val trimmed = line.trim('\r', '\n', ' ')
                    if (trimmed.isNotEmpty()) handlePacket(trimmed)
                }
            }
        }
        ws.onerror = { _: Event ->
            if (!opened.isCompleted) opened.completeExceptionally(SocketGoneException())
        }
        ws.onclose = { _: Event ->
            if (!opened.isCompleted) opened.completeExceptionally(SocketGoneException())
            lost(ws)
        }

        socket = ws
        opened.await()
    }

    /**
     * A socket closed under us: a refused dial or a dropped session, depending on where we were.
     * Branching matters, a disconnection reported for a handshake that never landed tells the
     * room it is reconnecting to something it never reached.
     */
    private fun lost(ws: WebSocket) {
        if (socket !== ws) return
        socket = null
        backlog.clear()
        when (state.value) {
            ConnectionState.CONNECTING -> viewmodel.callback.onConnectionFailed()
            ConnectionState.CONNECTED -> viewmodel.callback.onDisconnected()
            else -> Unit
        }
    }

    /**
     * False on purpose. The page's own scheme already decided this, before any Syncplay message
     * was exchanged, so there is no in-band upgrade for the protocol to ask for.
     */
    override fun supportsTLS(): Boolean = false

    override suspend fun upgradeTls() {
        loggy("A TLS upgrade was requested on a WebSocket, where the page's scheme has already settled it.")
    }

    override fun terminateExistingConnection() {
        val ws = socket ?: return
        socket = null
        backlog.clear()
        runCatching {
            ws.onopen = null
            ws.onmessage = null
            ws.onerror = null
            ws.onclose = null
            ws.close()
        }
    }

    override suspend fun writeActualString(s: String) {
        val ws = socket ?: throw SocketGoneException()
        when (ws.readyState) {
            WebSocket.CONNECTING -> backlog += s
            WebSocket.OPEN -> ws.send(s)
            else -> throw SocketGoneException()
        }
    }
}

/** A WebSocket frame is text or binary; this transport only ever sends and expects text. */
private fun org.w3c.dom.MessageEvent.asDynamicText(): String? = runCatching {
    jsMessageText(this)
}.getOrNull()?.takeIf { it.isNotEmpty() }

private fun jsMessageText(event: org.w3c.dom.MessageEvent): String =
    js("(typeof event.data === 'string' ? event.data : '')")
