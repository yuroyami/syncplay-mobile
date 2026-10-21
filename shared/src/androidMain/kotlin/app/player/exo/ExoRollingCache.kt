package app.player.exo

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.extractor.SeekMap
import app.utils.loggy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val WINDOW_MS = 300_000L
private const val METADATA_BYTES = 2L * 1024L * 1024L

internal data class CacheTimeWindow(val startMs: Long, val endMs: Long)

internal fun cacheTimeWindow(positionMs: Long, durationMs: Long): CacheTimeWindow {
    val size = min(WINDOW_MS, durationMs.coerceAtLeast(0))
    val start = (positionMs - size / 2).coerceIn(0, (durationMs - size).coerceAtLeast(0))
    return CacheTimeWindow(start, start + size)
}

/** Snapshot the extractor's index on its own thread; background work only reads these arrays. */
@OptIn(UnstableApi::class)
internal class RollingCacheIndex private constructor(
    private val durationMs: Long,
    private val stepMs: Long,
    private val before: LongArray,
    private val after: LongArray,
) {
    fun position(positionMs: Long): Long = before[(positionMs.coerceAtLeast(0) / stepMs)
        .coerceAtMost(before.lastIndex.toLong()).toInt()]

    fun range(positionMs: Long, contentLength: Long, maxBytes: Long): CacheRange? {
        if (contentLength <= 0 || maxBytes <= 0) return null
        val time = cacheTimeWindow(positionMs, durationMs)
        val first = (time.startMs / stepMs).toInt().coerceAtMost(before.lastIndex)
        // Include the next indexed seek point so the final keyframe's samples are available.
        val last = ((time.endMs + stepMs - 1) / stepMs + 1)
            .coerceAtMost(after.lastIndex.toLong()).toInt()
        var start = if (time.startMs == 0L) 0L else before[first].coerceAtMost(contentLength)
        var end = if (time.endMs >= durationMs) contentLength else after[last].coerceAtMost(contentLength)
        if (end <= start) return null
        if (end - start > maxBytes) {
            val center = position(positionMs).coerceIn(start, end)
            start = (center - maxBytes / 2).coerceIn(start, end - maxBytes)
            end = start + maxBytes
        }
        return CacheRange(start, end - start)
    }

    companion object {
        fun from(seekMap: SeekMap): RollingCacheIndex? {
            if (!seekMap.isSeekable || seekMap.durationUs <= 0) return null
            val durationMs = max(1, seekMap.durationUs / 1000)
            val stepMs = max(5_000, (durationMs + 9_999) / 10_000)
            val count = ((durationMs + stepMs - 1) / stepMs).toInt() + 1
            val before = LongArray(count)
            val after = LongArray(count)
            for (i in 0 until count) {
                val points = seekMap.getSeekPoints(min(i * stepMs, durationMs) * 1000)
                before[i] = points.first.position
                after[i] = max(points.first.position, points.second.position)
                if (before[i] < 0 || (i > 0 && before[i] < before[i - 1])) return null
            }
            return RollingCacheIndex(durationMs, stepMs, before, after)
        }
    }
}

private fun overlaps(start: Long, length: Long, other: CacheRange): Boolean =
    start < other.start + other.length && other.start < start + length

internal fun keepCacheSpan(start: Long, length: Long, window: CacheRange, contentLength: Long): Boolean =
    overlaps(start, length, window) || start < METADATA_BYTES ||
        start + length > (contentLength - METADATA_BYTES).coerceAtLeast(0)

internal fun sequentialCacheHasGap(playhead: Long, reader: Long, cachedAhead: Long): Boolean =
    playhead < reader && (cachedAhead <= 0 || reader - playhead - cachedAhead > ExoMediaCache.FRAGMENT_BYTES)

internal fun sequentialRetentionWindow(window: CacheRange, reader: Long): CacheRange =
    CacheRange(window.start, max(window.start + window.length, reader) - window.start)

internal fun rollingCacheRanges(window: CacheRange, playhead: Long, contentLength: Long): List<CacheRange> {
    val regions = listOf(
        CacheRange(0, min(METADATA_BYTES, contentLength)),
        window,
        CacheRange((contentLength - METADATA_BYTES).coerceAtLeast(0), min(METADATA_BYTES, contentLength)),
    ).sortedBy { it.start }
    val merged = mutableListOf<CacheRange>()
    for (region in regions) {
        val previous = merged.lastOrNull()
        if (previous != null && region.start <= previous.start + previous.length) {
            merged[merged.lastIndex] = CacheRange(previous.start,
                max(previous.start + previous.length, region.start + region.length) - previous.start)
        } else merged += region
    }
    return merged.flatMap { region ->
        cacheRanges(region.length).map { CacheRange(region.start + it.start, it.length) }
    }.sortedWith(compareBy<CacheRange> {
        when {
            playhead >= it.start && playhead < it.start + it.length -> 0
            it.start < METADATA_BYTES || it.start + it.length > contentLength - METADATA_BYTES -> 1
            it.start >= playhead -> 2
            else -> 3
        }
    }.thenBy { abs(it.start - playhead) })
}

