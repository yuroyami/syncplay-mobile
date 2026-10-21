package app.player.exo

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.min

private const val RANGE_CHUNK_BYTES = 8L * 1024L * 1024L
private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

private data class ResponseRange(val start: Long, val end: Long, val total: Long)

private fun parseContentRange(header: String?): ResponseRange? {
    val parts = CONTENT_RANGE.matchEntire(header ?: return null)?.groupValues ?: return null
    val start = parts[1].toLongOrNull() ?: return null
    val end = parts[2].toLongOrNull() ?: return null
    val total = parts[3].toLongOrNull() ?: return null
    return ResponseRange(start, end, total).takeIf { start <= end && end < total }
}

internal data class CacheRange(val start: Long, val length: Long)

internal fun cacheRanges(length: Long, chunkSize: Long = RANGE_CHUNK_BYTES): List<CacheRange> {
    require(length >= 0 && chunkSize > 0)
    val ranges = mutableListOf<CacheRange>()
    var start = 0L
    while (start < length) {
        val size = min(chunkSize, length - start)
        ranges += CacheRange(start, size)
        start += size
    }
    return ranges
}

internal fun contentRangeTotal(header: String?): Long? {
    val range = parseContentRange(header) ?: return null
    return range.total.takeIf { range.start == 0L && range.end == 0L }
}

internal fun matchesContentRange(header: String?, start: Long, length: Long): Boolean {
    if (start < 0 || length <= 0 || start > Long.MAX_VALUE - length) return false
    val range = parseContentRange(header) ?: return false
    return range.start == start && range.end == start + length - 1
}

internal fun rangeRequestHeaders(headers: Map<String, String>, start: Long, length: Long): Map<String, String> {
    require(start >= 0 && length > 0 && start <= Long.MAX_VALUE - length)
    return headers.filterKeys { !it.equals("Range", ignoreCase = true) } +
        ("Range" to "bytes=$start-${start + length - 1}")
}

internal data class HttpMediaInfo(val length: Long, val supportsRanges: Boolean)

internal class RangeNotSupportedException : IOException("Server no longer supports byte ranges")

internal fun httpMediaInfo(code: Int, contentRange: String?, contentLength: String?): HttpMediaInfo? =
    when (code) {
        206 -> contentRangeTotal(contentRange)?.let { HttpMediaInfo(it, true) }
        200 -> contentLength?.toLongOrNull()?.takeIf { it > 0 }?.let { HttpMediaInfo(it, false) }
        else -> null
    }

/** Waiting here keeps the current response open rather than reconnecting at every window update. */
internal suspend fun readCacheStream(
    start: Long,
    read: (ByteArray, Int) -> Int,
    limit: () -> Long = { Long.MAX_VALUE },
    onPosition: (Long) -> Unit = {},
    onPause: () -> Unit = {},
) {
    val buffer = ByteArray(64 * 1024)
    var position = start
    while (true) {
        coroutineContext.ensureActive()
        val remaining = limit() - position
        if (remaining <= 0) {
            onPause()
            delay(250)
            continue
        }
        val count = read(buffer, min(buffer.size.toLong(), remaining).toInt())
        if (count == C.RESULT_END_OF_INPUT) return
        if (count <= 0) throw IOException("Media source made no progress")
        position += count
        onPosition(position)
    }
}

