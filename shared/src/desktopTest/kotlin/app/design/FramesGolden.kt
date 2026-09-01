package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.SettingsGlyph
import app.uicomponents.frames.ModalFrame
import app.uicomponents.frames.ModalSize
import app.uicomponents.frames.Notice
import app.uicomponents.frames.NoticeSeverity
import app.uicomponents.frames.PanelFrame
import app.uicomponents.frames.ScreenFrame
import kotlin.test.Test
import kotlin.test.assertTrue

/** The screen frame, the panel frame, the notices and the modal frame, drawn once each. */
class FramesGolden {

    @Composable
    private fun Rows() {
        repeat(4) { i -> ListRow(onClick = {}) { RowLabel("Row ${i + 1}"); RowValue("value") } }
    }

    @Test
    fun screenFrame() {
        val r = DesignHarness.render("screen-frame", 360, heightDp = 480) {
            ScreenFrame(title = "Settings", onBack = {}, scrolled = true, actions = { GlyphButton(SettingsGlyph, name = "Search", onClick = {}) }) {
                Column { Rows() }
            }
        }
        assertTrue(r.contentHeightDp == 480, "screen frame should fill its window, got ${r.contentHeightDp}dp")
    }

    @Test
    fun panelFrame() {
        DesignHarness.render("panel-frame", 360, heightDp = 320, overVideo = true) {
            Box(Modifier.padding(Space.gutter)) {
                PanelFrame(title = "People", modifier = Modifier.fillMaxWidth().height(280.dp), actions = { GlyphButton(SettingsGlyph, name = "More", onClick = {}) }) {
                    Rows()
                }
            }
        }
    }

    @Test
    fun notices() {
        DesignHarness.render("notices", 360, heightDp = 260, overVideo = true) {
            Column(Modifier.padding(Space.gutter)) {
                Notice("Alice paused", NoticeSeverity.Info)
                Box(Modifier.height(Space.gapTight))
                Notice("Bob joined the other room", NoticeSeverity.Quiet)
                Box(Modifier.height(Space.gapTight))
                Notice("Slowing down to match Carol", NoticeSeverity.Sync)
                Box(Modifier.height(Space.gapTight))
                Notice("Your file is different from Alice's", NoticeSeverity.Warn)
                Box(Modifier.height(Space.gapTight))
                Notice("Saved", NoticeSeverity.Info, overVideo = false)
            }
        }
    }

    @Test
    fun modalFrames() {
        DesignHarness.render("modal-ask", 360, heightDp = 400) {
            ModalFrame(ModalSize.Ask, "Leave the room?", true, {}, actions = {
                SecondaryAction("Stay", onClick = {}); AccentAction("Leave", onClick = {})
            }) { Text("Playback keeps going for everyone else.", style = Type.note, color = palette.inkDim) }
        }
        DesignHarness.render("modal-panel", 720, heightDp = 480) {
            ModalFrame(ModalSize.Panel, "Network engine", true, {}, actions = { AccentAction("Done", onClick = {}) }) { Rows() }
        }
        DesignHarness.render("modal-sheet", 360, heightDp = 640) {
            ModalFrame(ModalSize.Panel, "Network engine", true, {}, actions = { AccentAction("Done", onClick = {}) }) { Rows() }
        }
    }
}
