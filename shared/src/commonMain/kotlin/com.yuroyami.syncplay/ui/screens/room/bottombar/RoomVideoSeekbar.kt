package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.composeunstyled.rememberSliderState
import com.yuroyami.syncplay.ui.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.ui.utils.FlexibleFancyText
import com.yuroyami.syncplay.ui.utils.gradientOverlay
import com.yuroyami.syncplay.utils.timeStamper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.composeunstyled.Slider as UnstyledSlider
import com.composeunstyled.Thumb as UnstyledThumb

@Composable
fun RoomVideoSeekbar(modifier: Modifier) {
    val viewmodel = LocalViewmodel.current

    val chapters = remember(
        viewmodel.media?.fileName
    ) { viewmodel.media?.chapters ?: emptyList() }

    LaunchedEffect(viewmodel.media?.fileName) {
        viewmodel.viewModelScope.launch {
            viewmodel.player?.analyzeChapters(
                viewmodel.media ?: return@launch
            )
        }
    }

    //val videoCurrentTime by viewmodel.timeCurrent.collectAsState()
    val videoCurrentTime by remember { mutableFloatStateOf(0f) }

    //val videoFullDuration by viewmodel.timeFull.collectAsState()
    val videoFullDuration by remember { mutableFloatStateOf(100000f) }

    val sliderState = rememberSliderState(
        initialValue = videoCurrentTime.toFloat(),
        valueRange = (0f..(videoFullDuration.toFloat()))
    )
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isPressed by sliderInteractionSource.collectIsPressedAsState()

    LaunchedEffect(sliderState.value) {
        //Listening to changes to seekbar values
        viewmodel.player?.seekTo(sliderState.value.toLong() * 1000L)

        if (viewmodel.isSoloMode) {
            viewmodel.player?.let {
                viewmodel.viewModelScope.launch(Dispatchers.Main) {
                    viewmodel.seeks.add(
                        Pair(
                            it.currentPositionMs(), sliderState.value.toLong() * 1000
                        )
                    )
                }
            }
        }

        viewmodel.timeCurrent.value = sliderState.value.toLong()
    }

    LaunchedEffect(isPressed) {
        if (!isPressed) {
            //TODO viewmodel.sendSeek(videoCurrentTime * 1000L)
        }
    }

    val currentTimeText by derivedStateOf { timeStamper(videoCurrentTime) }
    val fullTimeText by derivedStateOf { if (videoFullDuration >= Long.MAX_VALUE / 1000L) "???" else timeStamper(videoFullDuration) }


    var textWidth by remember { mutableStateOf(0.dp) }

    UnstyledSlider(
        state = sliderState,
        interactionSource = sliderInteractionSource,
        modifier = Modifier.padding(horizontal = textWidth/2).height(48.dp),
        track = {
            val trackThickness by animateDpAsState(targetValue = if (isPressed) 12.dp else 24.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackThickness)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Gray)
            ) {
                if (!isPressed) {
                    //Current video position text
                    FlexibleFancyText(
                        modifier = Modifier.alpha(0.85f).gradientOverlay().padding(horizontal = 8.dp).align(Alignment.CenterStart),
                        text = currentTimeText,
                        size = 11f,
                        strokeColors = listOf(Color.Black)
                    )

                    //Full video time text
                    FlexibleFancyText(
                        modifier = Modifier.alpha(0.85f).gradientOverlay().padding(horizontal = 8.dp).align(Alignment.CenterEnd),
                        text = fullTimeText,
                        size = 11f,
                        strokeColors = listOf(Color.Black)
                    )
                }
            }
        },
        thumb = {
            Box(
                modifier = Modifier.wrapContentWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Time bubble
                if (isPressed) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-28).dp) // Move the bubble above the thumb needle
                            .wrapContentWidth()
                            .background(Color.DarkGray, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$currentTimeText/$fullTimeText",
                            fontSize = 11.sp,
                            modifier = Modifier.gradientOverlay(),
                            color = Color.White
                        )
                    }
                }

                // Actual thumb needle
                UnstyledThumb(
                    color = Color.DarkGray,
                    modifier = Modifier
                        .height(24.dp)
                        .width(if (isPressed) 8.dp else 8.dp) // keep it thin always
                        .shadow(4.dp, CircleShape),
                    shape = CircleShape
                )
            }
        }

    )

    return


// Assuming media duration is available in ms
//    val mediaDuration = viewmodel.media?.fileDuration ?: 1L
//
//    // Capture track width
//    var trackWidth by remember { mutableStateOf(0) }
//
//    Box(
//        modifier = Modifier.fillMaxWidth()
//            .onSizeChanged { trackWidth = it.width }) {
//        // Draw the slider track
//        SliderDefaults.Track(
//            sliderState = sliderState,
//            modifier = Modifier.fillMaxWidth()
//                .scale(scaleX = 1F, scaleY = 0.85F),
//            thumbTrackGapSize = 0.dp,
//            drawStopIndicator = null,
//            drawTick = { _, _ -> })
//
//
//        chapters.forEach { chapter ->
//            if (chapter.timestamp / 1000 != 0L) {
//                // Calculate horizontal position fraction based on chapter timestamp
//                val positionFraction =
//                    (chapter.timestamp / mediaDuration.toFloat()) / 1000
//                Box(
//                    modifier = Modifier
//                        .offset {
//                            // 4.dp to pixels
//                            val offsetAdjustment = with(density) { 4.dp.toPx() }
//                            IntOffset((positionFraction * trackWidth).toInt() - offsetAdjustment.toInt(), 0)
//                        }.align(Alignment.CenterStart)
//                        .size(8.dp).clip(CircleShape)
//                        .background(Color.White).clickable {
//                            viewmodel.player?.jumpToChapter(chapter)
//                        }
//                )
//            }
//        }
//    }
}