package app.room.ui.misc

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.foundation.layout.windowInsetsStartWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Covers the camera notch columns at the sides of the screen while the keyboard is open, and
 * swallows taps there.
 *
 * In landscape, the keyboard stops short of a notch, so a strip of the screen stays bare along
 * that edge. A thumb lands there while typing. Without this cover, a tap on the strip counts
 * as a tap outside the keyboard and closes the keyboard. With the keyboard closed, this composable
 * adds nothing, so the video keeps the whole width for its own taps.
 *
 * Only the side columns are covered, because a notch on the top edge is far from the keyboard.
 */
@Composable
fun KeyboardNotchShield(keyboardOpen: Boolean, cutout: WindowInsets = WindowInsets.displayCutout) {
    if (!keyboardOpen) return
    Box(Modifier.fillMaxSize()) {
        // The same tap shield as the composer (the chat input row). A swallowed tap cancels the
        // tap handler of the room screen.
        val deadGround = Modifier.fillMaxHeight().pointerInput(Unit) { detectTapGestures { } }
        Box(deadGround.align(Alignment.CenterStart).windowInsetsStartWidth(cutout))
        Box(deadGround.align(Alignment.CenterEnd).windowInsetsEndWidth(cutout))
    }
}
