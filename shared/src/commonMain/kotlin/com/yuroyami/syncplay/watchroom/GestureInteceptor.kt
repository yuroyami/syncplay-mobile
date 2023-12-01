package com.yuroyami.syncplay.watchroom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.yuroyami.syncplay.player.PlayerUtils
import com.yuroyami.syncplay.utils.getScreenSizeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GestureInterceptor(
    gestures: Boolean, hasVideo: Boolean,
    onSingleTap: () -> Unit
) {
    val scope = rememberCoroutineScope { Dispatchers.IO }

    val seekLeftInteraction = remember { MutableInteractionSource() }
    val seekRightInteraction = remember { MutableInteractionSource() }

    val dimensions = getScreenSizeInfo()

    //TODO: Individual gesture toggling option

    Box(modifier = Modifier.fillMaxSize()
        .pointerInput(hasVideo, gestures) {
            detectTapGestures(
                onDoubleTap = if (gestures && hasVideo) { offset ->
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
        .pointerInput(hasVideo, gestures) {
            detectVerticalDragGestures(
                onDragStart = {

                },
                onVerticalDrag = { pntr, f ->

                }
            )
        }
    ) {
        /* Seek animators, their only purpose is to animate a seek action */
        if (gestures) {
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
                    interactionSource = seekRightInteraction,
                    indication = rememberRipple(
                        bounded = false,
                        color = Color(100, 100, 100, 190)
                    )
                ) {}
            )
        }
    }
}