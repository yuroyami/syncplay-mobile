package com.yuroyami.syncplay.logic.player

import com.yuroyami.syncplay.logic.SyncplayViewmodel
import org.jetbrains.compose.resources.DrawableResource

interface PlayerEngine {
    val name: String
    val isDefault: Boolean
    val isAvailable: Boolean
    val img: DrawableResource

    fun instantiate(viewmodel: SyncplayViewmodel): BasePlayer
}