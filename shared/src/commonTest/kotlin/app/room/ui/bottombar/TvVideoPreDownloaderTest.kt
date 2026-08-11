package app.room.ui.bottombar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvVideoPreDownloaderTest {

    @Test
    fun splitsEveryByteAcrossParallelRanges() {
        assertEquals(
            listOf(0L..24L, 25L..49L, 50L..74L, 75L..99L),
            splitDownloadRanges(totalBytes = 100L, requestedParts = 4),
        )
    }

    @Test
    fun distributesRemainderWithoutGaps() {
        assertEquals(
            listOf(0L..2L, 3L..5L, 6L..7L, 8L..9L),
            splitDownloadRanges(totalBytes = 10L, requestedParts = 4),
        )
    }

    @Test
    fun doesNotCreateEmptyRangesForSmallFiles() {
        assertEquals(
            listOf(0L..0L, 1L..1L),
            splitDownloadRanges(totalBytes = 2L, requestedParts = 4),
        )
    }

    @Test
    fun acceptsOnlyTheRequestedContentRange() {
        assertTrue(contentRangeMatches("bytes 25-49/100", 25L..49L, 100L))
        assertTrue(contentRangeMatches("BYTES 25-49/100", 25L..49L, 100L))
        assertFalse(contentRangeMatches("bytes 0-24/100", 25L..49L, 100L))
        assertFalse(contentRangeMatches("bytes 25-49/101", 25L..49L, 100L))
        assertFalse(contentRangeMatches(null, 25L..49L, 100L))
    }
}
