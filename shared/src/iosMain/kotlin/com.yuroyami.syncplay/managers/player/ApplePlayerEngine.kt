package com.yuroyami.syncplay.managers.player

import com.yuroyami.syncplay.managers.player.avplayer.AvPlayer
import com.yuroyami.syncplay.managers.player.vlc.VlcPlayer
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.swift
import syncplaymobile.shared.generated.resources.vlc

sealed interface ApplePlayerEngine: PlayerEngine {

    object AVPlayer: ApplePlayerEngine {
        override val isAvailable: Boolean = true
        override val isDefault: Boolean = false
        override val name: String = "AVPlayer"
        override val img: DrawableResource = Res.drawable.swift

        override fun instantiate(viewmodel: SyncplayViewmodel): BasePlayer = AvPlayer(viewmodel)
    }

    object VLC: ApplePlayerEngine {
        override val isAvailable: Boolean = true
        override val isDefault: Boolean = true
        override val name: String = "VLCKit" //We name this one VLCKit to differentiate it from Android's VLC when saving to datastore
        override val img: DrawableResource = Res.drawable.vlc

        override fun instantiate(viewmodel: SyncplayViewmodel): BasePlayer = VlcPlayer(viewmodel)
    }

}