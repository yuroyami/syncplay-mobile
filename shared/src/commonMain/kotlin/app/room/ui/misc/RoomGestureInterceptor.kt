package app.room.ui.misc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.waterfall
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.preferences.Preferences.DOUBLETAP_SEEK
import app.preferences.Preferences.GESTURES
import app.preferences.Preferences.SWIPE_GESTURES
import app.preferences.watchPref
import app.utils.platformCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_brightness
import syncplaymobile.shared.generated.resources.room_volume
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun RoomGestureInterceptor(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val scope = rememberCoroutineScope()
    val gesturesEnabled by GESTURES.watchPref()
    val doubletapEnabled by DOUBLETAP_SEEK.watchPref()
    val swipeEnabled by SWIPE_GESTURES.watchPref()
    val hasVideo by viewmodel.hasVideo.collectAsState()
    val isHUDVisible by viewmodel.uiState.visibleHUD.collectAsState()

    val seekLeftInteraction = remember { MutableInteractionSource() }
    val seekRightInteraction = remember { MutableInteractionSource() }

    /* Real system edge-guard sizes (quick-settings pull zone, nav-bar pull zone, waterfall
     * screen edges, camera cutout). Wrapped in rememberUpdatedState because the pointerInput
     * coroutines below are long-lived and would otherwise keep stale composition-time
     * captures after a rotation (the activity handles configChanges itself, so nothing
     * recreates them). Reading .value inside the handlers always yields current values. */
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val topGestureGuardPx by rememberUpdatedState(
        maxOf(
            WindowInsets.systemGestures.getTop(density),
            WindowInsets.displayCutout.getTop(density)
        )
    )
    val bottomGestureGuardPx by rememberUpdatedState(WindowInsets.systemGestures.getBottom(density))
    val leftGestureGuardPx by rememberUpdatedState(WindowInsets.waterfall.getLeft(density, layoutDirection))
    val rightGestureGuardPx by rememberUpdatedState(WindowInsets.waterfall.getRight(density, layoutDirection))

    var currentBrightness by remember { mutableFloatStateOf(-1f) }
    var currentVolume by remember { mutableIntStateOf(-1) }
    var vertdragOffset by remember { mutableStateOf(Offset.Zero) }

    // Track initial and last values for drag gestures
    var initialBrightness by remember { mutableFloatStateOf(0f) }
    var initialVolume by remember { mutableIntStateOf(0) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var lastAppliedBrightness by remember { mutableFloatStateOf(0f) }
    var lastAppliedVolume by remember { mutableIntStateOf(0) }

    Box(modifier) {
        var fastForward by remember { mutableStateOf(false) }
        var fastRewind by remember { mutableStateOf(false) }

        if (gesturesEnabled && doubletapEnabled && !isHUDVisible) {
            /** Seek back - visual-feedback left section */
            Box(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.1f)
                    .clickable(
                        enabled = false,
                        interactionSource = seekLeftInteraction,
                        indication = ripple(bounded = false, color = Color(100, 100, 100, 190)),
                        onClick = {}
                    )
            ) {
                AnimatedVisibility(
                    visible = fastRewind,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(64.dp)
                    )
                }
            }

            /** Seek forward - visual-feedback right section */
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.1f)
                    .clickable(
                        enabled = false,
                        interactionSource = seekRightInteraction,
                        indication = ripple(bounded = false, color = Color(100, 100, 100, 190)),
                        onClick = {}
                    )
            ) {
                AnimatedVisibility(
                    visible = fastForward,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(64.dp)
                    )
                }
            }
        }

        /* Gesture-detection box, layered ON TOP of the HUD (see RoomScreenUI).
         *
         * HUD VISIBLE: no pointerInput modifiers attached, so touches fall through to the HUD
         * beneath (chat input, buttons, scrollable lists).
         * HUD HIDDEN: tap/drag handlers attached. They intercept touches that would otherwise hit
         * the still-alive but invisible HUD elements (preventing ghost clicks) and route them to
         * volume/brightness/seek/show-HUD.
         *
         * Edge gating: vertical drags starting inside the system gesture zones (quick-settings
         * pull at the top, nav-bar pull at the bottom, waterfall side edges) are ignored so
         * system gestures aren't swallowed by the volume/brightness handler. Uses the real
         * inset values reported by the OS, with an 8%-of-height floor as a fallback for
         * devices/platforms that report none.
         */
        val haptic = LocalHapticFeedback.current
        val softwareKB = LocalSoftwareKeyboardController.current
        val edgeGuardFraction = 0.08f // minimum guard when reported gesture insets are smaller/absent

        Box(
            content = {},
            modifier = Modifier.fillMaxSize().then(
                if (!isHUDVisible) {
                    Modifier
                        .pointerInput(gesturesEnabled, doubletapEnabled, hasVideo) {
                            detectTapGestures(
                                onPress = { offset ->
                                    if (gesturesEnabled && doubletapEnabled && hasVideo && offset.x > size.width * 0.5f) {
                                        val press = PressInteraction.Press(offset)
                                        val job = scope.launch {
                                            delay(1000)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            delay(1000)

                                            fastForward = true
                                            seekRightInteraction.emit(press)
                                            while (isActive) {
                                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                viewmodel.dispatcher.seekFrwrd()
                                                seekRightInteraction.emit(press)
                                                delay(200)
                                                seekRightInteraction.emit(PressInteraction.Release(press))
                                            }
                                        }
                                        tryAwaitRelease()
                                        job.cancel()
                                        fastForward = false
                                        seekRightInteraction.emit(PressInteraction.Release(press))
                                    }
                                    if (gesturesEnabled && doubletapEnabled && hasVideo && offset.x < size.width * 0.5f) {
                                        val press = PressInteraction.Press(offset)
                                        val job = scope.launch {
                                            delay(1000)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            delay(1000)
                                            fastRewind = true
                                            seekLeftInteraction.emit(press)
                                            while (isActive) {
                                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                viewmodel.dispatcher.seekBckwd()
                                                seekLeftInteraction.emit(press)
                                                delay(200)
                                                seekLeftInteraction.emit(PressInteraction.Release(press))
                                            }
                                        }
                                        tryAwaitRelease()
                                        job.cancel()
                                        fastRewind = false
                                        seekLeftInteraction.emit(PressInteraction.Release(press))
                                    }
                                },
                                onDoubleTap = if (gesturesEnabled && doubletapEnabled && hasVideo) {
                                    { offset ->
                                        scope.launch {
                                            if (offset.x < size.width * 0.5f) {
                                                viewmodel.dispatcher.seekBckwd()
                                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

                                                val press = PressInteraction.Press(Offset.Zero)
                                                seekLeftInteraction.emit(press)
                                                delay(200)
                                                seekLeftInteraction.emit(PressInteraction.Release(press))
                                            }
                                            if (offset.x > size.width * 0.5f) {
                                                viewmodel.dispatcher.seekFrwrd()
                                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

                                                val press = PressInteraction.Press(Offset.Zero)
                                                seekRightInteraction.emit(press)
                                                delay(150)
                                                seekRightInteraction.emit(PressInteraction.Release(press))
                                            }
                                        }
                                    }
                                } else null,
                                onTap = {
                                    /* HUD is currently hidden — single tap reveals it. */
                                    viewmodel.uiState.visibleHUD.value = true
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    softwareKB?.hide()
                                },
                            )
                        }.pointerInput(gesturesEnabled, swipeEnabled, hasVideo) {
                            if (gesturesEnabled && swipeEnabled && hasVideo) {
                                detectVerticalDragGestures(
                                    onDragStart = { startOffset ->
                                        // Edge guard: ignore drags originating inside system gesture zones
                                        // or on waterfall edges. size.* (not captured screen dims) so the
                                        // values stay correct after an in-place rotation.
                                        val guardTop = maxOf(size.height * edgeGuardFraction, topGestureGuardPx.toFloat())
                                        val guardBottom = maxOf(size.height * edgeGuardFraction, bottomGestureGuardPx.toFloat())
                                        if (startOffset.y < guardTop ||
                                            startOffset.y > size.height - guardBottom ||
                                            startOffset.x < leftGestureGuardPx ||
                                            startOffset.x > size.width - rightGestureGuardPx
                                        ) {
                                            initialBrightness = -1f // sentinel: drag is ignored
                                            return@detectVerticalDragGestures
                                        }

                                        initialBrightness = platformCallback.getCurrentBrightness()
                                        initialVolume = viewmodel.player.getCurrentVolume()
                                        lastAppliedBrightness = initialBrightness
                                        lastAppliedVolume = initialVolume
                                        dragDistance = 0f
                                    },
                                    onDragEnd = {
                                        dragDistance = 0f
                                        currentBrightness = -1f
                                        currentVolume = -1
                                    },
                                    onVerticalDrag = { pntr, f ->
                                        if (initialBrightness < 0f) return@detectVerticalDragGestures // edge-ignored drag
                                        dragDistance += f
                                        vertdragOffset = pntr.position

                                        if (pntr.position.x >= size.width * 0.5f) {
                                            // Volume adjusting
                                            val height = size.height / 2f
                                            val maxVolume = viewmodel.player.getMaxVolume()
                                            var newVolume = (initialVolume + (-dragDistance * maxVolume / height)).roundToInt()
                                            newVolume = newVolume.coerceIn(0, maxVolume)

                                            currentVolume = newVolume

                                            if (newVolume != lastAppliedVolume) {
                                                viewmodel.player.changeCurrentVolume(newVolume)
                                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                lastAppliedVolume = newVolume
                                            }
                                        } else {
                                            // Brightness adjusting
                                            val height = size.height / 2f
                                            val maxBright = platformCallback.getMaxBrightness()
                                            var newBright = initialBrightness + (-dragDistance * maxBright / height)

                                            // Snap to 5% increments above 10%
                                            if (newBright > 0.1f) {
                                                newBright = (newBright / 0.05f).roundToInt() * 0.05f
                                            }
                                            newBright = newBright.coerceIn(0f, 1f)

                                            currentBrightness = newBright

                                            // Only apply if changed significantly (avoid tiny fluctuations)
                                            if (abs(newBright - lastAppliedBrightness) >= 0.025f) {
                                                platformCallback.changeCurrentBrightness(newBright)
                                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                lastAppliedBrightness = newBright
                                            }
                                        }
                                    }
                                )
                            }
                        }
                } else Modifier
            )
        )

        with(LocalDensity.current) {
            // Over-video chrome: fixed dark scrim + white content, independent of the app theme,
            // so the bubbles stay readable over any video frame.
            if (currentBrightness != -1f) {
                Row(
                    modifier = Modifier
                        .offset((vertdragOffset.x - 100).toDp(), vertdragOffset.y.toDp())
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Brightness6,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    val brightness = stringResource(
                        Res.string.room_brightness,
                        "${currentBrightness.times(100).toInt()}%"
                    )
                    Text(brightness, color = Color.White)
                }
            }

            if (currentVolume != -1) {
                Row(
                    modifier = Modifier
                        .offset((vertdragOffset.x + 100).toDp(), vertdragOffset.y.toDp())
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = CenterVertically
                ) {
                    val maxVolume = remember { viewmodel.player.getMaxVolume() }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    val volume = stringResource(Res.string.room_volume, "$currentVolume/$maxVolume")
                    Text(volume, color = Color.White)
                }
            }
        }
    }
}