package app.player

import app.player.PlayerImpl.TrackType
import app.player.models.Track
import app.player.models.TrackChoice
import app.player.models.TrackChoices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackChoicesTest {

    private fun track(type: TrackType, index: Int, language: String?) = object : Track() {
        override val name = "track $index"
        override val type = type
        override val index = index
        override val selected = false
        override val language = language
    }

    @Test
    fun aPickFollowsTheViewerToTheNextFileByLanguageOnly() {
        val choices = TrackChoices()
        choices.remember(TrackType.AUDIO, track(TrackType.AUDIO, 2, "jpn"))
        choices.remember(TrackType.SUBTITLE, null)
        choices.remember(TrackType.VIDEO, track(TrackType.VIDEO, 0, null))

        val next = choices.forNextFile()
        assertEquals(TrackChoice.ByLanguage("jpn"), next.audio)
        assertEquals(TrackChoice.Off, next.subtitle)
        assertNull(next.video, "a track with no language cannot be found again in another file")
        assertEquals(TrackChoice.ByLanguage("jpn"), next.forNextFile().audio, "an unused wish carries on")
    }
}
