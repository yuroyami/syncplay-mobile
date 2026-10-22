package app.room

import app.room.ui.rightcards.compactRosterFileName
import app.room.ui.rightcards.abbreviateRosterFileName
import app.utils.FileComparison
import app.utils.mediaExs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RosterFileNameTest {
    @Test
    fun `the release group and extension leave the title and episode`() {
        assertEquals(
            "Dandandan S01 - Episode 03",
            compactRosterFileName("[Anime Time] Dandandan S01 - Episode 03.mkv"),
        )
    }

    @Test
    fun `adjacent repeated and nested leading groups are removed`() {
        assertEquals("Title 03", compactRosterFileName("  [Group [Team]][1080p]  Title 03.MKV  "))
    }

    @Test
    fun `all recognized video and audio extensions are removed case insensitively`() {
        mediaExs.forEach { extension ->
            assertEquals("Episode.03", compactRosterFileName("Episode.03.${extension.uppercase()}"))
        }
    }

    @Test
    fun `dotted titles and unrecognized suffixes stay intact`() {
        assertEquals("Show.S01.E03", compactRosterFileName("Show.S01.E03"))
        assertEquals("Dr.Strange.S01E03", compactRosterFileName("Dr.Strange.S01E03.mp4"))
        assertEquals("Title.custom", compactRosterFileName("[Group] Title.custom"))
    }

    @Test
    fun `brackets inside the title and unmatched groups survive`() {
        assertEquals("Title [Part 2] [1080p]", compactRosterFileName("[Group] Title [Part 2] [1080p].mkv"))
        assertEquals("[Group Title", compactRosterFileName("[Group Title.mkv"))
        assertEquals("[Group [Team] Title", compactRosterFileName("[Group [Team] Title.mkv"))
    }

    @Test
    fun `the only remaining title is never stripped away`() {
        assertEquals("[Title]", compactRosterFileName("[Title].mkv"))
        assertEquals("[Title]", compactRosterFileName("[Group] [Title].mkv"))
        assertEquals(".mkv", compactRosterFileName(".mkv"))
        assertEquals(FileComparison.PRIVACY_HIDDENFILENAME, compactRosterFileName(FileComparison.PRIVACY_HIDDENFILENAME))
        assertEquals("123456abcdef", compactRosterFileName("123456abcdef"))
    }

    @Test
    fun `unicode titles stay whole and are not shortened to a fixed length`() {
        val title = "ダンダダン 🎬 – Épisode 03 – الجزء الأخير"
        assertEquals(title, compactRosterFileName("[Subs] $title.mkv"))
    }

    @Test
    fun `empty and whitespace inputs stay empty`() {
        assertEquals("", compactRosterFileName(""))
        assertEquals("", compactRosterFileName("  \t  "))
    }

    @Test
    fun `middle abbreviation preserves the example title and episode ending`() {
        val title = compactRosterFileName("[Anime Time] Dandandan S01 - Episode 03.mkv")
        assertEquals("Dandan…sode 03", abbreviateRosterFileName(title) { it.length <= 14 })
    }

    @Test
    fun `a fitting title stays whole and a wider budget restores more characters`() {
        val title = "Dandandan S01 - Episode 03"
        assertEquals(title, abbreviateRosterFileName(title) { it.length <= title.length })
        for (budget in 3 until title.length) {
            val shortened = abbreviateRosterFileName(title) { it.length <= budget }
            assertEquals(budget, shortened.length, "Use all available characters for budget $budget")
            val parts = shortened.split('…')
            assertTrue(title.startsWith(parts[0]) && title.endsWith(parts[1]))
            assertTrue(parts[1].length in parts[0].length..parts[0].length + 1)
        }
    }

    @Test
    fun `tiny widths get an ellipsis or no text without overflowing`() {
        assertEquals("…", abbreviateRosterFileName("Episode 03") { it.length <= 1 })
        assertEquals("…", abbreviateRosterFileName("Episode 03") { it.length <= 2 })
        assertEquals("", abbreviateRosterFileName("Episode 03") { it.isEmpty() })
        assertEquals("", abbreviateRosterFileName("") { it.isEmpty() })
    }

    @Test
    fun `emoji at both ends survive without splitting surrogate pairs`() {
        val title = "🎬Series🔥 - Episode 03🚀"
        assertEquals("🎬…🚀", abbreviateRosterFileName(title) { it.length <= 5 })
        for (budget in 1..title.length) {
            val shortened = abbreviateRosterFileName(title) { it.length <= budget }
            assertTrue(shortened.length <= budget)
            shortened.forEachIndexed { index, character ->
                if (character in '\uD800'..'\uDBFF') {
                    assertTrue(index + 1 < shortened.length && shortened[index + 1] in '\uDC00'..'\uDFFF', shortened)
                }
                if (character in '\uDC00'..'\uDFFF') {
                    assertTrue(index > 0 && shortened[index - 1] in '\uD800'..'\uDBFF', shortened)
                }
            }
        }
    }

    @Test
    fun `measured glyph width rather than character count controls abbreviation`() {
        val title = "WWWiiiWWWiii03"
        fun width(text: String) = text.sumOf { if (it == 'W') 4 else 1 }
        val shortened = abbreviateRosterFileName(title) { width(it) <= 13 }
        assertEquals("WW…i03", shortened)
        assertTrue(width(shortened) <= 13)
    }
}
