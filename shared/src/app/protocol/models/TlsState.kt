package app.protocol.models

/** TLS negotiation state for the network manager: not enabled, asked but unconfirmed, active. */
enum class TlsState {
    TLS_NO,
    TLS_ASK,
    TLS_YES;
}