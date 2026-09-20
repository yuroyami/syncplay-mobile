package app.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.DestructiveAction
import app.uicomponents.controls.Field
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Text
import app.uicomponents.frames.ModalFrame
import app.uicomponents.frames.ModalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A modal under a remote opens on something a person can use, never on its scrim. The scrim's own
 * click is "dismiss", so focus resting there meant Center closed every dialog before anything in it
 * could be reached (issue #146).
 */
class TvModalFocusTest {

    @Test
    fun aModalWithAFieldOpensOnTheField() {
        var dismissed = false
        var fieldFocused = false
        DesignHarness.drive(widthDp = 480, content = {
            ModalFrame(
                size = ModalSize.Panel, title = "Trusted domains", dismissable = true, onDismiss = { dismissed = true },
                actions = { SecondaryAction("Cancel", onClick = {}); AccentAction("Save", onClick = {}) },
            ) {
                Text("Links from these hosts load without asking.")
                Field(value = "", onValueChange = {}, modifier = Modifier.onFocusChanged { fieldFocused = it.isFocused })
            }
        }) {
            assertTrue(fieldFocused, "entry lands on the field")
            press(Key.DirectionCenter)
            assertFalse(dismissed, "Center belongs to the field; it does not dismiss the modal")
        }
    }

    @Test
    fun aModalWithoutAFieldOpensOnItsConfirmingAction() {
        var dismissed = false
        var focused = ""
        var okPressed = false
        DesignHarness.drive(widthDp = 480, content = {
            ModalFrame(
                size = ModalSize.Ask, title = "Did ya know?", dismissable = true, onDismiss = { dismissed = true },
                actions = {
                    SecondaryAction("No more tips", onClick = {}, modifier = Modifier.onFocusChanged { if (it.isFocused) focused = "no more" })
                    SecondaryAction("Next", onClick = {}, modifier = Modifier.onFocusChanged { if (it.isFocused) focused = "next" })
                    AccentAction("OK", onClick = { okPressed = true }, modifier = Modifier.onFocusChanged { if (it.isFocused) focused = "ok" })
                },
            ) { Text("Each player has different strengths.") }
        }) {
            assertEquals("ok", focused, "entry lands on the confirming action")
            press(Key.DirectionLeft)
            assertEquals("next", focused, "Left walks the actions")
            press(Key.DirectionRight)
            press(Key.DirectionCenter)
            assertTrue(okPressed, "Center presses OK")
            assertFalse(dismissed, "and does not fall through to the scrim")
        }
    }

    @Test
    fun aDestructiveConfirmationOpensOnItsSafeChoice() {
        var dismissed = false
        var left = false
        var focused = ""
        DesignHarness.drive(widthDp = 480, content = {
            ModalFrame(
                size = ModalSize.Ask, title = "Leave the room?", dismissable = true, onDismiss = { dismissed = true },
                actions = {
                    SecondaryAction("Stay", onClick = {}, modifier = Modifier.onFocusChanged { if (it.isFocused) focused = "stay" })
                    DestructiveAction("Leave", onClick = { left = true }, modifier = Modifier.onFocusChanged { if (it.isFocused) focused = "leave" })
                },
            ) { Text("Playback stops for you.") }
        }) {
            assertTrue(focused != "leave", "a remote never lands on the destructive action (landed on '$focused')")
            press(Key.DirectionCenter)
            assertFalse(left, "Center on entry does not leave")
        }
    }

    @Test
    fun theDpadNeverClosesAModal() {
        var dismissed = false
        DesignHarness.drive(widthDp = 480, content = {
            ModalFrame(
                size = ModalSize.Panel, title = "Settings", dismissable = true, onDismiss = { dismissed = true },
                actions = { SecondaryAction("Cancel", onClick = {}); AccentAction("Save", onClick = {}) },
            ) {
                Field(value = "a", onValueChange = {})
                Field(value = "b", onValueChange = {})
            }
        }) {
            repeat(4) { press(Key.DirectionUp) }
            repeat(8) { press(Key.DirectionDown) }
            repeat(4) { press(Key.DirectionLeft) }
            repeat(4) { press(Key.DirectionRight) }
            assertFalse(dismissed, "no direction reaches the scrim's dismiss")
        }
    }

    @Test
    fun offATelevisionUnderTouchNothingIsFocusedOnEntry() {
        var fieldFocused = false
        DesignHarness.drive(widthDp = 480, television = false, content = {
            ModalFrame(size = ModalSize.Panel, title = "Trusted domains", dismissable = true, onDismiss = {}, actions = null) {
                Field(value = "", onValueChange = {}, modifier = Modifier.onFocusChanged { fieldFocused = it.isFocused })
            }
        }) {
            assertFalse(fieldFocused, "touch users get no keyboard on arrival")
        }
    }
}
