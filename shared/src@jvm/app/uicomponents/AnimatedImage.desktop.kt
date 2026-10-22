package app.uicomponents

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.utils.httpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Image as SkiaImage

/**
 * Desktop animated image. Coil has no JVM GIF decoder (coil-gif is Android-only), so Skia's Codec
 * decodes GIFs directly. Every frame is rendered ahead, in order: a delta frame draws on top of
 * the previous frame through priorFrame. Each frame is then scaled down to the tile size that
 * [AnimatedImageBudget] sets. Playback cycles the frames, each for its own duration. This mirrors
 * the iOS actual, including the LRU cache that keeps scrolling in the KLIPY GIF panel smooth.
 */
@Composable
actual fun AnimatedImage(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
    alpha: Float,
    onLoaded: (() -> Unit)?,
    onFailed: (() -> Unit)?,
) {
    var animation by remember(url) { mutableStateOf<DecodedAnimation?>(AnimatedImageCache.peek(url)) }
    LaunchedEffect(animation) { if (animation != null) onLoaded?.invoke() }

    if (animation == null) {
        LaunchedEffect(url) {
            animation = AnimatedImageCache.load(url)
            if (animation == null) onFailed?.invoke()
        }
    }

    val anim = animation ?: return
    if (anim.frames.isEmpty()) return

    var frameIndex by remember(anim) { mutableIntStateOf(0) }

    if (anim.frames.size > 1) {
        // Keyed on visibility too. Nobody sees a GIF at alpha 0, and the room keeps its panels
        // composed while the HUD is hidden, so without this key every GIF keeps animating.
        val visible = alpha > 0f
        LaunchedEffect(anim, visible) {
            if (!visible) return@LaunchedEffect
            var i = frameIndex
            while (isActive) {
                delay(anim.frames[i].durationMs)
                i = (i + 1) % anim.frames.size
                frameIndex = i
            }
        }
    }

    Image(
        bitmap = anim.frames[frameIndex].bitmap,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.alpha(alpha),
    )
}

internal class DecodedFrame(val bitmap: ImageBitmap, val durationMs: Long)

internal class DecodedAnimation(val frames: List<DecodedFrame>) {
    /** The memory of the decoded frames, at four bytes a pixel. */
    val bytes: Long = frames.sumOf { it.bitmap.width.toLong() * it.bitmap.height * 4 }
}

internal object AnimatedImageCache {

    private const val MAX_ENTRIES = 64
    private const val MAX_BYTES = 48L * 1024 * 1024

    private val cache = LinkedHashMap<String, DecodedAnimation>(16, 0.75f, true)

    fun peek(url: String): DecodedAnimation? = synchronized(cache) { cache[url] }

    private fun store(url: String, decoded: DecodedAnimation) = synchronized(cache) {
        cache[url] = decoded
        // Two bounds: a count for the panel's grid, and bytes so a few long GIFs cannot fill memory.
        var bytes = cache.values.sumOf { it.bytes }
        val oldestFirst = cache.keys.iterator()
        while ((cache.size > MAX_ENTRIES || bytes > MAX_BYTES) && cache.size > 1) {
            val oldest = oldestFirst.next()
            bytes -= cache.getValue(oldest).bytes
            oldestFirst.remove()
        }
    }

    suspend fun load(url: String): DecodedAnimation? {
        peek(url)?.let { return it }
        // No de-duplication of loads in progress, on purpose: the KLIPY grid loads distinct URLs.
        return AnimatedImageBudget.loads.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val bytes = httpClient.downloadAtMost(url, AnimatedImageBudget.MAX_DOWNLOAD_BYTES) ?: return@withContext null
                    decode(bytes)?.also { store(url, it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /** Decodes every frame at the size that [AnimatedImageBudget.decodeSize] allows, or returns null over a limit. */
    fun decode(bytes: ByteArray): DecodedAnimation? {
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        codec.use { c ->
            val info = c.imageInfo
            val size = AnimatedImageBudget.decodeSize(info.width, info.height, c.frameCount.coerceAtLeast(1)) ?: return null
            // One full-size frame at a time, which MAX_SOURCE_PIXELS bounds.
            val work = Bitmap().apply { allocPixels(info) }
            val frames = ArrayList<DecodedFrame>(c.frameCount)

            if (c.frameCount <= 1) {
                c.readPixels(work, 0)
                frames.add(DecodedFrame(work.toFrameImage(size.width, size.height), Long.MAX_VALUE))
            } else {
                val frameInfos = c.framesInfo
                for (i in 0 until c.frameCount) {
                    // priorFrame is the frame currently held in `work`, so Skia draws a
                    // delta-encoded GIF frame on top of the frames before it.
                    if (i == 0) c.readPixels(work, 0) else c.readPixels(work, i, i - 1)
                    val duration = frameInfos.getOrNull(i)?.duration?.takeIf { it > 0 } ?: 100
                    frames.add(
                        DecodedFrame(
                            bitmap = work.toFrameImage(size.width, size.height),
                            durationMs = duration.coerceAtLeast(20).toLong(),
                        )
                    )
                }
            }
            work.close()
            return DecodedAnimation(frames)
        }
    }

    /** A copy of the frame in [this], scaled to [width] by [height] when that is smaller. */
    private fun Bitmap.toFrameImage(width: Int, height: Int): ImageBitmap {
        if (width == this.width && height == this.height) return SkiaImage.makeFromBitmap(this).toComposeImageBitmap()
        val scaled = Bitmap().apply { check(allocPixels(ImageInfo.makeN32Premul(width, height))) { "No memory for a $width x $height frame" } }
        SkiaImage.makeFromBitmap(this).use { full ->
            Canvas(scaled).use { canvas ->
                canvas.drawImageRect(
                    full,
                    Rect.makeWH(this.width.toFloat(), this.height.toFloat()),
                    Rect.makeWH(width.toFloat(), height.toFloat()),
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
                    null,
                    true,
                )
            }
        }
        scaled.setImmutable()
        return scaled.use { SkiaImage.makeFromBitmap(it).toComposeImageBitmap() }
    }
}
