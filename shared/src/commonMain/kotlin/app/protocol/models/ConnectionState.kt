package app.protocol.models

/** The 4 States of Connection - Needed for the protocol to function seamlessly */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SCHEDULING_RECONNECT;
}