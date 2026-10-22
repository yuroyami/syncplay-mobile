package app.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Text
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The name check in [DesignHarness.render]: every control a screen reader reaches must have a
 * name, from a description or from its text. A role alone says what a control is, not what it
 * does, and a blank name says nothing.
 */
class ControlNamesTest {

    @Test
    fun aClickableWithOnlyARoleHasNoName() {
        val result = DesignHarness.render("names-role-only", widthDp = 120, heightDp = 80, requireNamedControls = false) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button) {})
        }
        assertEquals(1, result.unnamedControls.size, result.unnamedControls.toString())
    }

    @Test
    fun anIconButtonWithABlankNameHasNoName() {
        val result = DesignHarness.render("names-blank-icon", widthDp = 120, heightDp = 80, requireNamedControls = false) {
            GlyphButton(CloseGlyph, name = " ") {}
        }
        assertEquals(1, result.unnamedControls.size, result.unnamedControls.toString())
    }

    @Test
    fun controlsWithADescriptionOrTextAreNamed() {
        val result = DesignHarness.render("names-named", widthDp = 240, heightDp = 200) {
            Column {
                GlyphButton(CloseGlyph, name = "Close") {}
                SecondaryAction("Save", onClick = {})
                Box(Modifier.clickable(role = Role.Button) {}) { Text("Play") }
            }
        }
        assertEquals(emptyList(), result.unnamedControls)
    }
}
