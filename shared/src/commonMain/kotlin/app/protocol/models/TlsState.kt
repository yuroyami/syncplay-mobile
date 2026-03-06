package app.protocol.models

/** The 3 TLS modes that the Netty client has to work with */
enum class TlsState {
    TLS_NO,
    TLS_ASK,
    TLS_YES;
}