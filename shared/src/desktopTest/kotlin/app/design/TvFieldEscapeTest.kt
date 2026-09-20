package app.design

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import app.uicomponents.controls.Field
import app.uicomponents.controls.SecondaryAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * On a television the D-pad must leave a text field. Before this, Down stayed in the first field of
 * the join form for ever and everything below it was out of reach, the "invisible wall" of issue
 * #146. Off a television the arrows stay the caret's, as a keyboard expects.
 */
class TvFieldEscapeTest {

    private class Form {
        var focused = ""
        val first = FocusRequester()
    }

    @Composable
    private fun Form.content() {
        LaunchedEffect(Unit) { first.requestFocus() }
        Column {
            Field(value = "user", onValueChange = {}, focusRequester = first, modifier = Modifier.onFocusChanged { if (it.isFocused) focused = "username" })
            Field(value = "room", onValueChange = {}, modifier = Modifier.onFocusChanged { if (it.isFocused) focused = "room" })
            SecondaryAction("Join", onClick = {}, modifier = Modifier.onFocusChanged { if (it.isFocused) focused = "join" })
        }
    }

    @Test
    fun downLeavesAFieldForWhateverIsBelowItOnATelevision() {
        val form = Form()
        DesignHarness.drive(widthDp = 360, television = true, content = { form.content() }) {
            assertEquals("username", form.focused, "the first field takes focus on entry")
            press(Key.DirectionDown)
            assertEquals("room", form.focused, "Down leaves the username field")
            press(Key.DirectionDown)
            assertEquals("join", form.focused, "Down leaves the room field for the action under it")
            press(Key.DirectionUp)
            assertEquals("room", form.focused, "Up comes back into the field")
        }
    }

    @Test
    fun centerOnAFieldStaysInIt() {
        val form = Form()
        DesignHarness.drive(widthDp = 360, television = true, content = { form.content() }) {
            press(Key.DirectionCenter)
            assertEquals("username", form.focused, "Center opens the keyboard for this field; it moves nothing")
        }
    }

    @Test
    fun aHardwareKeyboardTypesIntoAnIdleField() {
        var text = "user"
        var focused = false
        val first = FocusRequester()
        DesignHarness.drive(widthDp = 360, content = {
            LaunchedEffect(Unit) { first.requestFocus() }
            Field(value = text, onValueChange = { text = it }, focusRequester = first, modifier = Modifier.onFocusChanged { focused = it.isFocused })
        }) {
            assertTrue(focused)
            press(Key.X, typed = 'x')
            assertEquals("userx", text, "a printable key starts editing and lands in the field")
        }
    }

    @Test
    fun offATelevisionTheArrowsStayInTheField() {
        val form = Form()
        DesignHarness.drive(widthDp = 360, television = false, content = { form.content() }) {
            assertEquals("username", form.focused)
            press(Key.DirectionDown)
            assertEquals("username", form.focused, "a keyboard's Down is the caret's, not the form's")
        }
    }
}
