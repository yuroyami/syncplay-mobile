package app.protocol

/**
 * Side-agnostic handler for messages travelling from server to client.
 *
 * The Syncplay client side implements this to react to each variant of [ServerMessage].
 * Dispatch is via [ServerMessage.dispatch].
 */
interface ServerMessageHandler {
    suspend fun onHello(message: ServerMessage.Hello)
    suspend fun onState(message: ServerMessage.State)
    suspend fun onSet(message: ServerMessage.Set)
    suspend fun onList(message: ServerMessage.List)
    suspend fun onChat(message: ServerMessage.Chat)
    suspend fun onTLS(message: ServerMessage.TLS)
    suspend fun onError(message: ServerMessage.Error)
}
