package app.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.theme.Space
import app.uicomponents.controls.Chevron
import app.uicomponents.controls.ChevronDirection
import app.uicomponents.controls.DestructiveAction
import app.uicomponents.controls.Field
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.GroupHeading
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.PrimaryAction
import app.uicomponents.controls.ProgressBar
import app.uicomponents.controls.Rocker
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.Rule
import app.uicomponents.controls.ScrubTrack
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Segmented
import app.uicomponents.controls.SheetHandle
import app.uicomponents.controls.Stepper
import app.uicomponents.controls.Swatch
import app.uicomponents.controls.Tag
import app.uicomponents.controls.Tone
import app.uicomponents.controls.SearchGlyph
import app.uicomponents.controls.SettingsGlyph
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every FOUNDATION control on one sheet, so a change to any of them is seen at once. */
class ControlsGolden {

    @Composable
    private fun Sheet() {
        Column(Modifier.fillMaxWidth()) {
            GroupHeading("Rows")
            ListRow { RowLabel("Secure connection (TLS)"); RowGap(); RowValue("On", accent = true, width = 36.dp); RowGap(); Rocker(on = true, onChange = {}) }
            ListRow { RowLabel("Pause when someone leaves"); RowGap(); RowValue("Off", width = 36.dp); RowGap(); Rocker(on = false, onChange = {}) }
            ListRow(enabled = false) { RowLabel("Chapter marks clickable"); RowGap(); RowValue("Off", width = 36.dp); RowGap(); Rocker(on = false, onChange = {}, enabled = false) }
            ListRow { RowLabel("Network engine"); RowGap(); Stepper(listOf("Netty", "Ktor"), 0, {}) }
            ListRow(onClick = {}) { RowLabel("Trusted domains"); RowGap(); RowValue("None"); RowGap(Space.gapTight); Chevron(ChevronDirection.Right) }
            ListRow(onClick = {}, selected = true) { RowLabel("Selected row"); RowGap(); RowValue("value") }
            ListRow { RowLabel("Video background"); RowGap(); RowValue("#000000"); RowGap(); Swatch(Color.Black, onClick = {}) }
            Rule()
            GroupHeading("Tracks")
            Column(Modifier.padding(horizontal = Space.gutter)) {
                ScrubTrack(value = 0.35f, onValueChange = {}, buffered = 0.6f, ticks = listOf(0.2f, 0.5f, 0.8f), activeTick = 0)
                ScrubTrack(value = 0.1f, onValueChange = {}, enabled = false)
                Spacer(Modifier.height(Space.gap))
                ProgressBar(progress = 0.4f)
                Spacer(Modifier.height(Space.gap))
                ProgressBar(progress = null)
            }
            Rule()
            GroupHeading("Fields and choices")
            Column(Modifier.padding(horizontal = Space.gutter)) {
                Field(value = "movienight", onValueChange = {}, placeholder = "Room", leading = SearchGlyph)
                Spacer(Modifier.height(Space.gap))
                Field(value = "", onValueChange = {}, placeholder = "Password, if any")
                Spacer(Modifier.height(Space.gap))
                Segmented(listOf("Official", "Custom"), 0, {})
                Spacer(Modifier.height(Space.gap))
                Segmented(listOf("8995", "8996", "8997", "8998"), 2, {}, enabled = true)
            }
            Rule()
            GroupHeading("Tags, glyphs, actions")
            ListRow {
                Tag("Experimental", tone = Tone.Warn); RowGap(Space.gapTight)
                Tag("Default", tone = Tone.Accent); RowGap(Space.gapTight)
                Tag("Ready", tone = Tone.Ok, filled = true); RowGap(Space.gapTight)
                Tag("S01E02")
            }
            ListRow {
                GlyphButton(SettingsGlyph, name = "Settings", onClick = {}); RowGap(Space.gapTight)
                GlyphButton(SearchGlyph, name = "Search", onClick = {}); RowGap(Space.gapTight)
                GlyphButton(SettingsGlyph, name = "Settings, disabled", onClick = {}, enabled = false)
            }
            Column(Modifier.padding(horizontal = Space.gutter)) {
                PrimaryAction("Join", onClick = {}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.gap))
                SecondaryAction("Watch alone", onClick = {}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.gap))
                DestructiveAction("Reset all settings", onClick = {}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.gap))
                SheetHandle()
            }
        }
    }

    @Test
    fun controlsSheet() {
        val phone = DesignHarness.render("controls", 360) { Sheet() }
        DesignHarness.render("controls", 360, fontScale = 1.3f) { Sheet() }
        DesignHarness.render("controls", 720) { Sheet() }
        DesignHarness.render("controls", 360, theme = DesignHarness.lightTheme) { Sheet() }
        DesignHarness.render("controls", 360, overVideo = true) { Sheet() }
        assertTrue(phone.contentHeightDp in 990..1060, "controls sheet height ${phone.contentHeightDp}dp moved outside its budget")
    }
}
