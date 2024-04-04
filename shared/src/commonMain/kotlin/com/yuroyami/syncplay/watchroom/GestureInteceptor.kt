package com.yuroyami.syncplay.watchroom

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.yuroyami.syncplay.player.PlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

lateinit var gestureCallback: GestureCallback
var dragdistance = 0f
var initialVolume = 0
var initialBrightness = 0f

@Composable
fun GestureInterceptor(
    gestureState: State<Boolean>,
    videoState: State<Boolean>,
    onSingleTap: () -> Unit
) {
    val scope = rememberCoroutineScope { Dispatchers.IO }

    val g by gestureState
    val v by videoState

    val seekLeftInteraction = remember { MutableInteractionSource() }
    val seekRightInteraction = remember { MutableInteractionSource() }

    val dimensions = LocalScreenSize.current

    //TODO: Individual gesture toggling option

    var currentBrightness by remember { mutableFloatStateOf(-1f) }
    var currentVolume by remember { mutableIntStateOf(-1) }

    var vertdragOffset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = Modifier.fillMaxSize()
        .pointerInput(g, v) {
            detectTapGestures(
                onDoubleTap = if (g && v) { offset ->
                    scope.launch {
                        if (offset.x < dimensions.wPX.times(0.25f)) {
                            PlayerUtils.seekBckwd()

                            val press = PressInteraction.Press(Offset.Zero)
                            seekLeftInteraction.emit(press)
                            delay(150)
                            seekLeftInteraction.emit(PressInteraction.Release(press))
                        }
                        if (offset.x > dimensions.wPX.times(0.85f)) {
                            PlayerUtils.seekFrwrd()

                            val press = PressInteraction.Press(Offset.Zero)
                            seekRightInteraction.emit(press)
                            delay(150)
                            seekRightInteraction.emit(PressInteraction.Release(press))
                        }
                    }
                } else null,
                onTap = { onSingleTap.invoke() },
            )
        }
        .pointerInput(g, v) {
            if (g && v) {
                detectVerticalDragGestures(
                    onDragStart = {
                        initialBrightness = gestureCallback.getCurrentBrightness()
                        initialVolume = gestureCallback.getCurrentVolume()
                    },
                    onDragEnd = {
                        dragdistance = 0F
                        currentBrightness = -1f
                        currentVolume = -1
                    },
                    onVerticalDrag = { pntr, f ->
                        dragdistance += f

                        vertdragOffset = pntr.position

                        if (pntr.position.x >= dimensions.wPX * 0.5f) {
                            /** Volume adjusting */
                            val h = dimensions.hPX / 1.5
                            val maxVolume = gestureCallback.getMaxVolume()

                            var newVolume = (initialVolume + (-dragdistance * maxVolume / h)).roundToInt()

                            if (newVolume > maxVolume) newVolume = maxVolume
                            if (newVolume < 0) newVolume = 0

                            currentVolume = newVolume //ui

                            gestureCallback.changeCurrentVolume(newVolume)
                        } else {
                            /** Brightness adjusting */
                            val h = dimensions.hPX / 1.5
                            val maxBright = gestureCallback.getMaxBrightness()
                            val newBright = (initialBrightness + (-dragdistance * maxBright / h)).toFloat()

                            currentBrightness = newBright //ui

                            gestureCallback.changeCurrentBrightness(newBright.coerceIn(0f, 1f))
                        }
                    }
                )
            }
        }
    ) {
        /* Seek animators, their only purpose is to animate a seek action */
        if (g) {
            Box(modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight().fillMaxWidth(0.1f)
                .clickable(
                    enabled = false,
                    interactionSource = seekLeftInteraction,
                    indication = rememberRipple(
                        bounded = false,
                        color = Color(100, 100, 100, 190)
                    )
                ) {}
            )

            Box(modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight().fillMaxWidth(0.1f)
                .clickable(
                    enabled = false,
                    interactionSource = seekRightInteraction,
                    indication = rememberRipple(
                        bounded = false,
                        color = Color(100, 100, 100, 190)
                    )
                ) {}
            )
            with(LocalDensity.current) {
                if (currentBrightness != -1f) {
                    Row(
                        modifier = Modifier.offset(
                            (vertdragOffset.x + 100).toDp(),
                            vertdragOffset.y.toDp()
                        ).background(Color.LightGray).clip(RoundedCornerShape(25)),
                        verticalAlignment = CenterVertically
                    ) {
                        Icon(imageVector = Icons.Filled.Brightness6, "")
                        //TODO: Delegate 'times(100).toInt()' to platform
                        Text("Brightness: ${currentBrightness.times(100).toInt()}%", color = Color.Black)
                    }
                }

                if (currentVolume != -1) {
                    Row(
                        modifier = Modifier.offset(
                            (vertdragOffset.x - 500).toDp(),
                            vertdragOffset.y.toDp()
                        ).background(Color.LightGray).clip(RoundedCornerShape(25)),
                        verticalAlignment = CenterVertically
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, "")
                        //TODO '/30' should be platform specific
                        Text("Volume: $currentVolume/30", color = Color.Black)
                    }
                }
            }
        }
    }
}