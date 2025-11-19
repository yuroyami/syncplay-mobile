package com.yuroyami.syncplay.ui.screens.room.misc

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.managers.preferences.Preferences.GESTURES
import com.yuroyami.syncplay.managers.preferences.watchPref
import com.yuroyami.syncplay.ui.components.screenHeightPx
import com.yuroyami.syncplay.ui.components.screenWidthPx
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.utils.platformCallback
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
    val hasVideo by viewmodel.hasVideo.collectAsState()

    val seekLeftInteraction = remember { MutableInteractionSource() }
    val seekRightInteraction = remember { MutableInteractionSource() }

    val h = screenHeightPx
    val w = screenWidthPx

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

        if (gesturesEnabled) {
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

        /** Actual gesture-detection logic box */
        val haptic = LocalHapticFeedback.current
        val softwareKB = LocalSoftwareKeyboardController.current

        Box(
            content = {},
            modifier = Modifier.fillMaxSize().pointerInput(gesturesEnabled, hasVideo) {
                detectTapGestures(
                    onPress = { offset ->
                        if (gesturesEnabled && hasVideo && offset.x > w.times(0.65f)) {
                            val press = PressInteraction.Press(offset)
                            val job = scope.launch {
                                delay(1000)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                delay(1000)

                                fastForward = true
                                seekRightInteraction.emit(press)
                                while (isActive) {
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    viewmodel.actionManager.seekFrwrd()
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
                        if (gesturesEnabled && hasVideo && offset.x < w.times(0.35f)) {
                            val press = PressInteraction.Press(offset)
                            val job = scope.launch {
                                delay(1000)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                delay(1000)
                                fastRewind = true
                                seekLeftInteraction.emit(press)
                                while (isActive) {
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    viewmodel.actionManager.seekBckwd()
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
                    onDoubleTap = if (gesturesEnabled && hasVideo) {
                        { offset ->
                            scope.launch {
                                if (offset.x < w.times(0.35f)) {
                                    viewmodel.actionManager.seekBckwd()
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

                                    val press = PressInteraction.Press(Offset.Zero)
                                    seekLeftInteraction.emit(press)
                                    delay(200)
                                    seekLeftInteraction.emit(PressInteraction.Release(press))
                                }
                                if (offset.x > w.times(0.65f)) {
                                    viewmodel.actionManager.seekFrwrd()
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
                        viewmodel.uiManager.visibleHUD.value = !viewmodel.uiManager.visibleHUD.value
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        if (!viewmodel.uiManager.visibleHUD.value) softwareKB?.hide()
                    },
                )
            }.pointerInput(gesturesEnabled, hasVideo) {
                if (gesturesEnabled && hasVideo) {
                    detectVerticalDragGestures(
                        onDragStart = {
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
                            dragDistance += f
                            vertdragOffset = pntr.position

                            if (pntr.position.x >= w * 0.5f) {
                                // Volume adjusting
                                val height = h / 2f
                                val maxVolume = viewmodel.player.getMaxVolume()
                                var newVolume = (initialVolume + (-dragDistance * maxVolume / height)).roundToInt()
                                newVolume = newVolume.coerceIn(0, maxVolume)

                                currentVolume = newVolume

                                // Only apply if changed
                                if (newVolume != lastAppliedVolume) {
                                    viewmodel.player.changeCurrentVolume(newVolume)
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    lastAppliedVolume = newVolume
                                }
                            } else {
                                // Brightness adjusting
                                val height = h / 2f
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
        )

        with(LocalDensity.current) {
            if (currentBrightness != -1f) {
                Row(
                    modifier = Modifier
                        .offset((vertdragOffset.x - 100).toDp(), vertdragOffset.y.toDp())
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color.LightGray)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Brightness6,
                        contentDescription = "",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    val brightness = stringResource(
                        Res.string.room_brightness,
                        "${currentBrightness.times(100).toInt()}%"
                    )
                    Text(brightness, color = Color.Black)
                }
            }

            if (currentVolume != -1) {
                Row(
                    modifier = Modifier
                        .offset((vertdragOffset.x + 100).toDp(), vertdragOffset.y.toDp())
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color.LightGray)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = CenterVertically
                ) {
                    val maxVolume = remember { viewmodel.player.getMaxVolume() }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    val volume = stringResource(Res.string.room_volume, "$currentVolume/$maxVolume")
                    Text(volume, color = Color.Black)
                }
            }
        }
    }
}