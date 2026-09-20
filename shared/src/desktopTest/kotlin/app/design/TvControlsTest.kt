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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import app.preferences.settings.ColorSliders
import app.uicomponents.controls.ScrubTrack
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Stepper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a remote does with the controls that hold a value rather than fire an action. */
class TvControlsTest {

    /**
     * The stepper steps on Left and Right. Its two arrows are tap targets, not focus stops: a
     * remote landing on an arrow saw no focus ring and pressed a direction that did two things.
     */
    @Test
    fun aStepperTakesFocusItselfAndStepsOnLeftAndRight() {
        var index by mutableStateOf(1)
        var focused = ""
        DesignHarness.drive(widthDp = 420, content = {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { first.requestFocus() }
            Column {
                SecondaryAction("Above", onClick = {}, modifier = Modifier.focusRequester(first).onFocusChanged { if (it.isFocused) focused = "above" })
                Stepper(
                    options = listOf("TonalSpot", "Vibrant", "Expressive"),
                    index = index,
                    onIndex = { index = it },
                    modifier = Modifier.onFocusChanged { if (it.isFocused) focused = "stepper" },
                )
            }
        }) {
            assertEquals("above", focused)
            press(Key.DirectionDown)
            assertEquals("stepper", focused, "Down lands on the stepper itself, never on an arrow")
            press(Key.DirectionRight)
            assertEquals(2, index, "Right steps forward")
            press(Key.DirectionLeft)
            assertEquals(1, index, "Left steps back")
        }
    }

    /**
     * A track that carries a long press (the seek bar's chapter list) gives it to the press key
     * too. A remote has no long press, so without this the chapters were reachable by finger only.
     */
    @Test
    fun theTrackGivesItsLongPressToThePressKey() {
        var held = 0
        var value by mutableStateOf(0.5f)
        DesignHarness.drive(widthDp = 420, content = {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { first.requestFocus() }
            ScrubTrack(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.focusRequester(first),
                onLongPress = { held++ },
            )
        }) {
            press(Key.DirectionCenter)
            assertEquals(1, held, "Center opens what a long press opens")
            press(Key.Enter)
            assertEquals(2, held, "so does Enter, for a keyboard")
            press(Key.DirectionRight)
            assertEquals(2, held, "a direction still moves the value")
            assertTrue(value > 0.5f, "Right stepped forward: $value")
        }
    }

    /**
     * The colour picker answers a finger or a mouse only, so a television gets sliders. Each one
     * takes focus in turn and moves its part of the colour with Left and Right.
     */
    @Test
    fun colorSlidersMoveTheColourWithARemote() {
        var colour by mutableStateOf(Color(0xFF9879EF))
        var reached = 0
        DesignHarness.drive(widthDp = 420, content = {
            ColorSliders(colour, onColor = { colour = it }, modifier = Modifier.onFocusChanged { if (it.hasFocus) reached++ })
        }) {
            press(Key.DirectionDown)
            val start = colour
            press(Key.DirectionRight)
            press(Key.DirectionRight)
            assertTrue(colour != start, "a slider moved the colour: $start -> $colour")
            assertTrue(reached > 0, "the sliders take focus")
        }
    }
}
