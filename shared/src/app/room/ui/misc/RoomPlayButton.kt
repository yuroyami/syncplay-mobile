package app.room.ui.misc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.i18n.strings
import app.player.Playback
import app.room.LocalRoomInitialFocus
import app.theme.BrandSpan
import app.theme.Motion
import app.theme.Space
import app.theme.palette
import app.uicomponents.controls.Icon
import app.uicomponents.controls.PauseGlyph
import app.uicomponents.controls.PlayGlyph
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import app.uicomponents.controls.waitingShape
import kotlin.math.ceil
import kotlin.math.max

/**
 * The play button: a 60dp key filled with its slice of the brand ramp (`brandField`), drawn like
 * every brand shape ([app.theme.brandBrush]). The button acts on the state that it shows, so it can
 * never show pause and send play.
 *
 * The button itself shows buffering. It rounds out to a circle and the ramp drifts through it,
 * then both come back to rest after the engine (one of the video players that the app can drive,
 * such as ExoPlayer or mpv) has caught up. Nothing may appear under the button, because an element
 * there would push the transport keys up each time the engine buffers.
 */
@Composable
fun RoomPlayButton(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val hasVideo by viewmodel.hasVideo.collectAsState()
    val playing by viewmodel.playerManager.isNowPlaying.collectAsState()
    val buffering by viewmodel.playerManager.isBuffering.collectAsState()
    if (!hasVideo) return

    val p = palette
    val source = remember { MutableInteractionSource() }
    val name = if (playing) strings.roomPause else strings.roomPlay
    val bufferingLabel = strings.roomBuffering
    val initialFocus = LocalRoomInitialFocus.current

    // Translucent like the other room controls, so the video shows through the button.
    val field = remember(p.brandField) { p.brandField.map { it.copy(alpha = 0.9f) } }
    // Square at rest and round while it buffers, like every key that waits.
    val wait by animateFloatAsState(if (buffering) 1f else 0f, Motion.move(), label = "buffering")
    val shape = waitingShape(wait)
    val walk = rememberGradientWalk(walking = buffering)

    Box(
        modifier = modifier
            .size(Space.hero)
            .then(if (initialFocus != null) Modifier.focusRequester(initialFocus) else Modifier)
            .clip(shape)
            .drawBehind { if (size.width > 0f) drawRect(walkingBrand(field, walk.value, size.width, BrandSpan.toPx())) }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                viewmodel.dispatcher.controlPlayback(if (playing) Playback.PAUSE else Playback.PLAY, true)
            }
            .hoverable(source)
            .semantics {
                contentDescription = name
                if (buffering) stateDescription = bufferingLabel
            }
            .controlStates(source, shape, focusRing = p.brandInk, filled = true)
            .pressFeedback(source),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) PauseGlyph else PlayGlyph,
            contentDescription = null,
            tint = p.brandInk,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** The time of one full walk of the ramp through the button, there and back. */
private const val WALK_CYCLE_MS = 1400

/**
 * Returns the walk phase of the ramp. At a whole number, the ramp sits exactly where it sits at
 * rest. While [walking] is true, the phase cycles from 0 to 1. When [walking] turns false, the
 * phase finishes the cycle at the same pace and stops on the next whole number. The ramp then
 * neither jumps nor changes speed on the way out.
 *
 * With reduced motion, the walk never starts. The button still rounds out, so the buffering state
 * still shows.
 */
@Composable
private fun rememberGradientWalk(walking: Boolean): State<Float> {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(walking) {
        if (walking && !Motion.reduced) {
            phase.animateTo(1f, infiniteRepeatable(tween(WALK_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart))
        } else {
            val rest = ceil(phase.value)
            val remainingMs = ((rest - phase.value) * WALK_CYCLE_MS).toInt()
            phase.animateTo(rest, tween(if (Motion.reduced) 0 else remainingMs, easing = LinearEasing))
            phase.snapTo(0f)
        }
    }
    return phase.asState()
}

/**
 * Returns the brand ramp at [phase], seen through a shape [width] pixels wide. At phase 0 it is
 * the rest picture of [app.theme.brandBrush]: the middle of a ramp [minSpan] pixels wide, or as
 * wide as the shape. As the phase grows, the ramp slides past the shape and mirrors at its ends.
 * So the ramp wraps with no seam, and every whole phase looks the same as phase 0.
 */
internal fun walkingBrand(colors: List<Color>, phase: Float, width: Float, minSpan: Float): Brush {
    val span = max(width, minSpan)
    val start = (width - span) / 2f - phase * 2f * span
    return Brush.linearGradient(
        colors = colors,
        start = Offset(start, 0f),
        end = Offset(start + span, 0f),
        tileMode = TileMode.Mirror,
    )
}
