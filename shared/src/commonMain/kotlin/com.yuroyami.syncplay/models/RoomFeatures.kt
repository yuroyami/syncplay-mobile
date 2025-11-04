package com.yuroyami.syncplay.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* The default values here are as per the official server, but they don't matter since they're gonna
* be overwritten on Hello handshake anyway */
@Serializable
class RoomFeatures(
    val isolateRooms: Boolean = true,
    @SerialName("readiness") val supportsReadiness: Boolean = true,
    @SerialName("managedRooms") val supportsManagedRooms: Boolean = true,
    val persistentRooms: Boolean = true,
    @SerialName("chat") val supportsChat: Boolean = true,
    val maxChatMessageLength: Int = 150,
    val maxUsernameLength: Int = 16,
    val maxRoomNameLength: Int = 35,
    val maxFilenameLength: Int = 250
)