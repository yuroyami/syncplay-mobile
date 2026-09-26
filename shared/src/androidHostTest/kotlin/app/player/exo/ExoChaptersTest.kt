package app.player.exo

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.Label
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import app.player.models.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.media3.extractor.metadata.Chapter as MediaChapter

@OptIn(UnstableApi::class)
class ExoChaptersTest {
    private fun chapter(startMs: Long, title: String?, hidden: Boolean = false): MediaChapter =
        MediaChapter.Builder()
            .setStartTimeMs(startMs)
            .setEndTimeMs(startMs + 60_000)
            .setHidden(hidden)
            .setTitle(title?.let { Label(null, it) })
            .build()

    private fun format(vararg chapters: MediaChapter): Format =
        Format.Builder().setMetadata(Metadata(*chapters)).build()

    @Test
    fun chaptersComeInTimeOrderWithTheirTitles() {
        val chapters = chaptersOf(listOf(format(chapter(60_000, "Middle"), chapter(0, "Opening"))))
        assertEquals(listOf(Chapter(0, "Opening", 0), Chapter(1, "Middle", 60_000)), chapters)
    }

    @Test
    fun theSameListOnTwoTracksCountsOnce() {
        val list = arrayOf(chapter(0, "Opening"), chapter(60_000, "Middle"))
        assertEquals(2, chaptersOf(listOf(format(*list), format(*list))).size)
    }

    @Test
    fun hiddenChaptersStayOutAndUntitledOnesGetANumber() {
        val chapters = chaptersOf(listOf(format(chapter(0, null), chapter(30_000, "Secret", hidden = true), chapter(60_000, " "))))
        assertEquals(listOf(Chapter(0, "Chapter 1", 0), Chapter(1, "Chapter 2", 60_000)), chapters)
    }

    @Test
    fun aFileWithoutChaptersHasNone() {
        assertEquals(emptyList(), chaptersOf(listOf(Format.Builder().build(), format())))
    }
}
