package app.player.exo

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.math.min

private const val RANGE_CHUNK_BYTES = 8L * 1024L * 1024L
private const val RANGE_WORKERS = 4
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

@OptIn(UnstableApi::class)
internal class ExoBufferAhead(
    private val context: Context,
    private val uri: Uri,
    private val ranges: List<CacheRange>,
    private val contentLength: Long,
) {
    private val cancelled = AtomicBoolean(false)
    private val writers = mutableSetOf<CacheWriter>()

    fun cancel() {
        cancelled.set(true)
        cancelWriters()
    }

    suspend fun cache() = coroutineScope {
        val failure = AtomicReference<IOException?>(null)
        val http = DefaultHttpDataSource.Factory()
        val rangeFactory = ExoMediaCache.cacheDataSourceFactory(context)
            .setUpstreamDataSourceFactory(DataSource.Factory {
                ValidatingRangeDataSource(http.createDataSource(), contentLength)
            })

        (0 until min(RANGE_WORKERS, ranges.size)).map { worker ->
            async(Dispatchers.IO) {
                for (index in worker until ranges.size step RANGE_WORKERS) {
                    coroutineContext.ensureActive()
                    if (cancelled.get() || failure.get() != null) break
                    val range = ranges[index]
                    val spec = DataSpec.Builder().setUri(uri)
                        .setPosition(range.start).setLength(range.length)
                        .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION).build()
                    try {
                        write(CacheWriter(rangeFactory.createDataSourceForDownloading(), spec, null, null))
                    } catch (error: IOException) {
                        if (!cancelled.get() && failure.compareAndSet(null, error)) cancelWriters()
                        break
                    }
                }
            }
        }.awaitAll()
        if (!cancelled.get()) failure.get()?.let { throw it }
    }

    private fun write(writer: CacheWriter) {
        synchronized(writers) {
            if (cancelled.get()) return
            writers += writer
        }
        try {
            writer.cache()
        } finally {
            synchronized(writers) { writers -= writer }
        }
    }

    private fun cancelWriters() {
        synchronized(writers) { writers.forEach(CacheWriter::cancel) }
    }
}

@OptIn(UnstableApi::class)
private class ValidatingRangeDataSource(
    private val source: HttpDataSource,
    private val contentLength: Long,
) : DataSource by source {
    override fun open(dataSpec: DataSpec): Long {
        val length = source.open(dataSpec)
        val header = source.responseHeaders["Content-Range"]?.firstOrNull()
        if (source.responseCode != 206 ||
            !matchesContentRange(header, dataSpec.position, dataSpec.length) ||
            parseContentRange(header)?.total != contentLength
        ) {
            source.close()
            throw IOException("Server did not honor the requested media byte range")
        }
        return length
    }
}

@OptIn(UnstableApi::class)
internal fun probeRangeLength(uri: Uri): Long? {
    val source = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(5_000)
        .setReadTimeoutMs(5_000)
        .createDataSource()
    return try {
        source.open(DataSpec.Builder().setUri(uri).setLength(1).build())
        if (source.responseCode != 206) null
        else contentRangeTotal(source.responseHeaders["Content-Range"]?.firstOrNull())
    } catch (_: IOException) {
        null
    } finally {
        runCatching { source.close() }
    }
}
