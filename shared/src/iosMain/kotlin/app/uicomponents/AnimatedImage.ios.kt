package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.layout.ContentScale
import app.klipy.KlipyUtils
import app.utils.httpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceGetCount
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

@Suppress("DEPRECATION")
@Composable
actual fun AnimatedImage(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
) {
    var nativeImage by remember(url) { mutableStateOf<UIImage?>(null) }

    LaunchedEffect(url) {
        nativeImage = downloadAndDecodeAnimatedImage(url)
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
        },
        interactive = false,
        modifier = modifier
    )
}

/** Downloads image data using the shared Ktor client and decodes it as animated GIF/WebP. */
private suspend fun downloadAndDecodeAnimatedImage(url: String): UIImage? {
    return try {
        val bytes: ByteArray = httpClient.get(url).body()
        decodeAnimatedImage(bytes)
    } catch (_: Exception) {
        null
    }
}

/**
 * Decodes raw image bytes into a [UIImage]. For multi-frame GIF/WebP images,
 * all frames are extracted via ImageIO's CGImageSource and combined into a
 * natively-animated UIImage via [UIImage.animatedImageWithImages].
 */
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

        /* Animated image — extract every frame */
        val frames = mutableListOf<UIImage>()
        for (i in 0 until frameCount) {
            val cgImage = CGImageSourceCreateImageAtIndex(source, i.toULong(), null) ?: continue
            frames.add(UIImage.imageWithCGImage(cgImage))
        }
        CFRelease(source)

        if (frames.isEmpty()) return@usePinned null

        /* Default ~100ms per frame. Accurate per-frame timing requires parsing
         * the "{GIF}"/"{WebP}" CFDictionary from each frame's properties, which
         * needs toll-free bridging unavailable in K/N. 100ms (10 FPS) is the
         * most common GIF frame rate and works well as a default. */
        val totalDuration = frameCount * 0.1
        UIImage.animatedImageWithImages(frames, totalDuration)
    }
}
