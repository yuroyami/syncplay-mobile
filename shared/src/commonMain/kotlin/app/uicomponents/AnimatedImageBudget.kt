package app.uicomponents

import androidx.compose.ui.unit.IntSize
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.sync.Semaphore
import kotlinx.io.readByteArray
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The limits for an animated image (a GIF in chat or in the GIF panel) on iOS and desktop. These
 * two platforms decode such images themselves, while Android and the web use Coil. Each limit
 * applies before the large allocation that it guards.
 */
internal object AnimatedImageBudget {
    /** A download stops, and the image fails, once its body passes this many bytes. */
    const val MAX_DOWNLOAD_BYTES: Long = 8L * 1024 * 1024

    /** An image with more frames is refused. */
    const val MAX_FRAMES = 400

    /** An image with more pixels in one frame is refused, because each frame is first decoded at full size. */
    const val MAX_SOURCE_PIXELS = 4_000_000

    /** The decoded frames of one image hold at most this many bytes, at four bytes a pixel. */
    const val MAX_DECODED_BYTES: Long = 24L * 1024 * 1024

    /** The short edge of a decoded frame, in pixels. Chat and the GIF panel draw smaller square tiles. */
    const val TILE_EDGE_PX = 512

    /** An image that needs a smaller short edge than this to fit [MAX_DECODED_BYTES] is refused. */
    const val MIN_EDGE_PX = 64

    /** The downloads and decodes that run at the same time. Every other tile waits for a turn. */
    val loads = Semaphore(4)

    /**
     * The size at which to decode the frames of a [width] by [height] image with [frames] frames,
     * or null when the image is over a limit. It never enlarges an image.
     */
    fun decodeSize(width: Int, height: Int, frames: Int): IntSize? {
        if (width <= 0 || height <= 0 || frames <= 0) return null
        if (frames > MAX_FRAMES || width.toLong() * height > MAX_SOURCE_PIXELS) return null
        val shortEdge = min(width, height)
        var scale = min(1.0, TILE_EDGE_PX.toDouble() / shortEdge)
        val bytes = frames * (width * scale) * (height * scale) * 4
        if (bytes > MAX_DECODED_BYTES) scale *= sqrt(MAX_DECODED_BYTES / bytes)
        if (scale < 1.0 && shortEdge * scale < MIN_EDGE_PX) return null
        return IntSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    }
}

/**
 * Downloads [url] whole, or returns null when the answer is not a success or its body passes
 * [maxBytes]. The body is read as it arrives, so a larger body is never held in memory.
 */
internal suspend fun HttpClient.downloadAtMost(url: String, maxBytes: Long): ByteArray? =
    prepareGet(url).execute { response ->
        if (!response.status.isSuccess()) return@execute null
        if ((response.contentLength() ?: 0L) > maxBytes) return@execute null
        val body = response.bodyAsChannel().readRemaining(maxBytes + 1).readByteArray()
        body.takeIf { it.size <= maxBytes }
    }
