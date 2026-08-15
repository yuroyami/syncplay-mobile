package app.player.kite

import app.player.PlayerEngine
import app.player.PlayerImpl
import app.room.RoomViewmodel
import io.github.yuroyami.kiteplayer.KitePlayerPlatform
import org.jetbrains.compose.resources.DrawableResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.kiteplayer

/**
 * KitePlayer: a media engine written in Kotlin Multiplatform from the ground up, decoding through
 * FFmpeg (KiteCodec) and rendering through the platform's own output.
 *
 * It is the only engine here whose implementation is shared: [KiteImpl] is written once and runs
 * unchanged on Android and iOS, because the engine itself is common code and only the audio
 * device, the video surface and the hardware decoder differ per platform. Hardware decode is
 * MediaCodec on Android and VideoToolbox on iOS, both inside FFmpeg, with a measured software
 * fallback rather than a silent failure.
 *
 * Experimental, and honestly so: KitePlayer has no public release, no qualification on physical
 * hardware, and its subtitle support today covers SubRip and WebVTT only. Styled ASS subtitles
 * are seen as tracks but not yet drawn.
 */
@Suppress("KotlinConstantConditions")
internal class KiteEngine(
    private val mediaResolver: KiteMediaResolver,
    override val name: String = "KitePlayer",
    private val presentation: KitePlayerPresentation = KiteInteropPresentation,
) : PlayerEngine {
    override val isDefault: Boolean = false

    /**
     * False in the `exoOnly` Android flavor, which ships no native player libraries: that build
     * strips `libkitecodec_jni.so` from its jniLibs, so offering the engine there would hand the
     * user a picker entry that cannot load. Always true on iOS, where the flag is never set.
     */
    override val isAvailable: Boolean
        get() = KitePlayerPlatform.isAvailable

    override val isExperimental: Boolean = true
    override val img: DrawableResource = Res.drawable.kiteplayer

    override fun createImpl(viewmodel: RoomViewmodel): PlayerImpl =
        KiteImpl(viewmodel, this, mediaResolver, presentation)
}
