package app.player.kite

import app.player.resolver.extractYtId
import com.eygraber.uri.Uri
import io.github.yuroyami.kiteplayer.MediaIo
import io.github.yuroyami.kiteplayer.MediaIoFactory
import io.github.yuroyami.kiteplayer.PlaybackWarning
import io.github.yuroyami.kiteplayer.network.KtorMediaIo

/**
 * Serves a short forward seek by reading on through the open stream, instead of a new request.
 *
 * KitePlayer's byte cache treats every seek past its window as a far one, and its network reader
 * answers a far seek with a new request. The visualizer's song scan reads only the audio of a
 * file that also holds video, so it skips every video chunk, and each skip cost a request: a
 * YouTube song that downloads in 10 seconds took 98 seconds to scan.
 */
internal class ReadThroughMediaIo(
    private val upstream: MediaIo,
    private val readThroughBytes: Long = READ_THROUGH_BYTES,
) : MediaIo {
    /** Where the next upstream read starts. */
    private var upstreamAt = 0L

    /** Bytes to read and drop before the next read serves the caller. */
    private var pendingSkip = 0L

    private val scratch by lazy { ByteArray(SKIP_CHUNK_BYTES) }

    override val size: Long? get() = upstream.size
    override val seekable: Boolean get() = upstream.seekable

    override suspend fun read(into: ByteArray, offset: Int, length: Int): Int {
        while (pendingSkip > 0) {
            val skipped = upstream.read(scratch, 0, minOf(scratch.size.toLong(), pendingSkip).toInt())
            // The end of the stream, or nothing yet: the caller hears the same as from upstream.
            if (skipped <= 0) return skipped
            upstreamAt += skipped
            pendingSkip -= skipped
        }
        val read = upstream.read(into, offset, length)
        if (read > 0) upstreamAt += read
        return read
    }

    override suspend fun seek(position: Long) {
        val ahead = position - upstreamAt
        if (ahead in 0..readThroughBytes) {
            pendingSkip = ahead
        } else {
            upstream.seek(position)
            upstreamAt = position
            pendingSkip = 0
        }
    }

    override fun close() = upstream.close()

    override fun setWarningSink(sink: (PlaybackWarning) -> Unit) = upstream.setWarningSink(sink)

    private companion object {
        /** Past this, a new request costs less than reading the gap. A video chunk is far smaller. */
        const val READ_THROUGH_BYTES = 1L shl 20
        const val SKIP_CHUNK_BYTES = 64 * 1024
    }
}

/**
 * A label for a YouTube file stream that stays the same for the same video and format, or null for
 * anything else: a live stream's manifest, another host, or a stream with no known page.
 *
 * The visualizer keys the study of a song by the item's URI. YouTube hands out a new stream
 * address on every resolve, so under its address a song was studied again on every play.
 */
internal fun youTubeStreamLabel(streamUrl: String, pageUrl: String?): String? {
    val videoId = pageUrl?.let(::extractYtId) ?: return null
    val stream = Uri.parseOrNull(streamUrl) ?: return null
    val host = stream.host ?: return null
    if (!host.endsWith(".googlevideo.com") || stream.path != "/videoplayback") return null
    val itag = stream.getQueryParameter("itag")?.takeIf { it.isNotBlank() } ?: return null
    return "youtube:$videoId/$itag"
}

/**
 * The KitePlayer item for a YouTube file stream: KitePlayer's own network reader behind
 * [ReadThroughMediaIo], under the label from [youTubeStreamLabel]. Null when that has no label, and
 * the caller then passes the address as it is.
 */
internal fun youTubeMediaPath(streamUrl: String, pageUrl: String?): KiteMediaPath? {
    val label = youTubeStreamLabel(streamUrl, pageUrl) ?: return null
    // A factory, so every open (the player's, each scan range's, a track switch's) gets its own reader.
    return KiteMediaPath(uri = label, io = MediaIoFactory { ReadThroughMediaIo(KtorMediaIo.open(streamUrl)) })
}
