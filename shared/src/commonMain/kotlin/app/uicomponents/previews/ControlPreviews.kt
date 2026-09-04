package app.uicomponents.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.theme.Space
import app.theme.palette
import app.uicomponents.controls.Chevron
import app.uicomponents.controls.ChevronDirection
import app.uicomponents.controls.DestructiveAction
import app.uicomponents.controls.Field
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.GroupHeading
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.LockGlyph
import app.uicomponents.controls.PrimaryAction
import app.uicomponents.controls.ProgressBar
import app.uicomponents.controls.Rocker
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.Rule
import app.uicomponents.controls.ScrubTrack
import app.uicomponents.controls.SearchGlyph
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Segmented
import app.uicomponents.controls.SettingsGlyph
import app.uicomponents.controls.Stepper
import app.uicomponents.controls.Swatch
import app.uicomponents.controls.Tag
import app.uicomponents.controls.Tone
import app.uicomponents.controls.UnlockGlyph
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * IDE previews for the drawn control set, so a control can be looked at while it is edited.
 *
 * What actually guards these is the golden harness in desktopTest, which renders the same
 * controls headlessly and writes PNGs a change can be compared against. Previews rot in silence;
 * a golden does not.
 *
 * The room's own composables have no previews and cannot have any until the RoomViewmodel graph
 * is constructible outside the app. That is the same wall the sync tests hit before the decision
 * became a function.
 */

@Composable
private fun Sheet(content: @Composable () -> Unit) {
    Column(
        Modifier.background(palette.ground).width(360.dp).padding(vertical = Space.gap),
        content = { content() },
    )
}

@Preview
@Composable
private fun RowsPreview() = Sheet {
    GroupHeading("Rows")
    ListRow { RowLabel("Secure connection"); RowGap(); RowValue("On", accent = true, width = 36.dp); RowGap(); Rocker(on = true, onChange = {}) }
    ListRow { RowLabel("Pause when someone leaves"); RowGap(); RowValue("Off", width = 36.dp); RowGap(); Rocker(on = false, onChange = {}) }
    ListRow(enabled = false) { RowLabel("Disabled row"); RowGap(); Rocker(on = false, onChange = {}, enabled = false) }
    ListRow(onClick = {}, selected = true) { RowLabel("Selected row"); RowGap(); RowValue("value") }
    ListRow { RowLabel("Video background"); RowGap(); RowValue("#000000"); RowGap(); Swatch(Color.Black, onClick = {}) }
}

@Preview
@Composable
private fun TracksPreview() = Sheet {
    GroupHeading("Tracks")
    Column(Modifier.padding(horizontal = Space.gutter)) {
        ScrubTrack(value = 0.35f, onValueChange = {}, buffered = 0.6f, ticks = listOf(0.2f, 0.5f, 0.8f), activeTick = 0)
        ScrubTrack(value = 0.1f, onValueChange = {}, enabled = false)
        Spacer(Modifier.height(Space.gap))
        ProgressBar(progress = 0.4f)
        Spacer(Modifier.height(Space.gap))
        ProgressBar(progress = null)
    }
}

@Preview
@Composable
private fun FieldsAndChoicesPreview() = Sheet {
    var room by remember { mutableStateOf("movienight") }
    var mode by remember { mutableStateOf(0) }
    GroupHeading("Fields and choices")
    Column(Modifier.padding(horizontal = Space.gutter)) {
        Field(value = room, onValueChange = { room = it }, placeholder = "Room", leading = SearchGlyph)
        Spacer(Modifier.height(Space.gap))
        Field(value = "", onValueChange = {}, placeholder = "Password, if any")
        Spacer(Modifier.height(Space.gap))
        Segmented(listOf("Official", "Custom", "Host mine"), mode, { mode = it })
        Spacer(Modifier.height(Space.gap))
        Stepper(listOf("Netty", "Ktor"), 0, {})
    }
}

@Preview
@Composable
private fun MarksAndActionsPreview() = Sheet {
    GroupHeading("Tags, glyphs, actions")
    ListRow {
        Tag("Experimental", tone = Tone.Warn); RowGap(Space.gapTight)
        Tag("Default", tone = Tone.Accent); RowGap(Space.gapTight)
        Tag("Ready", tone = Tone.Ok, filled = true); RowGap(Space.gapTight)
        Tag("S01E02")
    }
    ListRow {
        GlyphButton(SettingsGlyph, name = "Settings", onClick = {}); RowGap(Space.gapTight)
        GlyphButton(LockGlyph, name = "Encrypted", onClick = {}); RowGap(Space.gapTight)
        GlyphButton(UnlockGlyph, name = "Not encrypted", onClick = {}); RowGap(Space.gapTight)
        GlyphButton(SearchGlyph, name = "Search, disabled", onClick = {}, enabled = false)
    }
    ListRow(onClick = {}) { RowLabel("Trusted domains"); RowGap(); RowValue("None"); RowGap(Space.gapTight); Chevron(ChevronDirection.Right) }
    Rule()
    Column(Modifier.padding(horizontal = Space.gutter)) {
        PrimaryAction("Join", onClick = {}, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.gap))
        SecondaryAction("Watch alone", onClick = {}, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.gap))
        DestructiveAction("Reset all settings", onClick = {}, modifier = Modifier.fillMaxWidth())
    }
}
