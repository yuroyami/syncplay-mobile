package com.yuroyami.syncplay.managers.player

import com.yuroyami.syncplay.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource

interface PlayerEngine {
    val name: String
    val isDefault: Boolean
    val isAvailable: Boolean
    val img: DrawableResource

    fun instantiate(viewmodel: RoomViewmodel): BasePlayer
}