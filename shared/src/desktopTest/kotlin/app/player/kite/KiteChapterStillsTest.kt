package app.player.kite

import app.player.models.Chapter
import app.player.models.MediaFile
import app.player.models.MediaFileLocation
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Real stills through KitePlayer's FFmpeg, from a six-second 160 by 90 test pattern. */
class KiteChapterStillsTest {
    private val pattern = File("src/desktopTest/resources/media/test-pattern-160x90.mp4")
    private fun media() = MediaFile(location = MediaFileLocation.Local(PlatformFile(pattern)))

    @Test
    fun aChapterGetsAFrameFromTheFile() = runBlocking {
        val still = assertNotNull(KiteChapterStills.still(media(), Chapter(0, "Opening", 0), 6_000))
        assertTrue(still.width == 160 && still.height == 90, "a small source keeps its size: ${still.width}x${still.height}")
    }

    @Test
    fun theStillsOfOneFileAreKept() = runBlocking {
        val media = media()
        val first = KiteChapterStills.still(media, Chapter(0, "Opening", 0), 6_000)
        assertSame(first, KiteChapterStills.still(media, Chapter(0, "Opening", 0), 6_000))
    }

    @Test
    fun aFileThatCannotBeOpenedHasNoStill() = runBlocking {
        val missing = MediaFile(location = MediaFileLocation.Local(PlatformFile(File("src/desktopTest/resources/media/missing.mp4"))))
        assertNull(KiteChapterStills.still(missing, Chapter(0, "Opening", 0), 6_000))
    }
}
