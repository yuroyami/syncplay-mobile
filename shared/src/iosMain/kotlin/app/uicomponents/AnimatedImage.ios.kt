package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import app.utils.httpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.utils.loggy
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFNumberRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFNumberDoubleType
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceGetCount
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImagePropertyAPNGDelayTime
import platform.ImageIO.kCGImagePropertyAPNGUnclampedDelayTime
import platform.ImageIO.kCGImagePropertyGIFDelayTime
import platform.ImageIO.kCGImagePropertyGIFDictionary
import platform.ImageIO.kCGImagePropertyGIFUnclampedDelayTime
import platform.ImageIO.kCGImagePropertyPNGDictionary
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/**
 * Process-wide LRU cache of decoded [UIImage]s keyed by URL. Survives Compose recomposition,
 * panel teardown, and even leaving the room — so reopening the GIF panel or toggling the HUD
 * doesn't have to redownload-and-redecode 24 GIFs every time. NSURLCache (configured in
 * PlatformUtils.ios.kt) handles the byte-level cache; this layer skips the CGImageSource
 * frame-extraction step too, which is the dominant cost for animated GIFs (≈100-300 ms per
 * GIF on a multi-frame source).
 *
 * Capped at 64 entries: a panel page is 24 tiles plus the loaded second/third pages, so 64
 * comfortably covers two pages worth and still bounds memory at ≤ ~30 MB worst case.
 * Eviction is plain LRU via [LinkedHashMap]'s access-order behavior — touching an entry on
 * read moves it to the end, oldest-untouched gets evicted on overflow. Synchronized lock
 * because the cache is read on the Compose Main dispatcher and written from
 * [downloadAndDecodeAnimatedImage] which finishes on Dispatchers.Default.
 */
private const val IMAGE_CACHE_MAX_ENTRIES = 64
private val imageCacheLock = SynchronizedObject()
/* LinkedHashMap on Kotlin/Native preserves insertion order only — no access-order
 * constructor like the JVM offers. Implement LRU by removing + re-inserting on read,
 * which moves the entry to the tail; eviction then picks the head (oldest-untouched). */
private val imageCache = LinkedHashMap<String, UIImage>()

private fun cachedImage(url: String): UIImage? = synchronized(imageCacheLock) {
    val hit = imageCache.remove(url) ?: return@synchronized null
    imageCache[url] = hit
    hit
}

