package app.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.player.PlayerImpl.TrackType
import app.player.models.Track
import app.player.models.VisualizerControls
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.room.ui.rightcards.TrackControls
import app.room.ui.rightcards.VisualizerRows
import app.uicomponents.frames.PanelFrame
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The visualizer rows of the tracks card. The switch reads what is on screen, so a selected
 * video track shows it off whatever the stored value, and the director and pattern rows
 * appear only while something is drawn.
 */
class TracksVisualizerTest {
    private class FakeControls : VisualizerControls {
        override val drawings = listOf("Menger", "Shatter", "A long drawing name that has to shrink")
        override var showing by mutableStateOf(2)
        override fun show(index: Int) { showing = index }
        override var directed by mutableStateOf(true)
    }

    private fun video(chosen: Boolean) = object : Track() {
        override val index = 0
        override val name = "1080p"
        override val type = TrackType.VIDEO
        override val selected = chosen
        override val language = "und"
        override val trait = null
        override val channelCount = null
        override val channelLayout = null
        override val codec = "H.264"
    }

    private fun texts(tracks: List<Track>, on: Boolean, controls: VisualizerControls?, name: String): Pair<List<String>, DesignHarness.Result> {
        val result = DesignHarness.render(name, 280, heightDp = 360, overVideo = true) {
            PanelFrame("Tracks", Modifier.fillMaxSize(), scrollable = false) {
                Column { VisualizerRows(tracks, on, {}, controls) }
            }
        }
        return result.textLayouts.map { it.layoutInput.text.text } to result
    }

    @Test
    fun theRowsShowOnlyWhileTheVisualizerDraws() {
        val controls = FakeControls()

        val (withVideo, _) = texts(listOf(video(true)), on = true, controls, "tracks-viz-video-on")
        assertTrue("Audio visualizer" in withVideo, "the switch row is always there: $withVideo")
        assertFalse("Director" in withVideo, "a selected video track draws nothing, so no director row: $withVideo")

        val (drawing, result) = texts(listOf(video(false)), on = true, controls, "tracks-viz-drawing")
        assertTrue("Director" in drawing && "Pattern" in drawing, "director and pattern rows while drawing: $drawing")
        assertTrue(controls.drawings[2] in drawing, "the stepper names the drawing on screen: $drawing")
        assertFalse(drawing.any { "Draws the sound" in it || "Changes the pattern" in it },
            "a note waits for a long press, as on every settings row: $drawing")
        val label = result.textLayouts.first { it.layoutInput.text.text == "Pattern" }
        assertTrue(label.lineCount == 1, "the pattern label stays on one line beside its stepper")
        result.assertAllTextFits()

        val (off, _) = texts(listOf(video(false)), on = false, controls, "tracks-viz-off")
        assertFalse("Director" in off, "the switch off hides the rows: $off")
    }

    /**
     * A phone holds the card at about 236dp, and a large font makes it shorter still. The rows
     * scroll with the track list, so the stepper and the tracks are a swipe away. As a fixed
     * header they pushed both out of the card.
     */
    @Test
    fun aShortCardScrollsToTheStepperAndTheTracks() {
        val controls = FakeControls()
        val heightDp = 200
        DesignHarness.drive(280, heightDp = heightDp, television = false, content = {
            PanelFrame("Tracks", Modifier.fillMaxSize(), scrollable = false) {
                TrackControls(
                    tracks = listOf(video(false)), supportsVideo = true, supportsVisualization = true,
                    visualization = true, onVisualization = {}, onChoose = { _, _ -> }, onImport = {}, onSearch = {},
                    initialType = TrackType.VIDEO, visualizer = controls,
                )
            }
        }) {
            val edge = heightDp * 2f
            fun text(wanted: String) = DesignHarness.onUiThread {
                scene.semanticsOwners.firstNotNullOfOrNull { findText(it.unmergedRootSemanticsNode, wanted) }
            }
            val before = text("Disable Video")
            assertTrue(before == null || before.boundsInWindow.bottom > edge, "the track list starts under the card's edge")
            val list = DesignHarness.onUiThread {
                scene.semanticsOwners.firstNotNullOf { findAction(it.unmergedRootSemanticsNode) }
            }
            DesignHarness.onUiThread { list.config[SemanticsActions.ScrollBy].action?.invoke(0f, 10_000f) }
            frames(10)
            for (wanted in listOf(controls.drawings[2], "Disable Video", "1080p")) {
                val node = assertNotNull(text(wanted), "$wanted is in the list after the scroll")
                assertTrue(node.boundsInWindow.bottom <= edge + 1f && node.boundsInWindow.top >= 0f,
                    "$wanted sits inside the card after the scroll: ${node.boundsInWindow}")
            }
        }
    }

    private fun findText(node: SemanticsNode, wanted: String): SemanticsNode? {
        if (node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == wanted } == true) return node
        return node.children.firstNotNullOfOrNull { findText(it, wanted) }
    }

    private fun findAction(node: SemanticsNode): SemanticsNode? {
        if (node.config.getOrNull(SemanticsActions.ScrollBy) != null) return node
        return node.children.firstNotNullOfOrNull { findAction(it) }
    }
}
