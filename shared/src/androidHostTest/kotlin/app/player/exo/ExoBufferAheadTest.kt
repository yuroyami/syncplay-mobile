package app.player.exo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExoBufferAheadTest {
    @Test
    fun chunksCoverTheBudgetWithoutOverlap() {
        val ranges = cacheRanges(20, chunkSize = 8)
        assertEquals(listOf(CacheRange(0, 8), CacheRange(8, 8), CacheRange(16, 4)), ranges)
    }

    @Test
    fun anEmptyResourceHasNoChunks() {
        assertEquals(emptyList(), cacheRanges(0))
    }

    @Test
    fun probeNeedsAnExactSingleBytePartialResponse() {
        assertEquals(100, contentRangeTotal("bytes 0-0/100"))
        assertNull(contentRangeTotal("bytes 0-99/100"))
        assertNull(contentRangeTotal("bytes */100"))
        assertNull(contentRangeTotal("bytes 0-0/*"))
    }

    @Test
    fun everyChunkMustMatchTheRequestedRange() {
        assertTrue(matchesContentRange("bytes 8-15/100", 8, 8))
        assertFalse(matchesContentRange("bytes 0-7/100", 8, 8))
        assertFalse(matchesContentRange("bytes 8-14/100", 8, 8))
        assertFalse(matchesContentRange("bytes 8-15/15", 8, 8))
    }
}
