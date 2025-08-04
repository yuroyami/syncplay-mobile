package com.yuroyami.syncplay.player

import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel
import org.jetbrains.compose.resources.DrawableResource

interface PlayerEngine {
    val name: String
    val isDefault: Boolean
    val isAvailable: Boolean
    val img: DrawableResource

    fun instantiate(viewmodel: SyncplayViewmodel): BasePlayer
}