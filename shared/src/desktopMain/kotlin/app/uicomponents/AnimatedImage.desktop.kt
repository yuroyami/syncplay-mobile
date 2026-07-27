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
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image as SkiaImage

/**
 * Desktop animated image: Coil has no JVM GIF decoder (coil-gif is Android-only), so GIFs are
 * decoded with Skia's Codec directly — every frame pre-rendered sequentially (delta frames
 * accumulate onto the previous one via priorFrame) and cycled with each frame's own duration.
 * Mirrors the iOS actual, including its 64-entry LRU so Klipy panel scrolling stays smooth.
 */
@Composable
actual fun AnimatedImage(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
    alpha: Float,
) {
    var animation by remember(url) { mutableStateOf<DecodedAnimation?>(AnimatedImageCache.peek(url)) }

    if (animation == null) {
        LaunchedEffect(url) {
            animation = AnimatedImageCache.load(url)
        }
    }

    val anim = animation ?: return
    if (anim.frames.isEmpty()) return

    var frameIndex by remember(anim) { mutableIntStateOf(0) }

    if (anim.frames.size > 1) {
        LaunchedEffect(anim) {
            var i = 0
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

private class DecodedFrame(val bitmap: ImageBitmap, val durationMs: Long)

private class DecodedAnimation(val frames: List<DecodedFrame>)

private object AnimatedImageCache {

    private const val MAX_ENTRIES = 64

    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, DecodedAnimation>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DecodedAnimation>?) =
            size > MAX_ENTRIES
    }

    fun peek(url: String): DecodedAnimation? = synchronized(cache) { cache[url] }

    suspend fun load(url: String): DecodedAnimation? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[url] }?.let { return@withContext it }

        // In-flight dedup is intentionally skipped: the Klipy grid loads distinct URLs.
        runCatching {
            val bytes: ByteArray = httpClient.get(url).body()
            val decoded = decode(bytes)
            mutex.withLock { synchronized(cache) { cache[url] = decoded } }
            decoded
        }.getOrNull()
    }

    private fun decode(bytes: ByteArray): DecodedAnimation {
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        codec.use { c ->
            val info = c.imageInfo
            val work = Bitmap().apply { allocPixels(info) }
            val frames = ArrayList<DecodedFrame>(c.frameCount)

            if (c.frameCount <= 1) {
                c.readPixels(work, 0)
                frames.add(DecodedFrame(SkiaImage.makeFromBitmap(work).toComposeImageBitmap(), Long.MAX_VALUE))
            } else {
                val frameInfos = c.framesInfo
                for (i in 0 until c.frameCount) {
                    // priorFrame = the frame currently held in `work`, letting Skia compose
                    // delta-encoded GIF frames on top of the accumulated previous frame.
                    if (i == 0) c.readPixels(work, 0) else c.readPixels(work, i, i - 1)
                    val duration = frameInfos.getOrNull(i)?.duration?.takeIf { it > 0 } ?: 100
                    frames.add(
                        DecodedFrame(
                            bitmap = SkiaImage.makeFromBitmap(work).toComposeImageBitmap(),
                            durationMs = duration.coerceAtLeast(20).toLong(),
                        )
                    )
                }
            }
            return DecodedAnimation(frames)
        }
    }
}
