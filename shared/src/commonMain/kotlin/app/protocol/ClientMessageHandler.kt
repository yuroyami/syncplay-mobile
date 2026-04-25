package app.protocol

/**
 * Side-agnostic handler for messages travelling from client to server.
 *
 * The Syncplay server side implements this — typically `ClientConnection` — to react to
 * each variant of [ClientMessage]. Dispatch is via [ClientMessage.dispatch].
 */
interface ClientMessageHandler {
    suspend fun onHello(message: ClientMessage.Hello)
    suspend fun onState(message: ClientMessage.State)
    suspend fun onSet(message: ClientMessage.Set)
    suspend fun onList(message: ClientMessage.List)
    suspend fun onChat(message: ClientMessage.Chat)
    suspend fun onTLS(message: ClientMessage.TLS)
    suspend fun onError(message: ClientMessage.Error)
}
