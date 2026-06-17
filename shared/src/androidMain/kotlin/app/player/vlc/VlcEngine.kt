package app.player.vlc

import SyncplayMobile.shared.BuildConfig
import app.player.PlayerImpl
import app.player.PlayerEngine
import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.vlc

/**
 * libVLC engine: widest format/codec coverage. Marked experimental.
 * Available only in the `full` flavor (absent when EXOPLAYER_ONLY).
 */
object VlcEngine : PlayerEngine {
    override val isAvailable: Boolean = !BuildConfig.EXOPLAYER_ONLY
    override val isDefault: Boolean = false
    override val isExperimental: Boolean = true
    override val name: String = "VLC"
    override val img: DrawableResource = Res.drawable.vlc
    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = VlcImpl(viewmodel)
}