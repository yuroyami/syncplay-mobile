package app.uicomponents.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.theme.Radius
import app.theme.Space
import app.theme.palette
import kotlin.math.roundToInt

/**
 * The app's own slider: a 4dp track, the brand gradient as the played fill, a 3 x 16dp playhead
 * bar, optional tick marks and an optional buffered band. [value] is 0 to 1; hosts map it onto
 * whatever range they own. One engine or storage write on release is the host's job, through
 * [onValueChangeFinished]. Role Slider, with [describe] giving the spoken value.
 */
@Composable
fun ScrubTrack(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    ticks: List<Float> = emptyList(),
    activeTick: Int = -1,
    buffered: Float? = null,
    keyStep: Float = 0.05f,
    describe: (Float) -> String = { "${(it * 100).roundToInt()} percent" },
    name: String? = null,
    hitHeight: Dp = Space.touchMin,
) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var widthPx by remember { mutableIntStateOf(0) }
    var raw by remember { mutableFloatStateOf(value) }
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinished by rememberUpdatedState(onValueChangeFinished)

    fun fractionAt(x: Float): Float {
        if (widthPx <= 0) return 0f
        val f = (x / widthPx).coerceIn(0f, 1f)
        return if (rtl) 1f - f else f
    }

    val dragState = rememberDraggableState { delta ->
        if (widthPx > 0) {
            val d = delta / widthPx
            raw = (raw + if (rtl) -d else d).coerceIn(0f, 1f)
            latestChange(raw)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(hitHeight)
            .onSizeChanged { widthPx = it.width }
            .progressSemantics(value, 0f..1f)
            .semantics {
                stateDescription = describe(value)
                if (name != null) contentDescription = name
                setProgress { target ->
                    val v = target.coerceIn(0f, 1f)
                    latestChange(v); latestFinished?.invoke(); true
                }
            }
            .focusable(enabled, source)
            .hoverable(source, enabled)
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = when (event.key) {
                    Key.DirectionLeft -> -keyStep
                    Key.DirectionRight -> keyStep
                    else -> return@onKeyEvent false
                }
                latestChange((latestValue + step).coerceIn(0f, 1f))
                latestFinished?.invoke()
                true
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                interactionSource = source,
                startDragImmediately = false,
                onDragStarted = { start ->
                    raw = fractionAt(start.x)
                    latestChange(raw)
                },
                onDragStopped = { latestFinished?.invoke() },
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { tap ->
                    raw = fractionAt(tap.x)
                    latestChange(raw)
                    latestFinished?.invoke()
                }
            }
            .controlStates(source, Radius.controlShape, enabled = enabled),
    ) {
        val trackH = 4.dp.toPx()
        val y = size.height / 2 - trackH / 2
        val r = CornerRadius(1.dp.toPx())
        val played = value.coerceIn(0f, 1f)
        val fillW = size.width * played
        val fillLeft = if (rtl) size.width - fillW else 0f

        drawRoundRect(if (enabled) p.trackOff else p.disabled.copy(alpha = 0.12f), Offset(0f, y), Size(size.width, trackH), r)

        if (buffered != null && buffered > played) {
            val bw = size.width * (buffered.coerceIn(0f, 1f) - played)
            val bl = if (rtl) size.width - size.width * buffered.coerceIn(0f, 1f) else fillW
            drawRoundRect(p.ink.copy(alpha = 0.30f), Offset(bl, y), Size(bw, trackH), r)
        }

        if (fillW > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(if (enabled) p.brandField else listOf(p.disabled, p.disabled), 0f, size.width),
                topLeft = Offset(fillLeft, y),
                size = Size(fillW, trackH),
                cornerRadius = r,
            )
        }

        ticks.forEachIndexed { i, t ->
            val tf = t.coerceIn(0f, 1f)
            val tx = (if (rtl) 1f - tf else tf) * size.width
            val h = trackH + 8.dp.toPx()
            drawRect(
                color = if (i == activeTick) p.accent else p.rule,
                topLeft = Offset(tx.coerceAtMost(size.width - 1.dp.toPx()), y - 4.dp.toPx()),
                size = Size(1.dp.toPx(), h),
            )
        }

        val headW = 3.dp.toPx()
        val headH = 16.dp.toPx()
        val headX = (fillLeft + (if (rtl) 0f else fillW) - headW / 2).coerceIn(0f, size.width - headW)
        drawRoundRect(if (enabled) p.ink else p.disabled, Offset(headX, y + trackH / 2 - headH / 2), Size(headW, headH), r)
    }
}
