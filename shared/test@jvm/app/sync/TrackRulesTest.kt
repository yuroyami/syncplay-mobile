package app.sync

import app.player.PlayerImpl.TrackType
import app.player.models.Track
import app.preferences.Preferences.AUDIO_LANG
import app.preferences.set
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** The language rule runs when a file opens, with no panel open, and a pick follows by language. */
class TrackRulesTest {

    private fun audio(index: Int, language: String) = object : Track() {
        override val name = language
        override val type = TrackType.AUDIO
        override val index = index
        override val selected = false
        override val language = language
    }

    @Test
    fun thePreferredLanguageAppliesOnOpenAndAPickCarriesByLanguage() = withSoloRoom { viewmodel, player ->
        runBlocking { AUDIO_LANG.set("eng") }
        try {
            player.fileTracks = listOf(audio(0, "jpn"), audio(1, "eng"))
            runBlocking { player.injectVideoURL(TwoClientRoom.CLIP) }
            waitFor("the preferred language") { player.picks.toList() == listOf(TrackType.AUDIO to 1) }

            // The viewer picks Japanese. The next file lists its tracks in another order.
            runBlocking { player.selectTrack(player.fileTracks[0], TrackType.AUDIO) }
            player.picks.clear()
            player.fileTracks = listOf(audio(0, "eng"), audio(1, "jpn"))
            runBlocking { player.injectVideoURL("https://example.com/episode-2.mp4") }
            waitFor("the carried pick") { player.picks.isNotEmpty() }
            Thread.sleep(200)
            assertEquals(listOf(TrackType.AUDIO to 1), player.picks.toList())
            assertEquals("episode-2.mp4", viewmodel.media?.fileName)
        } finally {
            runBlocking { AUDIO_LANG.set(AUDIO_LANG.default) }
        }
    }
}
