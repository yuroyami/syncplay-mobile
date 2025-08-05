package com.yuroyami.syncplay.screens.room.subcomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun RoomVideoSeekbar() {
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
    Slider(
        value = slidervalue.toFloat(),
        valueRange = (0f..(slidermax.toFloat())),
        onValueChange = { f ->
            viewmodel.player?.seekTo(f.toLong() * 1000L)
            if (isSoloMode) {
                viewmodel.player?.let {
                    composeScope.launch(Dispatchers.Main) {
                        viewmodel.seeks.add(
                            Pair(
                                it.currentPositionMs(), f.toLong() * 1000
                            )
                        )
                    }
                }
            }

            slidervalue = f.toLong()
        },
        onValueChangeFinished = {
            viewmodel.sendSeek(slidervalue * 1000L)
        },
        modifier = Modifier.alpha(0.82f).fillMaxWidth(0.75f)
            .padding(horizontal = 12.dp),
        interactionSource = interactionSource,
        steps = 500,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = SliderDefaults.colors(),
                modifier = Modifier.alpha(0.6f)
            )
        },
        track = { sliderState ->
            // Assuming media duration is available in ms
            val mediaDuration = viewmodel.media?.fileDuration ?: 1L

            // Capture track width
            var trackWidth by remember { mutableStateOf(0) }

            Box(
                modifier = Modifier.fillMaxWidth()
                    .onSizeChanged { trackWidth = it.width }) {
                // Draw the slider track
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.fillMaxWidth()
                        .scale(scaleX = 1F, scaleY = 0.85F),
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                    drawTick = { _, _ -> })


                chapters.forEach { chapter ->
                    if (chapter.timestamp / 1000 != 0L) {
                        // Calculate horizontal position fraction based on chapter timestamp
                        val positionFraction =
                            (chapter.timestamp / mediaDuration.toFloat()) / 1000
                        Box(
                            modifier = Modifier.offset {
                                // 4.dp to pixels
                                val offsetAdjustment =
                                    with(density) { 4.dp.toPx() }
                                IntOffset(
                                    (positionFraction * trackWidth).toInt() - offsetAdjustment.toInt(),
                                    0
                                )
                            }.align(Alignment.CenterStart)

                                .size(8.dp).clip(CircleShape)
                                .background(Color.White).clickable {
                                    viewmodel.player?.jumpToChapter(chapter)

                                })
                    }
                }

            }
        })
}