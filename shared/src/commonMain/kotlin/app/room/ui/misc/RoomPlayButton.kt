package app.room.ui.misc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.palette
import app.uicomponents.controls.Icon
import app.uicomponents.controls.PauseGlyph
import app.uicomponents.controls.PlayGlyph
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import kotlin.math.ceil

/**
 * The room's one gradient moment: a 60dp key filled with the brand field. It acts on the state it
 * shows, so the key can never show pause and send play.
 *
 * Buffering is told by the key itself: the corners round out to a circle and the gradient walks
 * along the key, then settles back once the engine has caught up. Nothing appears under the key,
 * so the transport never moves while the engine fills its buffer. A thin bar used to grow under
 * it, and every buffering spell shoved the whole transport up by its height.
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

    // Translucent, like the rest of the chrome, so the picture reads through the key.
    val field = remember(p.brandField) { p.brandField.map { it.copy(alpha = 0.82f) } }
    // Half the key is a circle; the panel radius is the shape at rest.
    val corner by animateDpAsState(if (buffering) Space.hero / 2 else Radius.panel, Motion.move(), label = "corner")
    val shape = RoundedCornerShape(corner)
    val walk = rememberGradientWalk(walking = buffering)

    Box(
        modifier = modifier
            .size(Space.hero)
            .then(if (initialFocus != null) Modifier.focusRequester(initialFocus) else Modifier)
            .clip(shape)
            .drawBehind { if (size.width > 0f) drawRect(walkingBrand(field, walk.value, size.width)) }
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                viewmodel.dispatcher.controlPlayback(if (playing) Playback.PAUSE else Playback.PLAY, true)
            }
            .hoverable(source)
            .semantics {
                contentDescription = name
                if (buffering) stateDescription = bufferingLabel
            }
            // The gradient ring would vanish into the gradient fill, so the ring is ink.
            .controlStates(source, shape, focusRing = SolidColor(p.ink))
            .pressFeedback(source),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) PauseGlyph else PlayGlyph,
            contentDescription = null,
            tint = p.ground,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** One full walk of the gradient across the key. */
private const val WALK_CYCLE_MS = 1400

/**
 * The walk phase. Whole numbers are rest, where the gradient sits exactly as it does idle.
 * Walking cycles the phase from 0 to 1 for as long as it is asked to; stopping finishes the lap
 * at the same pace and comes to rest on the next whole number, so the gradient neither jumps
 * nor changes speed on the way out.
 *
 * Reduced motion never starts the walk: the key still rounds out, so the state is still shown.
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
 * The brand gradient at [phase]. Zero is the idle gradient, start to end across [width]; the
 * gradient slides forward as the phase grows and mirrors past its ends, so it wraps with no seam
 * and every whole phase lands back on the idle picture.
 */
internal fun walkingBrand(colors: List<Color>, phase: Float, width: Float): Brush {
    val shift = phase * 2f * width
    return Brush.linearGradient(
        colors = colors,
        start = Offset(shift, 0f),
        end = Offset(shift + width, 0f),
        tileMode = TileMode.Mirror,
    )
}
