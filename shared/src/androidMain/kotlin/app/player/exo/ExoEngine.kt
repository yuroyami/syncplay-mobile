package app.player.exo

import SyncplayMobile.shared.KiteBuildConfig
import app.player.PlayerImpl
import app.player.PlayerEngine
import app.room.RoomViewmodel
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.exoplayer

/**
 * ExoPlayer (Media3) engine. Stable and battery-efficient but with narrower codec/format
 * support than mpv, and weak softsub handling. Default engine on the `exoOnly` flavor
 * (`KiteBuildConfig.EXOPLAYER_ONLY`), which ships no native player libs.
 */
object ExoEngine : PlayerEngine {
    override val name = "ExoPlayer"
    override val isDefault = KiteBuildConfig.EXOPLAYER_ONLY
    override val isSystem = true
    override val isAvailable = true
    override val img = Res.drawable.exoplayer

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = ExoImpl(viewmodel)
}