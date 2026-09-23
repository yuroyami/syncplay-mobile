package app.player.mpv

import SyncplayMobile.shared.KiteBuildConfig
import app.player.PlayerEngine
import app.player.PlayerImpl
import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.mpv

/**
 * The mpv engine on Android, backed by libmpv from the libmpvKt library through [MpvImpl]. An
 * engine is one of the video players the app can drive.
 *
 * It is available, and the default engine, only in the `full` flavor (EXOPLAYER_ONLY is false).
 * `exoOnly` builds ship no native player libraries, so mpv is not available there.
 */
@Suppress("KotlinConstantConditions")
object MpvEngine: PlayerEngine {
    override val isAvailable: Boolean = !KiteBuildConfig.EXOPLAYER_ONLY
    override val isDefault: Boolean = !KiteBuildConfig.EXOPLAYER_ONLY
    override val name: String = "mpv"
    override val img: DrawableResource = Res.drawable.mpv

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = MpvImpl(viewmodel) }