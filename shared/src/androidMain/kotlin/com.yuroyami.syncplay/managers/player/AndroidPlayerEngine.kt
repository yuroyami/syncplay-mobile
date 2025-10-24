package com.yuroyami.syncplay.managers.player

import com.yuroyami.syncplay.BuildConfig
import com.yuroyami.syncplay.RoomViewmodel
import com.yuroyami.syncplay.managers.player.exo.ExoPlayer
import com.yuroyami.syncplay.managers.player.mpv.MpvPlayer
import com.yuroyami.syncplay.managers.player.vlc.VlcPlayer
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.exoplayer
import syncplaymobile.shared.generated.resources.mpv
import syncplaymobile.shared.generated.resources.vlc

@Suppress("KotlinConstantConditions")
sealed interface AndroidPlayerEngine: PlayerEngine {

    object Exoplayer: AndroidPlayerEngine {
        override val isAvailable: Boolean = true
        override val isDefault: Boolean = BuildConfig.FLAVOR == "noLibs"
        override val name: String = "Exoplayer"
        override val img: DrawableResource = Res.drawable.exoplayer

        override fun instantiate(viewmodel: RoomViewmodel): BasePlayer = ExoPlayer(viewmodel)
    }

    object Mpv: AndroidPlayerEngine {
        override val isAvailable: Boolean = BuildConfig.FLAVOR == "withLibs"
        override val isDefault: Boolean = BuildConfig.FLAVOR == "withLibs"
        override val name: String = "mpv"
        override val img: DrawableResource = Res.drawable.mpv

        override fun instantiate(viewmodel: RoomViewmodel): BasePlayer = MpvPlayer(viewmodel)
    }

    object VLC: AndroidPlayerEngine {
        override val isAvailable: Boolean = BuildConfig.FLAVOR == "withLibs"
        override val isDefault: Boolean = false
        override val name: String=  "VLC"
        override val img: DrawableResource = Res.drawable.vlc

        override fun instantiate(viewmodel: RoomViewmodel): BasePlayer = VlcPlayer(viewmodel)
    }
}