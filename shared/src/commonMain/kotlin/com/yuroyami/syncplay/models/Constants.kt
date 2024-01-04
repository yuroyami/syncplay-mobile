package com.yuroyami.syncplay.models

/* A couple of Enum-class constants that will regulate some of the things here and there */
object Constants {

    /** The 4 States of Ping - Needed to show the right ping icon */
    enum class PINGSTATE {
        PING_GREEN,
        PING_YELLOW,
        PING_RED,
        PING_GRAY
    }


    /** The 4 States of Connection - Needed for the protocol to function seamlessly */
    enum class CONNECTIONSTATE {
        STATE_DISCONNECTED,
        STATE_CONNECTING,
        STATE_CONNECTED,
        STATE_SCHEDULING_RECONNECT;
    }

    /** The 3 TLS modes that the Netty client has to work with */
    enum class TLS {
        TLS_NO,
        TLS_ASK,
        TLS_YES;
    }

    /** Pseudo-Popups (Basically just fragments appearing as popups) */
    enum class POPUP {
        POPUP_INROOM_SETTINGS,
        POPUP_SHARED_PLAYLIST,
        POPUP_MESSAGE_HISTORY;
    }

    /** Message types */
    enum class MsgTYPE {
        MSG_TYPE_CHAT,
        MSG_TYPE_SEEK,
        MSG_TYPE_PAUSEPLAY,
        MSG_TYPE_SERVER_ECHO,
        MSG_TYPE_LOCAL_ERROR;
    }
}