private fun cacheImage(url: String, image: UIImage) {
    synchronized(imageCacheLock) {
        imageCache.remove(url)
        imageCache[url] = image
        while (imageCache.size > IMAGE_CACHE_MAX_ENTRIES) {
            val oldest = imageCache.keys.iterator().next()
            imageCache.remove(oldest)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AnimatedImage(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
    alpha: Float,
) {
    /* Seed from the cache synchronously during composition so cache hits paint on the very
     * first frame — no flicker, no LaunchedEffect await. Cache misses fall through to the
     * effect below which downloads + decodes + populates the cache. */
    var nativeImage by remember(url) { mutableStateOf<UIImage?>(cachedImage(url)) }

    LaunchedEffect(url) {
        if (nativeImage != null) return@LaunchedEffect
        nativeImage = downloadAndDecodeAnimatedImage(url)?.also { cacheImage(url, it) }
    }

    val contentMode = when (contentScale) {
        ContentScale.Crop -> UIViewContentMode.UIViewContentModeScaleAspectFill
        ContentScale.FillBounds -> UIViewContentMode.UIViewContentModeScaleToFill
        else -> UIViewContentMode.UIViewContentModeScaleAspectFit
    }

    UIKitView(
        factory = {
            UIImageView().apply {
                this.contentMode = contentMode
                this.clipsToBounds = true
                this.userInteractionEnabled = false
            }
        },
        update = { imageView ->
            imageView.contentMode = contentMode
            imageView.image = nativeImage
            /* Native alpha — Compose's `Modifier.alpha` does not propagate into UIKit interop
             * layers. The underlying UIImageView keeps drawing at full opacity unless we set
             * its alpha here, which is what fades the actual pixels along with the parent HUD. */
            imageView.alpha = alpha.toDouble()
        },
        properties = UIKitInteropProperties(
            isInteractive = false,
            isNativeAccessibilityEnabled = false,
        ),
        modifier = modifier,
    )
}

/**
 * Downloads image data using the shared Ktor client and decodes it as animated GIF/WebP.
 * Decoding runs on `Dispatchers.Default` because [decodeAnimatedImage] does CGImageSource
 * frame-by-frame extraction synchronously — running it on the LaunchedEffect's default
 * (Compose Main) dispatcher means a 24-tile grid stalls the UI thread for hundreds of ms
 * per tile. Cancellation is rethrown so a closed/recomposed panel can correctly cancel
 * the in-flight download instead of being silently swallowed by `catch (_: Exception)`
 * (which catches `CancellationException` too).
 */
private suspend fun downloadAndDecodeAnimatedImage(url: String): UIImage? {
    return try {
        val bytes: ByteArray = httpClient.get(url).body()
        val image = withContext(Dispatchers.Default) { decodeAnimatedImage(bytes) }
        if (image == null) {
            /* Decode failure with non-empty bytes usually means the body was truncated
             * upstream. Most common cause on Darwin: the Ktor Logging plugin tees the
             * response channel, and on Kotlin/Native the consumer side receives partial
             * bytes for binary bodies. The PlatformUtils.ios.kt config filters API hosts
             * only to avoid this — if you ever see this log line for a static.klipy.com
             * URL, the filter has been broken or removed. */
            val firstFour = bytes.take(4).joinToString(" ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            loggy("AnimatedImage: decode FAILED url=$url bytes=${bytes.size} firstFour=[$firstFour]")
        }
        image
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        loggy("AnimatedImage: download FAILED url=$url ${e::class.simpleName}: ${e.message}")
        null
    }
}

/**
 * Decodes raw image bytes into a [UIImage]. For multi-frame GIF/WebP/APNG images,
 * all frames are extracted via ImageIO's CGImageSource and combined into a
 * natively-animated UIImage via [UIImage.animatedImageWithImages]. Per-frame delay
 * is read from the source properties so the resulting animation runs at the correct
 * speed; UIImage distributes the total duration evenly across frames, which is a
 * close-enough approximation for content with mostly uniform timing.
 */
@OptIn(ExperimentalForeignApi::class)
private fun decodeAnimatedImage(bytes: ByteArray): UIImage? {
    if (bytes.isEmpty()) return null

    return bytes.usePinned { pinned ->
        val ubytePtr = pinned.addressOf(0).reinterpret<UByteVar>()
        val cfData = CFDataCreate(null, ubytePtr, bytes.size.toLong()) ?: return@usePinned null

        val source = CGImageSourceCreateWithData(cfData, null)
        CFRelease(cfData)
        if (source == null) return@usePinned null

        val frameCount = CGImageSourceGetCount(source).toInt()

        if (frameCount <= 1) {
            /* Static image — single frame */
            val cgImage = CGImageSourceCreateImageAtIndex(source, 0u, null)
            CFRelease(source)
            return@usePinned cgImage?.let { UIImage.imageWithCGImage(it) }
        }

        /* Animated image — extract every frame and sum per-frame delays for total duration. */
        val frames = mutableListOf<UIImage>()
        var totalDuration = 0.0

        for (i in 0 until frameCount) {
            val cgImage = CGImageSourceCreateImageAtIndex(source, i.toULong(), null) ?: continue
            frames.add(UIImage.imageWithCGImage(cgImage))
            totalDuration += readFrameDelaySeconds(source, i.toULong())
        }
        CFRelease(source)

        if (frames.isEmpty()) return@usePinned null

        /* Sub-20ms delays in GIF metadata are routinely treated as 100ms by browsers
         * and most image viewers because old encoders abused tiny delays. Match that
         * behavior so animations don't appear to run at warp speed. */
        if (totalDuration < frames.size * MIN_FRAME_DELAY_SECONDS) {
            totalDuration = frames.size * DEFAULT_FRAME_DELAY_SECONDS
        }

        UIImage.animatedImageWithImages(frames, totalDuration)
    }
}

/* GIF/APNG/WebP convention: delays under ~20ms are interpreted as 100ms. */
private const val MIN_FRAME_DELAY_SECONDS = 0.02
private const val DEFAULT_FRAME_DELAY_SECONDS = 0.1

/**
 * Reads the per-frame delay (seconds) from the image source's frame properties.
 * Tries unclamped values first (true encoded delay) and falls back to clamped
 * values, then to the default if neither is present.
 */
@OptIn(ExperimentalForeignApi::class)
private fun readFrameDelaySeconds(source: CGImageSourceRef, index: ULong): Double {
    val props = CGImageSourceCopyPropertiesAtIndex(source, index, null) ?: return DEFAULT_FRAME_DELAY_SECONDS

    try {
        val gifDict = props.nestedDict(kCGImagePropertyGIFDictionary)
        if (gifDict != null) {
            val d = gifDict.doubleValue(kCGImagePropertyGIFUnclampedDelayTime)
                ?: gifDict.doubleValue(kCGImagePropertyGIFDelayTime)
            if (d != null) return d
        }

        val pngDict = props.nestedDict(kCGImagePropertyPNGDictionary)
        if (pngDict != null) {
            val d = pngDict.doubleValue(kCGImagePropertyAPNGUnclampedDelayTime)
                ?: pngDict.doubleValue(kCGImagePropertyAPNGDelayTime)
            if (d != null) return d
        }

        return DEFAULT_FRAME_DELAY_SECONDS
    } finally {
        CFRelease(props)
    }
}

/** Look up a nested CFDictionary by key. Returns null when the key is missing or the K/N
 *  binding doesn't expose it on this platform. */
@OptIn(ExperimentalForeignApi::class)
private fun CFDictionaryRef.nestedDict(key: kotlinx.cinterop.CPointer<*>?): CFDictionaryRef? {
    if (key == null) return null
    val ptr = CFDictionaryGetValue(this, key) ?: return null
    return ptr.reinterpret()
}

/** Look up a Double-valued CFNumber by key. */
@OptIn(ExperimentalForeignApi::class)
private fun CFDictionaryRef.doubleValue(key: kotlinx.cinterop.CPointer<*>?): Double? {
    if (key == null) return null
    val ptr = CFDictionaryGetValue(this, key) ?: return null
    val numRef: CFNumberRef = ptr.reinterpret()

    val value = memScoped {
        val out = alloc<DoubleVar>()
        if (CFNumberGetValue(numRef, kCFNumberDoubleType, out.ptr)) out.value else null
    }
    return value?.takeIf { it > 0.0 }
}
