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
import app.room.roomTopInsets
import app.theme.Motion
import app.theme.Space
import app.theme.palette
import app.uicomponents.controls.ProgressBar
import app.uicomponents.controls.formatTimecode
import app.uicomponents.frames.Notice
import app.uicomponents.frames.NoticeSeverity
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_brightness
import syncplaymobile.shared.generated.resources.room_volume

/** Which knob a swipe is moving. */
enum class GestureValueKind { VOLUME, BRIGHTNESS }

/** What the gesture readout shows: a level while swiping, or where a seek will land. */
sealed interface GestureReadout {
    /** [display] is the number shown; [normalMark] is where 100 percent sits on a boosting engine. */
    class Level(val kind: GestureValueKind, val display: Int, val fraction: Float, val normalMark: Float = 1f) : GestureReadout

    /** [deltaSeconds] is the accumulated double-tap chain, null for a long press preview. */
    class Seek(val deltaSeconds: Int?, val targetMs: Long, val fraction: Float?) : GestureReadout
}

/**
 * The gesture readout, in the notice channel's own shape and place: below the status line, on the
 * chrome tier. Feed it the live value and null when the gesture ends; it lingers, then fades.
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
            val label = stringResource(if (readout.kind == GestureValueKind.VOLUME) Res.string.room_volume else Res.string.room_brightness)
            Notice(
                text = "$label ${readout.display}%",
                severity = NoticeSeverity.Quiet,
                trailing = { LevelBar(readout.fraction, readout.normalMark, Modifier.width(72.dp)) },
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

/** A 4dp level bar; past the 100 percent mark the fill turns to the warning colour. */
@Composable
private fun LevelBar(fraction: Float, normalMark: Float, modifier: Modifier = Modifier) {
    val p = palette
    val fill by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.quick(), label = "level")
    Canvas(modifier.height(4.dp)) {
        val r = CornerRadius(1.dp.toPx())
        drawRoundRect(p.trackOff, Offset.Zero, Size(size.width, size.height), r)
        val normalW = size.width * normalMark.coerceIn(0f, 1f)
        drawRoundRect(p.accent, Offset.Zero, Size(minOf(fill, normalMark) * size.width, size.height), r)
        if (fill > normalMark) drawRect(p.warn, Offset(normalW, 0f), Size((fill - normalMark) * size.width, size.height))
        if (normalMark < 1f) drawRect(p.ink, Offset(normalW, 0f), Size(1.dp.toPx(), size.height))
    }
}