@OptIn(UnstableApi::class)
internal class ExoBufferAhead(
    private val context: Context,
    private val uri: Uri,
    private val contentLength: Long,
    private val cacheKey: String,
) {
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    suspend fun cache(ranges: List<CacheRange>) {
        val http = DefaultHttpDataSource.Factory()
        val rangeFactory = ExoMediaCache.cacheDataSourceFactory(context)
            .setUpstreamDataSourceFactory(DataSource.Factory {
                ValidatingRangeDataSource(http.createDataSource(), contentLength)
            })

        for (range in ranges) {
            coroutineContext.ensureActive()
            if (cancelled.get()) break
            val spec = DataSpec.Builder().setUri(uri).setKey(cacheKey)
                .setPosition(range.start).setLength(range.length)
                .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION).build()
            read(rangeFactory.createDataSourceForDownloading(), spec)
        }
    }

    suspend fun cacheSequentially(start: Long, limit: () -> Long, onPosition: (Long) -> Unit) {
        val spec = DataSpec.Builder().setUri(uri).setKey(cacheKey).setPosition(start)
            .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION).build()
        val sink = CommittingCacheDataSink(
            CacheDataSink(ExoMediaCache.cache(context), ExoMediaCache.FRAGMENT_BYTES),
        )
        val source = ExoMediaCache.cacheDataSourceFactory(context)
            .setCacheWriteDataSinkFactory { sink }.createDataSourceForDownloading()
        read(source, spec, limit, onPosition, sink::commit)
    }

    private suspend fun read(
        source: DataSource,
        spec: DataSpec,
        limit: () -> Long = { Long.MAX_VALUE },
        onPosition: (Long) -> Unit = {},
        onPause: () -> Unit = {},
    ) {
        try {
            coroutineContext.ensureActive()
            if (cancelled.get()) return
            source.open(spec)
            readCacheStream(spec.position, { buffer, length ->
                if (cancelled.get()) C.RESULT_END_OF_INPUT else source.read(buffer, 0, length)
            }, limit, onPosition, onPause)
        } finally {
            source.close()
        }
    }
}

/** Commit only the file sink; the TeeDataSource's HTTP response stays open across a pause. */
@OptIn(UnstableApi::class)
internal class CommittingCacheDataSink(private val sink: DataSink) : DataSink {
    private var spec: DataSpec? = null
    private var written = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec) {
        spec = dataSpec
        written = 0
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (!opened) {
            sink.open(checkNotNull(spec).subrange(written))
            opened = true
        }
        sink.write(buffer, offset, length)
        written += length
    }

    fun commit() {
        if (opened) {
            opened = false
            sink.close()
        }
    }

    override fun close() {
        try {
            commit()
        } finally {
            spec = null
        }
    }
}

@OptIn(UnstableApi::class)
internal class ValidatingRangeDataSource(
    private val source: HttpDataSource,
    private val contentLength: Long,
) : DataSource by source {
    override fun open(dataSpec: DataSpec): Long {
        // A nonzero DataSpec.position makes DefaultHttpDataSource discard a 200 response's prefix
        // inside open(). Send Range explicitly so it returns the headers before reading any body.
        val headers = rangeRequestHeaders(dataSpec.httpRequestHeaders, dataSpec.position, dataSpec.length)
        val length = source.open(dataSpec.buildUpon().setPosition(0).setLength(C.LENGTH_UNSET.toLong())
            .setHttpRequestHeaders(headers).build())
        val header = source.responseHeaders["Content-Range"]?.firstOrNull()
        if (source.responseCode != 206 ||
            !matchesContentRange(header, dataSpec.position, dataSpec.length) ||
            parseContentRange(header)?.total != contentLength
        ) {
            val responseCode = source.responseCode
            source.close()
            if (responseCode == 200) throw RangeNotSupportedException()
            throw IOException("Server did not honor the requested media byte range")
        }
        return length
    }
}

@OptIn(UnstableApi::class)
internal fun probeHttpMedia(uri: Uri): HttpMediaInfo? {
    val source = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(5_000)
        .setReadTimeoutMs(5_000)
        .createDataSource()
    return try {
        source.open(DataSpec.Builder().setUri(uri).setLength(1).build())
        httpMediaInfo(source.responseCode, source.responseHeaders["Content-Range"]?.firstOrNull(),
            source.responseHeaders["Content-Length"]?.firstOrNull())
    } catch (_: IOException) {
        null
    } finally {
        runCatching { source.close() }
    }
}
