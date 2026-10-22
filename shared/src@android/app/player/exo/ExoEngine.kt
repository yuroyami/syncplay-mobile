package app.player.exo

import SyncplayMobile.shared.KiteBuildConfig
import app.player.PlayerImpl
import app.player.PlayerEngine
import app.room.RoomViewmodel
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.exoplayer

/**
 * The ExoPlayer (Media3) engine. An engine is one of the video players the app can drive.
 * ExoPlayer is stable and battery-efficient, but it supports fewer codecs and formats than mpv
 * and handles soft subtitles poorly. It is the default engine in the `exoOnly` flavor
 * (`KiteBuildConfig.EXOPLAYER_ONLY`), which ships no native player libraries.
 */
object ExoEngine : PlayerEngine {
    override val name = "ExoPlayer"
    override val isDefault = KiteBuildConfig.EXOPLAYER_ONLY
    override val isSystem = true
    override val isAvailable = true
    override val img = Res.drawable.exoplayer

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = ExoImpl(viewmodel)
}