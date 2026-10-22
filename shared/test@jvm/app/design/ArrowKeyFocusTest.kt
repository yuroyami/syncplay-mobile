package app.design

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import app.uicomponents.controls.ArrowKeyFocus
import app.uicomponents.controls.ScrubTrack
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Stepper
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The desktop window seeks on the arrow keys unless a focused control uses them itself. These
 * controls must say so while they have focus, and only then.
 */
class ArrowKeyFocusTest {
    @Test
    fun aFocusedSliderOrStepperClaimsTheArrowKeysAndAButtonDoesNot() {
        var index by mutableStateOf(1)
        var value by mutableStateOf(0.5f)
        DesignHarness.drive(widthDp = 420, content = {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { first.requestFocus() }
            Column {
                SecondaryAction("Above", onClick = {}, modifier = Modifier.focusRequester(first))
                Stepper(options = listOf("One", "Two", "Three"), index = index, onIndex = { index = it })
                ScrubTrack(value = value, onValueChange = { value = it })
            }
        }) {
            assertFalse(ArrowKeyFocus.isClaimed, "a focused button leaves the arrows to the window")
            press(Key.DirectionDown)
            assertTrue(ArrowKeyFocus.isClaimed, "a focused stepper claims them")
            press(Key.DirectionDown)
            assertTrue(ArrowKeyFocus.isClaimed, "a focused slider claims them")
            press(Key.DirectionUp)
            press(Key.DirectionUp)
            assertFalse(ArrowKeyFocus.isClaimed, "the claim ends when focus leaves")
        }
        assertFalse(ArrowKeyFocus.isClaimed, "a control that leaves the screen ends its claim")
    }
}
