package app.room.ui.misc

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.waterfall
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import app.uicomponents.controls.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.player.VolumeLadder
import app.preferences.Preferences.DOUBLETAP_SEEK
import app.preferences.Preferences.GESTURES
import app.preferences.Preferences.SEEK_BACKWARD_JUMP
import app.preferences.Preferences.SEEK_FORWARD_JUMP
import app.preferences.Preferences.SWIPE_GESTURES
import app.preferences.watchPref
import app.theme.Motion
import app.theme.palette
import app.uicomponents.controls.Chevron
import app.uicomponents.controls.ChevronDirection
import app.uicomponents.controls.Feedback
import app.utils.platformCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CHAIN_WINDOW_MS = 900L
private const val LONG_PRESS_MS = 600L
private const val LONG_PRESS_STEP_MS = 200L

/** The mark at a double tap: the tap point, the seek direction, and an id that restarts the fade. */
private class SeekMark(val at: Offset, val forward: Boolean, val id: Int)

/**
 * The touch layer over the video. A double tap seeks, a long press seeks in steps, and a vertical
 * swipe changes brightness on the left half and volume on the right half.
 *
 * The layer is always composed, but its pointer handlers attach only while the HUD (the controls
 * over the video) is hidden. While the HUD shows, every touch reaches the controls.
 * Double taps less than 900 ms apart add up to one seek and one announcement. A long press moves
 * a preview of the landing point and seeks once, on release.
 */
