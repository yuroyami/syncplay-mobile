package app.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.utils.loggy
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared CPU-frame → Compose pipeline for the desktop engines (VLCJ and libmpv both decode into
 * BGRA CPU buffers). Producer threads call [deliver]; the [FrameCanvas] composable reads
 * [frameTick] in its DRAW phase, so a new frame invalidates only the draw — no recomposition.
 */
class DesktopFramePipeline {

    val frameLock = Any()
    private var skiaBitmap: Bitmap? = null
    var composeImage: ImageBitmap? = null
        private set
    private var pixelBytes: ByteArray = ByteArray(0)
    var frameWidth = 0
        private set
    var frameHeight = 0
        private set

    /** Bumped once per delivered frame. */
    val frameTick = mutableLongStateOf(0L)

    /** Copies one BGRA frame into the reused Skia bitmap. [fill] writes frame bytes into the
     *  passed array (sized rowBytes*height); runs under [frameLock]. */
    fun deliver(width: Int, height: Int, rowBytes: Int, fill: (ByteArray) -> Unit) {
        synchronized(frameLock) {
            val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)

            if (skiaBitmap == null || skiaBitmap!!.width != width || skiaBitmap!!.height != height) {
                skiaBitmap = Bitmap().apply { allocPixels(info) }
                composeImage = skiaBitmap!!.asComposeImageBitmap()
                frameWidth = width
                frameHeight = height
            }

            val needed = rowBytes * height
            if (pixelBytes.size != needed) pixelBytes = ByteArray(needed)
            fill(pixelBytes)

            skiaBitmap!!.installPixels(info, pixelBytes, rowBytes)

            if (frameTick.longValue == 0L) loggy("DesktopFramePipeline: first video frame ${width}x${height}")
        }
        frameTick.longValue++
    }

    fun reset() {
        synchronized(frameLock) {
            skiaBitmap = null
            composeImage = null
            pixelBytes = ByteArray(0)
            frameWidth = 0
            frameHeight = 0
        }
    }
}

/** Letterbox/crop/stretch modes applied in draw math (the CPU buffer is always source-sized). */
enum class DesktopAspectMode(val label: String, val forcedRatio: Float?) {
    FIT("Fit", null),
    FILL("Fill (crop)", null),
    STRETCH("Stretch", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f),
}

@Composable
fun FrameCanvas(pipeline: DesktopFramePipeline, aspectMode: () -> DesktopAspectMode, modifier: Modifier) {
    Canvas(modifier.clipToBounds()) {
        @Suppress("UNUSED_EXPRESSION")
        pipeline.frameTick.longValue // draw-phase state read: new frame => redraw only

        val image: ImageBitmap
        val srcW: Int
        val srcH: Int
        synchronized(pipeline.frameLock) {
            image = pipeline.composeImage ?: return@Canvas
            srcW = pipeline.frameWidth
            srcH = pipeline.frameHeight
        }
        if (srcW <= 0 || srcH <= 0) return@Canvas

        val mode = aspectMode()
        val canvasW = size.width
        val canvasH = size.height
        val sourceRatio = mode.forcedRatio ?: (srcW.toFloat() / srcH.toFloat())

        val (dstW, dstH) = when (mode) {
            DesktopAspectMode.STRETCH -> canvasW to canvasH
            DesktopAspectMode.FILL -> {
                val scale = maxOf(canvasW / sourceRatio, canvasH)
                (scale * sourceRatio) to scale
            }
            else -> {
                val scale = min(canvasW / sourceRatio, canvasH)
                (scale * sourceRatio) to scale
            }
        }

        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(
                ((canvasW - dstW) / 2f).roundToInt(),
                ((canvasH - dstH) / 2f).roundToInt()
            ),
            dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt()),
        )
    }
}
