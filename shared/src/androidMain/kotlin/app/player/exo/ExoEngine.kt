package app.player.exo

import SyncplayMobile.shared.BuildConfig
import app.player.PlayerImpl
import app.player.PlayerEngine
import app.room.RoomViewmodel
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.exoplayer

/**
 * ExoPlayer - Google's official media player for Android.
 *
 * **Characteristics:**
 * - Most stable and reliable
 * - Limited codec/format support compared to MPV/VLC
 * - Excellent performance and battery efficiency
 * - Default for noLibs builds (pure Kotlin/Java, no native code)
 *
 * **Best for:** Users who prioritize stability and common formats (MP4, MKV)
 * **Terrible for:** Softcoded subtitles. mpv and VLC would be best in that case.
 */
object ExoEngine : PlayerEngine {
    override val name = "ExoPlayer"
    override val isDefault = BuildConfig.EXOPLAYER_ONLY
    override val isAvailable = true
    override val img = Res.drawable.exoplayer

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl = ExoImpl(viewmodel)
}