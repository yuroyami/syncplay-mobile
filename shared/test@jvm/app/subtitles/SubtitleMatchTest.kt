package app.subtitles

import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** What a subtitle search takes from the file that plays: the show, the episode and the hash. */
class SubtitleMatchTest {

    @Test
    fun anEpisodeNameGivesTheShowAndItsNumbers() {
        assertEquals(SearchTerms("The Show", Episode(2, 11)), searchTermsFor("The.Show.S02E11.1080p.WEB.x264.mkv"))
        assertEquals(SearchTerms("Big Buck Bunny 2008", null), searchTermsFor("Big.Buck.Bunny.2008.1080p.mkv"))
        assertEquals(Episode(1, 3), episodeOf("show season1episode3.mp4"))
        assertNull(episodeOf("Big.Buck.Bunny.2008.mkv"))
    }

    @Test
    fun theHashMatchesTheReferenceAlgorithm() {
        // The same bytes through the reference implementation that OpenSubtitles publishes.
        val data = ByteArray(200_000) { ((it * 7 + 3) % 256).toByte() }
        val dir = createTempDirectory("synkplay-hash").toFile()
        try {
            val file = File(dir, "clip.mkv").apply { writeBytes(data) }
            val ends = runBlocking { PlatformFile(file.path).readEnds() }!!
            assertEquals(200_000L, ends.size)
            assertEquals("60a0df1f5fa2cd40", openSubtitlesHash(ends))
        } finally {
            dir.deleteRecursively()
        }
    }
}
