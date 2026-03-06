package app.player.mpv

import SyncplayMobile.shared.BuildConfig
import app.player.PlayerEngine
import app.player.PlayerImpl
import app.room.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.mpv

/**
 * MPV - Powerful open-source media player with extensive format support.
 *
 * **Characteristics:**
 * - Most powerful player with advanced features
 * - Supports most video/audio codecs and containers
 * - Mildly stable (occasional issues with edge cases)
 * - Default for withLibs builds
 * - Requires native libraries
 *
 * **Best for:** Users who need broad format support and advanced features
 *
 * **Availability:** Only in withLibs build flavor
 */
@Suppress("KotlinConstantConditions")
object MpvEngine: PlayerEngine {
    override val isAvailable: Boolean = !BuildConfig.EXOPLAYER_ONLY
    override val isDefault: Boolean = !BuildConfig.EXOPLAYER_ONLY
    override val name: String = "mpv"
    override val img: DrawableResource = Res.drawable.mpv

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = MpvImpl(viewmodel) }