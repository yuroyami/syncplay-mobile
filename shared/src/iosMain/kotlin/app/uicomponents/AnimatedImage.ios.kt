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
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGImageRelease
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/**
 * A process-wide LRU cache of decoded [UIImage]s, keyed by URL. It survives recomposition,
 * panel teardown and even leaving the room (the group of people watching together). So reopening
 * the GIF panel or toggling the HUD does not download and decode 24 GIFs again. NSURLCache (set
 * up in PlatformUtils.ios.kt) caches the bytes. This cache also skips the CGImageSource frame
 * extraction, which is the main cost of an animated GIF (about 100 to 300 ms for a multi-frame
 * source).
 *
 * Two bounds apply: 64 entries (a panel page is 24 tiles, so this holds more than two pages) and
 * [IMAGE_CACHE_MAX_BYTES] of decoded frames. The oldest untouched entry goes first. A lock guards
 * the cache: reads run during composition, and writes run after [downloadAndDecodeAnimatedImage]
 * returns (its decode step runs on Dispatchers.Default).
 */
private const val IMAGE_CACHE_MAX_ENTRIES = 64
private const val IMAGE_CACHE_MAX_BYTES = 48L * 1024 * 1024
private val imageCacheLock = SynchronizedObject()
/* Kotlin/Native's LinkedHashMap keeps insertion order only; it has no access-order constructor
 * like the JVM one. So a read removes and re-inserts the entry, which moves it to the tail, and
 * eviction takes the head (the oldest untouched entry). */
private val imageCache = LinkedHashMap<String, UIImage>()

private fun cachedImage(url: String): UIImage? = synchronized(imageCacheLock) {
    val hit = imageCache.remove(url) ?: return@synchronized null
    imageCache[url] = hit
    hit
}

/** Estimates the memory of a cached image: its decoded frames, at four bytes per pixel. */
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.approximateBytes(): Long {
    val (w, h) = size.useContents { width to height }
    val pixels = (w * scale) * (h * scale)
    val frames = (images?.size ?: 1).coerceAtLeast(1)
    return (pixels * 4.0 * frames).toLong()
}

