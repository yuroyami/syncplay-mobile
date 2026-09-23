package app.room.ui.misc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.i18n.strings
import app.room.roomTopInsets
import app.theme.Motion
import app.theme.Space
import app.theme.palette
import app.uicomponents.controls.ProgressBar
import app.uicomponents.controls.formatTimecode
import app.uicomponents.frames.Notice
import app.uicomponents.frames.NoticeSeverity
import kotlinx.coroutines.delay

/** The value that a vertical swipe changes. */
enum class GestureValueKind { VOLUME, BRIGHTNESS }

/** What the gesture readout shows: a level during a swipe, or where a seek will land. */
sealed interface GestureReadout {
    /**
     * [display] is the number shown, and [fraction] fills the base bar. [gain] fills the second
     * bar, from 0 to 1 across the gain range of the engine (one of the video players that the app
     * can drive, such as ExoPlayer or mpv). [gain] is null when the engine cannot amplify, and
     * then the second bar does not show.
     */
    data class Level(val kind: GestureValueKind, val display: Int, val fraction: Float, val gain: Float? = null) : GestureReadout

    /** [deltaSeconds] is the total jump of a double-tap chain, or null for a long-press preview. */
    data class Seek(val deltaSeconds: Int?, val targetMs: Long, val fraction: Float?) : GestureReadout
}

/**
 * Shows the gesture readout as a notice (a short message over the video), at the top center under
 * the status line. Pass the live value, and pass null when the gesture ends. The readout then
 * stays for 700 ms and fades out.
 *
 * The two readouts are data classes, so they compare by value. A swipe builds a new readout on
 * every pointer sample. With identity equality, every sample would count as a change. This
 * composable could then never skip, and the effect below would restart on every sample, even
 * when the value did not change.
 */
@Composable
fun RoomGestureReadout(active: GestureReadout?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<GestureReadout?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active != null) {
            shown = active
            visible = true
        } else if (shown != null) {
            delay(700)
            visible = false
        }
    }

    Box(
        modifier = modifier.windowInsetsPadding(roomTopInsets()).padding(top = Space.rowCompact + Space.gap),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.quick()) + slideInVertically(Motion.move()) { -it / 3 },
            exit = fadeOut(Motion.move()),
        ) {
            shown?.let { ReadoutNotice(it) }
        }
    }
}

@Composable
private fun ReadoutNotice(readout: GestureReadout) {
    when (readout) {
        is GestureReadout.Level -> {
            val label = if (readout.kind == GestureValueKind.VOLUME) strings.roomVolume else strings.roomBrightness
            Notice(
                text = "$label ${readout.display}%",
                severity = NoticeSeverity.Quiet,
                trailing = { LevelBars(readout.fraction, readout.gain, Modifier.width(72.dp)) },
            )
        }
        is GestureReadout.Seek -> {
            val delta = readout.deltaSeconds?.let { (if (it >= 0) "+" else "") + "$it s  " } ?: ""
            Notice(
                text = delta + formatTimecode(readout.targetMs),
                severity = NoticeSeverity.Info,
                trailing = readout.fraction?.let { f -> { ProgressBar(f, Modifier.width(72.dp)) } },
            )
        }
    }
}

/**
 * Draws the base level as a 4dp bar in the ink color. When [gain] is not null, a second 4dp bar
 * in the warning color sits under the base bar and fills as the volume goes past 100.
 */
@Composable
private fun LevelBars(fraction: Float, gain: Float?, modifier: Modifier = Modifier) {
    val p = palette
    val base by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.quick(), label = "level")
    val boost by animateFloatAsState((gain ?: 0f).coerceIn(0f, 1f), Motion.quick(), label = "gain")
    Canvas(modifier.height(if (gain != null) 11.dp else 4.dp)) {
        val r = CornerRadius(1.dp.toPx())
        val bar = 4.dp.toPx()
        drawRoundRect(p.trackOff, Offset.Zero, Size(size.width, bar), r)
        drawRoundRect(p.ink, Offset.Zero, Size(base * size.width, bar), r)
        if (gain != null) {
            val y = size.height - bar
            drawRoundRect(p.trackOff, Offset(0f, y), Size(size.width, bar), r)
            drawRoundRect(p.warn, Offset(0f, y), Size(boost * size.width, bar), r)
        }
    }
}
