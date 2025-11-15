package com.yuroyami.syncplay.managers.player

import SyncplayMobile.shared.BuildConfig
import com.yuroyami.syncplay.managers.player.exo.ExoPlayer
import com.yuroyami.syncplay.managers.player.mpv.MpvPlayer
import com.yuroyami.syncplay.managers.player.vlc.VlcPlayer
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.exoplayer
import syncplaymobile.shared.generated.resources.mpv
import syncplaymobile.shared.generated.resources.vlc

/**
 * Sealed interface defining available media player engines for Android.
 *
 * Provides three player options with different trade-offs between stability,
 * format support, and performance. Availability depends on the build flavor:
 * - **noLibs**: Only ExoPlayer (no native libraries)
 * - **withLibs**: All three players (includes MPV and VLC native libraries)
 *
 * ## Player Characteristics
 * - **ExoPlayer**: Most stable, limited format support, good performance
 * - **MPV**: Most powerful, wide format support, mildly stable, default for withLibs builds
 * - **VLC**: Widest format support (QuickTime, Xvid), powerful but unstable
 */
@Suppress("KotlinConstantConditions")
sealed interface AndroidPlayerEngine: PlayerEngine {

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
    object Exoplayer: AndroidPlayerEngine {
        override val isAvailable: Boolean = true
        override val isDefault: Boolean = BuildConfig.EXOPLAYER_ONLY
        override val name: String = "Exoplayer"
        override val img: DrawableResource = Res.drawable.exoplayer

        override fun instantiate(viewmodel: RoomViewmodel): BasePlayer = ExoPlayer(viewmodel)
    }

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
    object Mpv: AndroidPlayerEngine {
        override val isAvailable: Boolean = !BuildConfig.EXOPLAYER_ONLY
        override val isDefault: Boolean = !BuildConfig.EXOPLAYER_ONLY
        override val name: String = "mpv"
        override val img: DrawableResource = Res.drawable.mpv

        override fun instantiate(viewmodel: RoomViewmodel): BasePlayer = MpvPlayer(viewmodel)
    }

    /**
     * VLC - Popular cross-platform media player with maximum format support.
     *
     * **Characteristics:**
     * - Widest format support including QuickTime, Xvid, and obscure codecs
     * - Very powerful but least stable (potential crashes/bugs)
     * - Large library size
     * - Requires native libraries
     *
     * **Best for:** Users who need to play uncommon/legacy formats despite stability risks
     *
     * **Availability:** Only in withLibs build flavor
     */
    object VLC: AndroidPlayerEngine {
        override val isAvailable: Boolean = !BuildConfig.EXOPLAYER_ONLY
        override val isDefault: Boolean = false
        override val name: String=  "VLC"
        override val img: DrawableResource = Res.drawable.vlc

        override fun instantiate(viewmodel: RoomViewmodel): BasePlayer = VlcPlayer(viewmodel)
    }
}