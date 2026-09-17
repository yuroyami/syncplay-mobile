package app.player

import app.player.models.TrackChoice
import app.player.models.TrackChoices
import app.player.models.channelBadge
import app.player.models.codecBadge
import app.player.models.shouldShowAudioVisualization
import app.player.models.trackLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackPresentationTest {
    @Test fun regionAndLegacyLanguageCodesResolveWithoutInventingUnknownFlags() {
        assertEquals("🇺🇸", trackLanguage("eng-US").flag)
        assertEquals("🇵🇹", trackLanguage("por").flag)
        assertEquals("🇧🇷", trackLanguage("pt_BR").flag)
        assertEquals("🇩🇪", trackLanguage("ger").flag)
        assertEquals("🇹🇼", trackLanguage("zh-Hant-TW").flag)
        assertEquals("🌐", trackLanguage("und").flag)
        assertEquals("🌐", trackLanguage(null).flag)
        assertEquals("🌐", trackLanguage("ara").flag)
        assertEquals("🌐", trackLanguage("qaa").flag)
        assertEquals("🇬🇧", trackLanguage("en-u-ca-gregory").flag)
    }

    @Test fun badgesKeepActualSpeakerLayoutsDistinctFromCounts() {
        assertEquals("5.1 ch", channelBadge(6, "5.1(side)"))
        assertEquals("6 ch", channelBadge(6, null))
        assertEquals("2 ch", channelBadge(2, "stereo"))
        assertNull(channelBadge(-1, null))
        assertEquals("E-AC-3", codecBadge("eac3"))
    }

    @Test fun videoOffIsRememberedIndependentlyOfOtherTracks() {
        val choices = TrackChoices()
        choices[PlayerImpl.TrackType.AUDIO] = TrackChoice.ByIndex(3)
        choices[PlayerImpl.TrackType.SUBTITLE] = TrackChoice.ByIndex(7)
        choices.remember(PlayerImpl.TrackType.VIDEO, null)
        assertEquals(TrackChoice.Off, choices[PlayerImpl.TrackType.VIDEO])
        assertEquals(TrackChoice.ByIndex(3), choices[PlayerImpl.TrackType.AUDIO])
        assertEquals(TrackChoice.ByIndex(7), choices[PlayerImpl.TrackType.SUBTITLE])
    }

    @Test fun visualizationRequiresSoundNoSelectedVideoAndUserOptIn() {
        assertTrue(shouldShowAudioVisualization(true, true, false))
        assertFalse(shouldShowAudioVisualization(false, true, false))
        assertFalse(shouldShowAudioVisualization(true, true, true))
        assertFalse(shouldShowAudioVisualization(true, false, false))
    }
}