@Composable
fun RoomGestureInterceptor(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val p = palette
    val gesturesEnabled by GESTURES.watchPref()
    val doubletapEnabled by DOUBLETAP_SEEK.watchPref()
    val swipeEnabled by SWIPE_GESTURES.watchPref()
    val forwardJump by SEEK_FORWARD_JUMP.watchPref()
    val backwardJump by SEEK_BACKWARD_JUMP.watchPref()
    val hasVideo by viewmodel.hasVideo.collectAsState()
    val isHUDVisible by viewmodel.uiState.visibleHUD.collectAsState()
    val durationMs by viewmodel.playerManager.timeFullMillis.collectAsState()
    val media by viewmodel.playerManager.media.collectAsState()
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestForwardJump by rememberUpdatedState(forwardJump)
    val latestBackwardJump by rememberUpdatedState(backwardJump)

    /* The system edge guards are read through rememberUpdatedState. The pointer handlers below
     * live long and would otherwise keep the values from an old composition. A rotation does not
     * restart the handlers, because the activity handles configChanges itself. */
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val topGestureGuardPx by rememberUpdatedState(maxOf(WindowInsets.systemGestures.getTop(density), WindowInsets.displayCutout.getTop(density)))
    val bottomGestureGuardPx by rememberUpdatedState(WindowInsets.systemGestures.getBottom(density))
    val leftGestureGuardPx by rememberUpdatedState(WindowInsets.waterfall.getLeft(density, layoutDirection))
    val rightGestureGuardPx by rememberUpdatedState(WindowInsets.waterfall.getRight(density, layoutDirection))
    val edgeGuardFraction = 0.08f

    var readout by remember(media?.location) { mutableStateOf<GestureReadout?>(null) }

    // The double-tap chain: the first tap saves the origin, and the chain seeks once when it ends.
    var chainSteps by remember(media?.location) { mutableIntStateOf(0) }
    var chainOriginMs by remember(media?.location) { mutableLongStateOf(0L) }
    var chainVersion by remember(media?.location) { mutableIntStateOf(0) }
    var seekMark by remember(media?.location) { mutableStateOf<SeekMark?>(null) }
    var markId by remember { mutableIntStateOf(0) }

    // The long-press preview: the landing point moves while the finger stays down.
    var previewMs by remember(media?.location) { mutableStateOf<Long?>(null) }
    var pressOriginMs by remember(media?.location) { mutableLongStateOf(0L) }

    // The wash is a tint with an icon over the half of the screen that a swipe changes. It shows
    // on the first drag, and again after a gesture preference changes.
    var washSeen by remember(gesturesEnabled, swipeEnabled) { mutableStateOf(false) }
    var wash by remember { mutableStateOf<GestureValueKind?>(null) }
    var washKind by remember { mutableStateOf(GestureValueKind.VOLUME) }

    var initialBrightness by remember { mutableFloatStateOf(0f) }
    var initialVolume by remember { mutableIntStateOf(0) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var lastAppliedBrightness by remember { mutableFloatStateOf(0f) }
    var lastAppliedVolume by remember { mutableIntStateOf(0) }

    fun chainDeltaSeconds(steps: Int) = if (steps >= 0) steps * latestForwardJump else steps * latestBackwardJump
    fun fractionOf(ms: Long): Float? = if (latestDurationMs > 0L) (ms.toFloat() / latestDurationMs).coerceIn(0f, 1f) else null
    fun clampToMedia(ms: Long): Long = if (latestDurationMs > 0L) ms.coerceIn(0L, latestDurationMs) else ms.coerceAtLeast(0L)

    /* A chain makes one seek and one announcement, through the seek path of the dispatcher. The
     * saved origin is then used only once, and the room never gets four seeks for four taps. */
    LaunchedEffect(media?.location, chainVersion) {
        if (chainSteps == 0) return@LaunchedEffect
        delay(CHAIN_WINDOW_MS)
        if (media == null || viewmodel.playerManager.media.value !== media) return@LaunchedEffect
        val target = clampToMedia(chainOriginMs + chainDeltaSeconds(chainSteps) * 1000L)
        chainSteps = 0
        viewmodel.dispatcher.seek(target, fromMs = chainOriginMs)
        Feedback.medium()
        readout = null
    }
    LaunchedEffect(seekMark?.id) {
        if (seekMark != null) {
            delay(400)
            seekMark = null
        }
    }

    Box(modifier) {
        val softwareKB = LocalSoftwareKeyboardController.current
        val seekGestures = gesturesEnabled && doubletapEnabled && hasVideo

        Box(
            content = {},
            modifier = Modifier.fillMaxSize().then(
                if (!isHUDVisible) {
                    Modifier
                        .pointerInput(seekGestures, media?.location) {
                            detectTapGestures(
                                onPress = { offset ->
                                    if (!seekGestures) return@detectTapGestures
                                    val pressedMedia = viewmodel.playerManager.media.value
                                        ?: return@detectTapGestures
                                    coroutineScope {
                                        val forward = offset.x > size.width * 0.5f
                                        // The preview belongs to this press. If the pointer is
                                        // cancelled or the media changes, the preview job stops too.
                                        val job = launch {
                                            delay(LONG_PRESS_MS)
                                            Feedback.light()
                                            pressOriginMs = viewmodel.player.currentPositionMs()
                                            var target = pressOriginMs
                                            val step = (if (forward) latestForwardJump else -latestBackwardJump) * 1000L
                                            while (isActive) {
                                                target = clampToMedia(target + step)
                                                previewMs = target
                                                readout = GestureReadout.Seek(null, target, fractionOf(target))
                                                delay(LONG_PRESS_STEP_MS)
                                            }
                                        }
                                        try {
                                            val released = tryAwaitRelease()
                                            job.cancel()
                                            val landing = previewMs
                                            if (released && landing != null &&
                                                viewmodel.playerManager.media.value === pressedMedia
                                            ) {
                                                viewmodel.dispatcher.seek(landing, fromMs = pressOriginMs)
                                                Feedback.medium()
                                            }
                                        } finally {
                                            job.cancel()
                                            if (previewMs != null) readout = null
                                            previewMs = null
                                        }
                                    }
                                },
                                onDoubleTap = if (seekGestures) {
                                    { offset ->
                                        val forward = offset.x > size.width * 0.5f
                                        if (chainSteps == 0) chainOriginMs = viewmodel.player.currentPositionMs()
                                        chainSteps += if (forward) 1 else -1
                                        chainVersion++
                                        Feedback.tick()
                                        val delta = chainDeltaSeconds(chainSteps)
                                        val target = clampToMedia(chainOriginMs + delta * 1000L)
                                        readout = GestureReadout.Seek(delta, target, fractionOf(target))
                                        seekMark = SeekMark(offset, forward, markId++)
                                    }
                                } else null,
                                onTap = {
                                    // The HUD is hidden. A tap shows it and hides the keyboard.
                                    viewmodel.uiState.showHud()
                                    Feedback.tick()
                                    softwareKB?.hide()
                                },
                            )
                        }
                        .pointerInput(gesturesEnabled, swipeEnabled, hasVideo) {
                            if (!(gesturesEnabled && swipeEnabled && hasVideo)) return@pointerInput
                            detectVerticalDragGestures(
                                onDragStart = { startOffset ->
                                    /* A drag that starts in a system gesture zone or on a waterfall
                                     * edge (a curved screen side) is ignored until it ends. The size
                                     * values come from the pointer scope, so they stay correct
                                     * after a rotation. */
                                    val guardTop = maxOf(size.height * edgeGuardFraction, topGestureGuardPx.toFloat())
                                    val guardBottom = maxOf(size.height * edgeGuardFraction, bottomGestureGuardPx.toFloat())
                                    if (startOffset.y < guardTop || startOffset.y > size.height - guardBottom ||
                                        startOffset.x < leftGestureGuardPx || startOffset.x > size.width - rightGestureGuardPx
                                    ) {
                                        initialBrightness = -1f
                                        return@detectVerticalDragGestures
                                    }
                                    // Where the platform cannot set brightness, the left half
                                    // does nothing. It shows no readout for a change that
                                    // cannot happen.
                                    val brightnessSide = startOffset.x < size.width * 0.5f
                                    if (brightnessSide && !platformCallback.supportsBrightness) {
                                        initialBrightness = -1f
                                        return@detectVerticalDragGestures
                                    }
                                    initialBrightness = platformCallback.getCurrentBrightness()
                                    initialVolume = viewmodel.player.volume.current()
                                    lastAppliedBrightness = initialBrightness
                                    lastAppliedVolume = initialVolume
                                    dragDistance = 0f
                                    if (!washSeen) {
                                        washSeen = true
                                        washKind = if (startOffset.x >= size.width * 0.5f) GestureValueKind.VOLUME else GestureValueKind.BRIGHTNESS
                                        wash = washKind
                                    }
                                },
                                onDragEnd = {
                                    dragDistance = 0f
                                    readout = null
                                    wash = null
                                },
                                onVerticalDrag = { pointer, delta ->
                                    if (initialBrightness < 0f) return@detectVerticalDragGestures
                                    dragDistance += delta
                                    val height = size.height / 2f
                                    if (pointer.position.x >= size.width * 0.5f) {
                                        /* A swipe over half the screen height moves the volume by
                                         * 100. When the engine has a gain range, a longer swipe
                                         * goes on into the gain range at the same rate. */
                                        val ladder = viewmodel.player.volume.ladder
                                        val newVolume = (initialVolume + (-dragDistance * VolumeLadder.BASE_MAX / height)).roundToInt().coerceIn(0, ladder.max)
                                        val gainSpan = (ladder.gainMax - VolumeLadder.BASE_MAX).coerceAtLeast(1)
                                        readout = GestureReadout.Level(
                                            kind = GestureValueKind.VOLUME,
                                            display = newVolume,
                                            fraction = newVolume.coerceAtMost(VolumeLadder.BASE_MAX) / VolumeLadder.BASE_MAX.toFloat(),
                                            gain = if (ladder.hasGain) (newVolume - VolumeLadder.BASE_MAX).coerceAtLeast(0) / gainSpan.toFloat() else null,
                                        )
                                        if (newVolume != lastAppliedVolume) {
                                            viewmodel.player.volume.set(newVolume)
                                            Feedback.tick()
                                            lastAppliedVolume = newVolume
                                        }
                                    } else {
                                        val maxBright = platformCallback.getMaxBrightness()
                                        var newBright = initialBrightness + (-dragDistance * maxBright / height)
                                        if (newBright > 0.1f) newBright = (newBright / 0.05f).roundToInt() * 0.05f
                                        newBright = newBright.coerceIn(0f, 1f)
                                        readout = GestureReadout.Level(GestureValueKind.BRIGHTNESS, (newBright * 100f).roundToInt(), newBright)
                                        if (abs(newBright - lastAppliedBrightness) >= 0.025f) {
                                            platformCallback.changeCurrentBrightness(newBright)
                                            Feedback.tick()
                                            lastAppliedBrightness = newBright
                                        }
                                    }
                                    // The wash is only a hint, so it hides when the readout shows.
                                    wash = null
                                },
                            )
                        }
                } else Modifier
            ),
        )

        /* The wash checks the HUD itself. This interceptor is always composed, so without the
         * check, a wash could draw under the visible HUD. */
        val washAlpha by animateFloatAsState(if (wash != null && !isHUDVisible) 1f else 0f, Motion.quick(), label = "wash")
        if (washAlpha > 0f) {
            val onRight = washKind == GestureValueKind.VOLUME
            Box(
                modifier = Modifier
                    .align(if (onRight) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .alpha(washAlpha)
                    .background(
                        Brush.horizontalGradient(
                            if (onRight) listOf(Color.Transparent, p.ground.copy(alpha = 0.18f))
                            else listOf(p.ground.copy(alpha = 0.18f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (onRight) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Brightness6,
                    contentDescription = null,
                    tint = p.ink.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        // Two accent chevrons at the tap point show the seek direction, and fade out over 400 ms.
        seekMark?.let { mark ->
            key(mark.id) {
                val fade = remember { Animatable(1f) }
                LaunchedEffect(Unit) { fade.animateTo(0f, tween(400, easing = Motion.easing)) }
                val direction = if (mark.forward) ChevronDirection.Right else ChevronDirection.Left
                Row(
                    modifier = Modifier
                        .offset { IntOffset((mark.at.x - 18.dp.toPx()).roundToInt(), (mark.at.y - 9.dp.toPx()).roundToInt()) }
                        .alpha(fade.value),
                ) {
                    Chevron(direction, color = p.accent, size = 18.dp)
                    Chevron(direction, color = p.accent, size = 18.dp)
                }
            }
        }

        RoomGestureReadout(active = readout, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())
    }
}
