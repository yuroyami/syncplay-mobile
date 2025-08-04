package com.yuroyami.syncplay.player

import com.yuroyami.syncplay.player.avplayer.AvPlayer
import com.yuroyami.syncplay.player.vlc.VlcPlayer
import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel

sealed class ApplePlayerEngine: PlayerEngine {

    object AVPlayer: ApplePlayerEngine() {
        override val isAvailable: Boolean = true
        override val isDefault: Boolean = false
        override val name: String = "AVPlayer"

        override fun instantiate(viewmodel: SyncplayViewmodel): BasePlayer = AvPlayer(viewmodel)
    }

    object VLC: ApplePlayerEngine() {
        override val isAvailable: Boolean = true
        override val isDefault: Boolean = true
        override val name: String = "VLC"

        override fun instantiate(viewmodel: SyncplayViewmodel): BasePlayer = VlcPlayer(viewmodel)
    }

}