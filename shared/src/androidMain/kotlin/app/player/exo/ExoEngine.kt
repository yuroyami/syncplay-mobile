package app.player.exo

import SyncplayMobile.shared.BuildConfig
import app.player.PlayerImpl
import app.player.PlayerEngine
import app.room.RoomViewmodel
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.exoplayer

/**
 * ExoPlayer (Media3) engine. Stable and battery-efficient but with narrower codec/format
 * support than MPV/VLC, and weak softsub handling. Default engine on the `exoOnly` flavor
 * (`BuildConfig.EXOPLAYER_ONLY`), which ships no native player libs.
 */
object ExoEngine : PlayerEngine {
    override val name = "ExoPlayer"
    override val isDefault = BuildConfig.EXOPLAYER_ONLY
    override val isAvailable = true
    override val img = Res.drawable.exoplayer

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = ExoImpl(viewmodel)
}