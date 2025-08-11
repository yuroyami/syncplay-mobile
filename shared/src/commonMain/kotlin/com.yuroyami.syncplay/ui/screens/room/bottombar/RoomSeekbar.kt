package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.ui.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.ui.utils.FlexibleFancyText
import com.yuroyami.syncplay.ui.utils.gradientOverlay
import com.yuroyami.syncplay.utils.timeStamper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import com.composeunstyled.Thumb as UnstyledThumb

@Composable
fun RoomSeekbar(modifier: Modifier) {
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

    //TODO USE A LOCAL SLIDERVALUE STATE AND ONLY UPDATE IT GRACEFULLY

    val videoCurrentTime by viewmodel.timeCurrent.collectAsState()
    val videoFullDuration by viewmodel.timeFull.collectAsState()

    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isPressed by sliderInteractionSource.collectIsDraggedAsState()

    val currentTimeText by derivedStateOf { timeStamper(videoCurrentTime) }
    val fullTimeText by derivedStateOf { if (videoFullDuration >= Long.MAX_VALUE / 1000L) "???" else timeStamper(videoFullDuration) }

    var trackWidthPx by remember { mutableIntStateOf(0) }

    Box(modifier) {
        Slider(
            value = videoCurrentTime.toFloat(),
            onValueChange = { f ->
                viewmodel.player?.seekTo(f.roundToLong() * 1000L)
                viewmodel.timeCurrent.value = f.roundToLong()
            },
            onValueChangeFinished = {
                viewmodel.sendSeek(videoCurrentTime * 1000L)

                if (viewmodel.isSoloMode) {
                    viewmodel.player?.let {
                        viewmodel.viewModelScope.launch(Dispatchers.Main) {
                            viewmodel.seeks.add(
                                Pair(
                                    it.currentPositionMs(), videoCurrentTime * 1000
                                )
                            )
                        }
                    }
                }
            },
            modifier = modifier.height(56.dp),
            interactionSource = sliderInteractionSource,
            valueRange = 0f..(videoFullDuration.toFloat()),
            thumb = {
                UnstyledThumb(
                    color = Color.DarkGray.copy(0.8f),
                    modifier = Modifier
                        .height(26.dp)
                        .width(if (isPressed) 8.dp else 8.dp) // keep it thin always
                        .shadow(4.dp, CircleShape),
                    shape = CircleShape
                )
            },
            track = { state ->
                val trackThickness by animateDpAsState(targetValue = if (isPressed) 12.dp else 24.dp)
                val trackRoundedness by animateIntAsState(targetValue = if (isPressed) 20 else 35)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackThickness)
                        .clip(RoundedCornerShape(trackRoundedness))
                        .background(Color.Gray).onGloballyPositioned {
                            trackWidthPx = it.size.width
                        }
                ) {
                    chapters.forEach { chapter ->
                        if (chapter.timestamp / 1000 != 0L) {
                            // Calculate horizontal position fraction based on chapter timestamp
                            val positionFraction = (chapter.timestamp / videoFullDuration.toFloat().coerceAtLeast(1f))
                            Box(
                                modifier = Modifier
                                    .offset {
                                        // 4.dp to pixels
                                        val offsetAdjustment = with(density) { 4.dp.toPx() }

                                        IntOffset((positionFraction * trackWidthPx).toInt() - offsetAdjustment.toInt(), 0)
                                    }.align(CenterStart)
                                    .size(8.dp).clip(CircleShape)
                                    .background(Color.LightGray)
                                    .clickable(
                                        interactionSource = null,
                                        indication = ripple()
                                    ) {
                                        viewmodel.player?.jumpToChapter(chapter)
                                    }
                            )
                        }
                    }

                    if (!isPressed) {
                        //Current video position text
                        FlexibleFancyText(
                            modifier = Modifier.alpha(0.85f).padding(horizontal = 8.dp).align(CenterStart),
                            text = currentTimeText,
                            size = 11f,
                            fillingColors = listOf(Color.Black),
                            //strokeColors = listOf(Color.Black)
                        )

                        //Full video time text
                        FlexibleFancyText(
                            modifier = Modifier.alpha(0.85f).padding(horizontal = 8.dp).align(Alignment.CenterEnd),
                            text = fullTimeText,
                            size = 11f,
                            fillingColors = listOf(Color.Black),
                            //strokeColors = listOf(Color.Black)
                        )
                    }
                }
            },
        )

        if (videoFullDuration > 0 && isPressed) {
            val density = LocalDensity.current
            var bubbleTextWidth by remember { mutableIntStateOf(0) }

            val bubbleOffset by derivedStateOf {
                val sliderFraction = videoCurrentTime / videoFullDuration.toFloat().coerceAtLeast(1f)
                val sliderPx = trackWidthPx * sliderFraction
                val bubbleHalfPx = with(density) { bubbleTextWidth / 2 }

                // Clamp so the bubble stays fully visible
                val rawOffset = sliderPx - bubbleHalfPx
                rawOffset.coerceIn(0f, (trackWidthPx - bubbleTextWidth).toFloat())
            }

            Box(
                modifier = Modifier
                    .offset(y = (-28).dp) // Move the bubble above the thumb needle
                    .offset(x = with(density) { bubbleOffset.toDp() })
                    .align(CenterStart)
                    .background(Color.DarkGray.copy(0.8f), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .onGloballyPositioned {
                        bubbleTextWidth = it.size.width
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$currentTimeText / $fullTimeText",
                    fontSize = 11.sp,
                    modifier = Modifier.gradientOverlay(),
                    color = Color.White
                )
            }
        }
    }
}