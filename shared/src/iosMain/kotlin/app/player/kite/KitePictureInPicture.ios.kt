@file:OptIn(ExperimentalForeignApi::class)

package app.player.kite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import app.player.configurePlaybackAudioSession
import app.utils.loggy
import io.github.yuroyami.kiteplayer.KitePlayer
import io.github.yuroyami.kiteplayer.ffmpeg.KiteFFmpegVideoFrame
import io.github.yuroyami.kiteplayer.ffmpeg.corePixelBufferOrNull
import io.github.yuroyami.kiteplayer.ffmpeg.uploadPlanesOrNull
import io.github.yuroyami.kiteplayer.output.MetalPicture
import io.github.yuroyami.kiteplayer.output.MetalPictureResolver
import io.github.yuroyami.kiteplayer.output.SampleBufferVideoRenderer
import io.github.yuroyami.kiteplayer.view.KitePlayerPictureInPicture
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVSampleBufferDisplayLayer
import platform.AVKit.AVPictureInPictureController
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView
import kotlin.time.Duration.Companion.seconds

internal actual fun kitePictureInPicture(onActive: (Boolean) -> Unit): KitePictureInPicture? =
    if (AVPictureInPictureController.isPictureInPictureSupported()) IosKitePictureInPicture(onActive) else null

/** A black view whose only content is [sampleLayer], kept at the view's size. */
private class SampleBufferView(private val sampleLayer: AVSampleBufferDisplayLayer) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        setBackgroundColor(UIColor.blackColor)
        layer.addSublayer(sampleLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // No implicit animation: the picture follows a resize at once.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        sampleLayer.frame = bounds
        CATransaction.commit()
    }
}

/** How long a start waits for the system to allow the window. */
private val READY_TIMEOUT = 3.seconds

/**
 * The picture as the Metal renderer reads it: a VideoToolbox pixel buffer with no copy, or the
 * decoded planes with one copy each. The same mapping as KitePlayer's own iOS view.
 */
private val kiteResolver = MetalPictureResolver { frame ->
    val decoded = frame as? KiteFFmpegVideoFrame ?: return@MetalPictureResolver null
    decoded.corePixelBufferOrNull()?.let { MetalPicture.CorePixelBuffer(it) }
        ?: decoded.uploadPlanesOrNull()?.let { planes ->
            MetalPicture.SoftwarePlanes(
                width = planes.width,
                height = planes.height,
                format = planes.format,
                planes = planes.planes.map { MetalPicture.SoftwarePlanes.Plane(it.bytes, it.bytesPerRow, it.rows) },
            )
        }
}

private class IosKitePictureInPicture(private val onActive: (Boolean) -> Unit) : KitePictureInPicture {

    override val drawing = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var window: KitePlayerPictureInPicture? = null
    private var session: Job? = null

    override fun start() {
        if (drawing.value) {
            window?.let { if (!it.isActive && it.isPossible) it.start() }
            return
        }
        configurePlaybackAudioSession()
        drawing.value = true
    }

    override fun stop() {
        val open = window
        if (open != null && open.isActive) open.stop() else finish()
    }

    override fun close() {
        finish()
        scope.cancel()
    }

    @Composable
    override fun Surface(player: KitePlayer, modifier: Modifier) {
        val sampleLayer = remember { AVSampleBufferDisplayLayer().apply { videoGravity = AVLayerVideoGravityResizeAspect } }
        UIKitView(factory = { SampleBufferView(sampleLayer) }, modifier = modifier)
        DisposableEffect(player, sampleLayer) {
            val renderer = SampleBufferVideoRenderer(sampleLayer, kiteResolver)
            player.attachRenderer(renderer)
            val opened = KitePlayerPictureInPicture.createOrNull(player, sampleLayer)
            window = opened
            session = scope.launch { open(opened) }
            onDispose {
                session?.cancel()
                session = null
                player.detachRenderer(renderer)
                opened?.close()
                if (window === opened) window = null
            }
        }
    }

    /** Opens [opened] once the system allows it, and follows it until it closes. */
    private suspend fun open(opened: KitePlayerPictureInPicture?) {
        if (opened == null) {
            finish()
            return
        }
        opened.onRestoreRequested = { done -> done(true) }
        withTimeoutOrNull(READY_TIMEOUT) { while (!opened.isPossible) delay(100) }
        if (!opened.isPossible) {
            loggy("KitePlayer picture-in-picture is not possible right now")
            finish()
            return
        }
        opened.start()
        // A window that fails to open never reports itself active.
        if (withTimeoutOrNull(READY_TIMEOUT) { opened.active.first { it } } == null) {
            loggy("KitePlayer picture-in-picture did not open")
            finish()
            return
        }
        onActive(true)
        opened.active.first { !it }
        finish()
    }

    /** Puts the picture back in the app: the engine composes its usual renderer again. */
    private fun finish() {
        if (drawing.value) onActive(false)
        drawing.value = false
    }
}