@OptIn(UnstableApi::class)
internal class ExoRollingCache(private val context: Context, private val uri: Uri) {
    private val stopped = AtomicBoolean(false)
    @Volatile private var index: RollingCacheIndex? = null
    @Volatile private var batch: ExoBufferAhead? = null

    fun onSeekMap(seekMap: SeekMap) {
        index = RollingCacheIndex.from(seekMap)
    }

    fun cancel() {
        stopped.set(true)
        batch?.cancel()
    }

    suspend fun run(positionMs: suspend () -> Long?) = coroutineScope {
        coroutineContext.ensureActive()
        if (stopped.get()) return@coroutineScope
        val cache = ExoMediaCache.cache(context)
        val key = uri.toString()
        cache.keys.filter { it != key }.forEach(cache::removeResource)
        val media = probeHttpMedia(uri) ?: return@coroutineScope
        val contentLength = media.length
        coroutineContext.ensureActive()
        if (stopped.get()) return@coroutineScope
        cache.applyContentMetadataMutations(key, ContentMetadataMutations().also {
            ContentMetadataMutations.setContentLength(it, contentLength)
        })
        var filling: Job? = null
        var fillPosition = 0L
        var previousPosition: Long? = null
        val retryAfter = AtomicLong(0)
        // Zero means a single sequential response; positive values are range workers.
        val parallelism = AtomicInteger(if (media.supportsRanges) RANGE_WORKERS else 0)
        val sequentialPosition = AtomicLong(0)
        val sequentialLimit = AtomicLong(0)
        val sequentialEnded = AtomicBoolean(false)
        try {
            while (isActive && !stopped.get()) {
                val position = positionMs() ?: break
                val currentIndex = index
                val window = currentIndex?.range(position, contentLength, ExoMediaCache.WINDOW_LIMIT_BYTES)
                val playhead = currentIndex?.position(position)
                val jumped = previousPosition?.let { abs(position - it) > 5_000 } == true
                previousPosition = position
                val workers = parallelism.get()
                val reader = sequentialPosition.get()
                val missingBehindReader = workers == 0 && jumped && playhead != null && playhead < reader &&
                    sequentialCacheHasGap(playhead, reader, cache.getCachedLength(key, playhead, reader - playhead))
                if (missingBehindReader) sequentialEnded.set(false)
                if (filling != null && (
                        (filling.isCompleted && (workers > 0 || !sequentialEnded.get())) ||
                        (workers > 0 && (jumped || abs(position - fillPosition) >= 30_000)) ||
                        missingBehindReader
                    )) {
                    batch?.cancel()
                    filling.cancelAndJoin()
                    filling = null
                    batch = null
                }
                if (window != null) {
                    val start = currentIndex.position(position)
                    sequentialLimit.set(if (window.start + window.length >= contentLength) Long.MAX_VALUE
                        else window.start + window.length)
                    // A sequential response cannot revisit discarded bytes without reconnecting.
                    val retained = if (workers == 0) sequentialRetentionWindow(window, reader) else window
                    cache.getCachedSpans(key).filterNot {
                        keepCacheSpan(it.position, it.length, retained, contentLength)
                    }.forEach(cache::removeSpan)
                    if (filling == null && SystemClock.elapsedRealtime() >= retryAfter.get() &&
                        (workers > 0 || !sequentialEnded.get())) {
                        val ranges = if (workers > 0) {
                            rollingCacheRanges(window, start, contentLength)
                                .filterNot { cache.isCached(key, it.start, it.length) }
                        } else emptyList()
                        if (workers == 0 || ranges.isNotEmpty()) {
                            val writer = ExoBufferAhead(context, uri, contentLength)
                            batch = writer
                            fillPosition = position
                            sequentialPosition.set(start)
                            filling = launch {
                                try {
                                    if (workers > 0) {
                                        writer.cache(ranges, workers)
                                    } else {
                                        writer.cacheSequentially(start, sequentialLimit::get,
                                            sequentialPosition::set)
                                        sequentialEnded.set(true)
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    if (error is RangeNotSupportedException) parallelism.set(0)
                                    else if (workers > 1) parallelism.set(1)
                                    retryAfter.set(SystemClock.elapsedRealtime() + 10_000)
                                    loggy("Rolling cache stopped filling: ${error.message}")
                                }
                            }
                        }
                    }
                }
                delay(1_000)
            }
        } finally {
            batch?.cancel()
            filling?.cancel()
        }
    }
}
