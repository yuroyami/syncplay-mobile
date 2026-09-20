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
 * Dead ground along a camera notch while the keyboard is open.
 *
 * The keyboard stops short of a notch, so in landscape a strip of the room stays bare along that
 * edge, right where a thumb lands while typing. A tap there counted as a tap outside the keyboard
 * and closed it. This covers the notch columns and swallows the touch. With the keyboard closed it
 * is nothing at all, so the video keeps the whole width for its own taps.
 *
 * Only the side columns are covered: a notch on the top edge is nowhere near the keyboard.
 */
@Composable
fun KeyboardNotchShield(keyboardOpen: Boolean, cutout: WindowInsets = WindowInsets.displayCutout) {
    if (!keyboardOpen) return
    Box(Modifier.fillMaxSize()) {
        // The same tap shield as the composer: a swallowed tap cancels the room's own tap handler.
        val deadGround = Modifier.fillMaxHeight().pointerInput(Unit) { detectTapGestures { } }
        Box(deadGround.align(Alignment.CenterStart).windowInsetsStartWidth(cutout))
        Box(deadGround.align(Alignment.CenterEnd).windowInsetsEndWidth(cutout))
    }
}
