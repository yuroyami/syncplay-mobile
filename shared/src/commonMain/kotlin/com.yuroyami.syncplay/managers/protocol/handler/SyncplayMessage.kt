package com.yuroyami.syncplay.managers.protocol.handler

import kotlinx.serialization.Serializable

@Serializable
sealed interface SyncplayMessage {
    context(packetHandler: PacketHandler)
    suspend fun handle()
}