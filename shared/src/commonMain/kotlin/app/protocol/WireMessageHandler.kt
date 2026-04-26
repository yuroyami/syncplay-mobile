package app.protocol

/**
 * Side-agnostic visitor target for any [WireMessage]. Both ends of the protocol implement
 * this — the client's room handler and the server's per-connection handler — and each
 * overrides only the variants that travel toward it.
 *
 * Every method has a no-op default so an implementation that receives an unexpected
 * variant simply ignores it. In practice:
 *
 *  - The client overrides [onHello], [onState], [onSet], [onTLS], [onError],
 *    [onListResponse], [onChatBroadcast].
 *  - The server overrides [onHello], [onState], [onSet], [onTLS], [onError],
 *    [onListRequest], [onChatRequest].
 *
 * Dispatch is via [WireMessage.dispatch].
 */
interface WireMessageHandler {
    suspend fun onHello(message: WireMessage.Hello) = Unit
    suspend fun onState(message: WireMessage.State) = Unit
    suspend fun onSet(message: WireMessage.Set) = Unit
    suspend fun onTLS(message: WireMessage.TLS) = Unit
    suspend fun onError(message: WireMessage.Error) = Unit

    /** Empty `{"List": null}` — the client is asking for a user listing. Server-side. */
    suspend fun onListRequest(message: WireMessage.ListRequest) = Unit
    /** Server's full user/room listing reply. Client-side. */
    suspend fun onListResponse(message: WireMessage.ListResponse) = Unit

    /** Bare-string client chat: `{"Chat": "msg"}`. Server-side. */
    suspend fun onChatRequest(message: WireMessage.ChatRequest) = Unit
    /** Server-broadcast chat object: `{"Chat": {"username", "message"}}`. Client-side. */
    suspend fun onChatBroadcast(message: WireMessage.ChatBroadcast) = Unit
}
