package app.player.exo

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
class ExoRollingCacheTest {
    @Test
    fun windowShiftsAtTheStartAndEndInsteadOfWastingHalfItsDuration() {
        assertEquals(CacheTimeWindow(0, 300_000), cacheTimeWindow(0, 1_200_000))
        assertEquals(CacheTimeWindow(450_000, 750_000), cacheTimeWindow(600_000, 1_200_000))
        assertEquals(CacheTimeWindow(900_000, 1_200_000), cacheTimeWindow(1_199_000, 1_200_000))
        assertEquals(CacheTimeWindow(0, 60_000), cacheTimeWindow(30_000, 60_000))
    }

    private fun seekMap(durationMs: Long = 1_200_000, bytesAt: (Long) -> Long): SeekMap = object : SeekMap {
        override fun isSeekable() = true
        override fun getDurationUs() = durationMs * 1000
        override fun getSeekPoints(timeUs: Long) = SeekMap.SeekPoints(SeekPoint(timeUs, bytesAt(timeUs / 1000)))
    }

    @Test
    fun theSameTimeWindowUsesMoreBytesInHighBitrateScenes() {
        val index = assertNotNull(RollingCacheIndex.from(seekMap {
            if (it <= 600_000) it * 100 else 60_000_000 + (it - 600_000) * 400
        }))
        val low = assertNotNull(index.range(300_000, 300_000_000, Long.MAX_VALUE))
        val high = assertNotNull(index.range(900_000, 300_000_000, Long.MAX_VALUE))
        assertEquals(15_000_000, low.start)
        assertEquals(120_000_000, high.start)
        assertTrue(high.length > low.length * 3)
    }

    @Test
    fun aHighBitrateWindowShrinksToTheSafetyBudgetAroundThePlayhead() {
        val index = assertNotNull(RollingCacheIndex.from(seekMap { it * 10_000 }))
        val range = assertNotNull(index.range(600_000, 12_000_000_000, 100_000_000))
        assertEquals(100_000_000, range.length)
        assertTrue(index.position(600_000) in range.start until range.start + range.length)
    }

    @Test
    fun movingTheWindowEvictsOldMediaButPreservesContainerMetadata() {
        val window = CacheRange(100_000_000, 30_000_000)
        assertFalse(keepCacheSpan(50_000_000, 1_000_000, window, 500_000_000))
        assertTrue(keepCacheSpan(110_000_000, 1_000_000, window, 500_000_000))
        assertTrue(keepCacheSpan(0, 1_000_000, window, 500_000_000))
        assertTrue(keepCacheSpan(499_000_000, 1_000_000, window, 500_000_000))
    }

    @Test
    fun overlappingMetadataAndMediaRequestsAreMergedBeforeDownloading() {
        val ranges = rollingCacheRanges(CacheRange(0, 20_000_000), 10_000_000, 21_000_000).sortedBy { it.start }
        assertEquals(0, ranges.first().start)
        assertEquals(21_000_000, ranges.sumOf { it.length })
        assertTrue(ranges.zipWithNext().all { (left, right) -> left.start + left.length == right.start })
    }

    @Test
    fun anUnseekableStreamDoesNotGetAnInventedByteWindow() {
        assertNull(RollingCacheIndex.from(SeekMap.Unseekable(1_200_000_000)))
    }

    @Test
    fun aSequentialResponseKeepsItsAlreadyDownloadedAheadBytesAfterABackwardSeek() {
        val movedBack = CacheRange(10_000_000, 30_000_000)
        val retained = sequentialRetentionWindow(movedBack, 60_000_000)
        assertEquals(CacheRange(10_000_000, 50_000_000), retained)
        assertTrue(keepCacheSpan(50_000_000, 1_000_000, retained, 100_000_000))
        assertFalse(keepCacheSpan(5_000_000, 1_000_000, retained, 100_000_000))

        // The writer's current partial fragment is not visible in SimpleCache yet.
        assertFalse(sequentialCacheHasGap(20_000_000, 60_000_000, 39_500_000))
        assertTrue(sequentialCacheHasGap(20_000_000, 60_000_000, 5_000_000))
        assertTrue(sequentialCacheHasGap(20_000_000, 60_000_000, -1))
    }
}
