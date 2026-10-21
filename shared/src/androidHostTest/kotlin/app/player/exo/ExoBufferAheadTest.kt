package app.player.exo

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExoBufferAheadTest {
    @Test
    fun aPlainResponseUsesSequentialCachingAndAPartialResponseAllowsRanges() {
        assertEquals(HttpMediaInfo(100, false), httpMediaInfo(200, null, "100"))
        assertEquals(HttpMediaInfo(100, true), httpMediaInfo(206, "bytes 0-0/100", "1"))
        assertNull(httpMediaInfo(206, "bytes 10-10/100", "1"))
        assertNull(httpMediaInfo(503, null, "100"))
        assertNull(httpMediaInfo(200, null, null))
    }

    @Test
    fun aSequentialReaderPausesAndResumesTheSameInputWithoutRepeatingBytes() = runTest {
        val bytes = ByteArray(32) { it.toByte() }
        val input = ByteArrayInputStream(bytes)
        val output = ByteArrayOutputStream()
        var limit = 8L
        var position = 0L
        val reader = launch {
            readCacheStream(0, { buffer, length ->
                input.read(buffer, 0, length).also { if (it > 0) output.write(buffer, 0, it) }
            }, { limit }, { position = it })
        }
        runCurrent()
        assertEquals(8, position)
        assertEquals(24, input.available())

        // A backward seek shrinks the ahead limit without rewinding the live response.
        limit = 4
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(8, position)
        assertEquals(24, input.available())

        limit = 20
        advanceTimeBy(250)
        runCurrent()
        assertEquals(20, position)
        assertEquals(12, input.available())

        limit = Long.MAX_VALUE
        advanceTimeBy(250)
        reader.join()
        assertContentEquals(bytes, output.toByteArray())
    }

    @Test
    fun cancellingAFullAheadBufferDoesNotReadMoreData() = runTest {
        var reads = 0
        val reader = launch {
            readCacheStream(0, { _, _ -> reads++; 1 }, { 0L })
        }
        runCurrent()
        reader.cancelAndJoin()
        advanceTimeBy(1_000)
        assertEquals(0, reads)
    }

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
