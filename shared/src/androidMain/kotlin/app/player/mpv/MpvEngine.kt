package app.player.mpv

import SyncplayMobile.shared.BuildConfig
import app.player.PlayerEngine
import app.player.PlayerImpl
import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.mpv

/**
 * MPV engine descriptor (Android). Backed by native libmpv via [MpvImpl]/[MPVView].
 *
 * Available and the default engine only in the `full` build flavor (when EXOPLAYER_ONLY is false);
 * absent in `exoOnly` builds, which ship no native player libraries.
 */
@Suppress("KotlinConstantConditions")
object MpvEngine: PlayerEngine {
    override val isAvailable: Boolean = !BuildConfig.EXOPLAYER_ONLY
    override val isDefault: Boolean = !BuildConfig.EXOPLAYER_ONLY
    override val name: String = "mpv"
    override val img: DrawableResource = Res.drawable.mpv

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = MpvImpl(viewmodel) }