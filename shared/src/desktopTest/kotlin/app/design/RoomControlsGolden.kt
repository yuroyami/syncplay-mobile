package app.design

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.player.PlayerImpl.TrackType
import app.player.models.Track
import app.player.models.TrackTrait
import app.room.ui.rightcards.TrackControls
import app.room.ui.rightcards.SeekControls
import app.room.ui.tabs.ManagedRoomTabs
import app.room.ui.chat.chatMediaCellSize
import app.uicomponents.frames.PanelFrame
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomControlsGolden {
    private fun sample(type: TrackType, language: String, label: String, chosen: Boolean = false, purpose: TrackTrait? = null) = object : Track() {
        override val index = 0
        override val name = label
        override val type = type
        override val selected = chosen
        override val language = language
        override val trait = purpose
        override val channelCount = if (type == TrackType.AUDIO) 6 else null
        override val channelLayout = if (type == TrackType.AUDIO) "5.1" else null
        override val codec = if (type == TrackType.AUDIO) "AAC" else null
    }
    private val tracks = listOf(
        sample(TrackType.AUDIO, "eng", "Original", true),
        sample(TrackType.AUDIO, "fra", "French dub"),
        sample(TrackType.AUDIO, "ara", "Commentary", purpose = TrackTrait.ACCESSIBILITY),
        sample(TrackType.SUBTITLE, "eng", "English", true),
        sample(TrackType.SUBTITLE, "jpn", "Japanese", purpose = TrackTrait.FORCED),
        sample(TrackType.SUBTITLE, "und", "Signs"),
        sample(TrackType.VIDEO, "und", "1080p", true),
    )

    @Test fun tracksFitNarrowLandscapeDocksWithLargeText() {
        for (width in listOf(240, 280, 340)) for (scale in listOf(1f, 1.3f)) for (type in TrackType.entries) {
            DesignHarness.render("tracks-${type.name.lowercase()}", width, heightDp = 300, fontScale = scale, overVideo = true) {
                PanelFrame("Tracks", Modifier.fillMaxSize(), scrollable = false) {
                    TrackControls(tracks, true, true, true, {}, { _, _ -> }, {}, {}, initialType = type)
                }
            }.assertAllTextFits()
        }
    }

    @Test fun seekAndManagedTabsFitWithoutTrimmingTheirLabels() {
        for (width in listOf(240, 280, 340)) for (scale in listOf(1f, 1.3f)) {
            DesignHarness.render("seek-controls", width, heightDp = 240, fontScale = scale, overVideo = true) {
                SeekControls("00:12:34", {}, true, {}, "Skip 01:30", {}, false, {})
            }.assertAllTextFits()
        }
        for (language in listOf("en", "fr", "de", "ar")) {
            DesignHarness.render("managed-room-tabs", 420, heightDp = 100, fontScale = 1.3f, language = language) {
                ManagedRoomTabs(true) {}
            }.assertAllTextFits()
        }
    }

    @Test fun fourCellsDetermineTheSameChatImageSize() {
        assertEquals(65.dp, chatMediaCellSize(280.dp))
        assertEquals(95.dp, chatMediaCellSize(400.dp))
    }
}
