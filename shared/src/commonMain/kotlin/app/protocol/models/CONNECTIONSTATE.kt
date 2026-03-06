package app.protocol.models

/** The 4 States of Connection - Needed for the protocol to function seamlessly */
enum class CONNECTIONSTATE {
    STATE_DISCONNECTED,
    STATE_CONNECTING,
    STATE_CONNECTED,
    STATE_SCHEDULING_RECONNECT;
}