private fun cacheImage(url: String, image: UIImage) {
    synchronized(imageCacheLock) {
        imageCache.remove(url)
        imageCache[url] = image
        // Two bounds: a count for the panel's grid, and bytes so a few long GIFs cannot fill memory.
        var bytes = imageCache.values.sumOf { it.approximateBytes() }
        while (imageCache.size > IMAGE_CACHE_MAX_ENTRIES || (bytes > IMAGE_CACHE_MAX_BYTES && imageCache.size > 1)) {
            val oldest = imageCache.keys.iterator().next()
            bytes -= imageCache.remove(oldest)?.approximateBytes() ?: 0L
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
    onLoaded: (() -> Unit)?,
    onFailed: (() -> Unit)?,
) {
    /* Read the cache synchronously during composition, so a cache hit draws on the first frame,
     * with no flicker and no wait for LaunchedEffect. A miss goes to the effect below, which
     * downloads, decodes and fills the cache. */
    var nativeImage by remember(url) { mutableStateOf<UIImage?>(cachedImage(url)) }

    LaunchedEffect(url) {
        if (nativeImage != null) return@LaunchedEffect
        nativeImage = downloadAndDecodeAnimatedImage(url)?.also { cacheImage(url, it) }
        if (nativeImage == null) onFailed?.invoke()
    }
    LaunchedEffect(nativeImage) { if (nativeImage != null) onLoaded?.invoke() }

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
            // An invisible tile drops its image, so UIKit stops animating it; the decoded frames
            // stay in the cache, so showing it again is instant.
            imageView.image = if (alpha > 0f) nativeImage else null
            /* Set the native alpha. Compose's `Modifier.alpha` does not reach UIKit interop layers,
             * so the UIImageView draws at full opacity unless its alpha is set here. This is what
             * fades the pixels with the parent HUD. */
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
 * Downloads image bytes with the shared Ktor client and decodes them as an animated image. The
 * decode runs on `Dispatchers.Default`, because [decodeAnimatedImage] extracts the CGImageSource
 * frames one by one, synchronously. On the LaunchedEffect's own dispatcher (the Compose main
 * thread), a 24-tile grid would block the UI thread for hundreds of ms per tile.
 * CancellationException is rethrown, so a closed or recomposed panel can cancel the running
 * download. A plain `catch (e: Exception)` would swallow the cancellation too.
 */
private suspend fun downloadAndDecodeAnimatedImage(url: String): UIImage? {
    return try {
        val bytes: ByteArray = httpClient.get(url).body()
        val image = withContext(Dispatchers.Default) { decodeAnimatedImage(bytes) }
        if (image == null) {
            /* A decode failure with non-empty bytes usually means a truncated body. The most
             * common cause on Darwin: the Ktor Logging plugin copies the response channel, and on
             * Kotlin/Native the reading side then gets partial bytes for binary bodies. The
             * PlatformUtils.ios.kt config logs API hosts only, to avoid this. If this line shows
             * up for a static.klipy.com URL, that filter is broken or gone. */
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
 * Decodes raw image bytes into a [UIImage]. For a multi-frame GIF, WebP or APNG image, ImageIO's
 * CGImageSource extracts all frames, and [UIImage.animatedImageWithImages] combines them into a
 * natively animated UIImage. The frame delays come from the source properties (GIF and APNG;
 * other formats use the 100 ms default), so the animation runs at about the right speed. UIImage
 * spreads the total duration evenly across the frames, which is close enough for content with
 * mostly even timing.
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
            /* Static image: a single frame */
            val cgImage = CGImageSourceCreateImageAtIndex(source, 0u, null)
            CFRelease(source)
            // A Create call returns an owned reference. UIImage keeps its own, so release this
            // one, or every decoded image leaks one CGImage for the life of the process.
            return@usePinned cgImage?.let { UIImage.imageWithCGImage(it).also { _ -> CGImageRelease(it) } }
        }

        /* Animated image: extract every frame, and add up the frame delays for the duration. */
        val frames = mutableListOf<UIImage>()
        var totalDuration = 0.0

        for (i in 0 until frameCount) {
            val cgImage = CGImageSourceCreateImageAtIndex(source, i.toULong(), null) ?: continue
            frames.add(UIImage.imageWithCGImage(cgImage))
            CGImageRelease(cgImage)
            totalDuration += readFrameDelaySeconds(source, i.toULong())
        }
        CFRelease(source)

        if (frames.isEmpty()) return@usePinned null

        /* Browsers and most image viewers treat GIF delays under 20 ms as 100 ms, because old
         * encoders abused tiny delays. Do the same, based on the average delay, so animations
         * do not run far too fast. */
        if (totalDuration < frames.size * MIN_FRAME_DELAY_SECONDS) {
            totalDuration = frames.size * DEFAULT_FRAME_DELAY_SECONDS
        }

        UIImage.animatedImageWithImages(frames, totalDuration)
    }
}

/* The GIF, APNG and WebP convention: a delay under about 20 ms counts as 100 ms. */
private const val MIN_FRAME_DELAY_SECONDS = 0.02
private const val DEFAULT_FRAME_DELAY_SECONDS = 0.1

/**
 * Reads one frame's delay (in seconds) from the image source's GIF or APNG frame properties.
 * Tries the unclamped value first (the real encoded delay), then the clamped value, then the
 * default.
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

/** Looks up a nested CFDictionary by key. Returns null when the key is missing, or when the
 *  Kotlin/Native binding does not expose the key on this platform. */
@OptIn(ExperimentalForeignApi::class)
private fun CFDictionaryRef.nestedDict(key: kotlinx.cinterop.CPointer<*>?): CFDictionaryRef? {
    if (key == null) return null
    val ptr = CFDictionaryGetValue(this, key) ?: return null
    return ptr.reinterpret()
}

/** Looks up a CFNumber by key as a Double. Returns null when it is missing or not positive. */
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
