package app.room.ui.bottombar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalRoomViewmodel
import app.preferences.Preferences.CHAPTER_DOTS_CLICKABLE
import app.preferences.Preferences.SHOW_CHAPTER_DOTS
import app.preferences.watchPref
import app.theme.Theming.flexibleGradient
import app.utils.timestampFromMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToLong


@Composable
fun RoomSeekbar(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val scope = rememberCoroutineScope { Dispatchers.Main }
    val chapters = remember(viewmodel.media?.fileName) { viewmodel.media?.chapters ?: emptyList() }

    LaunchedEffect(viewmodel.media?.fileName) {
        viewmodel.player.analyzeChapters(viewmodel.media ?: return@LaunchedEffect)
    }

    var sliderValue by remember { mutableFloatStateOf(0f) }

    val videoCurrentTimeMs by viewmodel.playerManager.timeCurrentMillis.collectAsState()
    val videoFullDurationMs by viewmodel.playerManager.timeFullMillis.collectAsState()

    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isSliderBeingDragged by sliderInteractionSource.collectIsDraggedAsState()
    val isSliderBeingPressed by sliderInteractionSource.collectIsPressedAsState()
    val isSliderBeingFocused by sliderInteractionSource.collectIsFocusedAsState()

    /* Drag/press freezes the slider so it doesn't snap back under the user's finger. D-pad focus
     * alone doesn't freeze: LEFT/RIGHT are intercepted as real seeks, so the slider keeps tracking
     * the playback position visually. */
    val isSliderInUse by remember { derivedStateOf { isSliderBeingPressed || isSliderBeingDragged } }

    LaunchedEffect(videoCurrentTimeMs) {
        if (!isSliderInUse) {
            sliderValue = videoCurrentTimeMs.toFloat()
        }
    }

    val currentTimeText by derivedStateOf { timestampFromMillis(milliseconds = videoCurrentTimeMs) }
    val currentSliderValueText by derivedStateOf { timestampFromMillis(milliseconds = sliderValue) }
    val fullTimeText by derivedStateOf { if (videoFullDurationMs >= Long.MAX_VALUE) "???" else timestampFromMillis(videoFullDurationMs) }

    var trackWidthPx by remember { mutableIntStateOf(0) }

    var isSliding by remember { mutableStateOf(false) }
    var dragFromMs by remember { mutableLongStateOf(0L) }

    /* D-pad LEFT/RIGHT on the seekbar performs a configured-amount seek and broadcasts it
     * to the room (instead of the Slider's default tiny-step change). UP/DOWN falls through
     * to normal focus traversal. */
    val seekbarKeyModifier = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionLeft -> { viewmodel.dispatcher.seekBckwd(); true }
            Key.DirectionRight -> { viewmodel.dispatcher.seekFrwrd(); true }
            else -> false
        }
    }

    /* Visual focus indicator for D-pad: a gradient border matching the rest of the HUD. */
    val focusIndicatorModifier = if (isSliderBeingFocused) Modifier.border(
        width = 2.dp,
        brush = Brush.linearGradient(colors = flexibleGradient),
        shape = RoundedCornerShape(28.dp),
    ) else Modifier

    Box(modifier.then(seekbarKeyModifier).then(focusIndicatorModifier)) {
        Slider(
            value = sliderValue,
            onValueChange = { newVal ->
                if (!isSliding) {
                    isSliding = true
                    // The origin is captured on the first drag event, before the engine moves.
                    dragFromMs = viewmodel.player.currentPositionMs()
                }
                // Visual preview only while scrubbing; the engine seek happens once, on release,
                // instead of spamming per-frame seeks (expensive on mpv/VLC).
                sliderValue = newVal
            },
            onValueChangeFinished = {
                if (isSliding) {
                    isSliding = false
                    viewmodel.dispatcher.seek(targetMs = sliderValue.roundToLong(), fromMs = dragFromMs)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            interactionSource = sliderInteractionSource,
            valueRange = 0f..(videoFullDurationMs.toFloat()),
            thumb = {
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .width(8.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            },
            track = { _ ->
                val trackThickness by animateDpAsState(targetValue = if (isSliderBeingDragged) 28.dp else 20.dp)
                val progressFraction = (sliderValue / videoFullDurationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)

                /* Over-video chrome: fixed translucent white/black, readable on any frame. */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackThickness)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.24f))
                        .onGloballyPositioned {
                            trackWidthPx = it.size.width
                        }
                ) {
                    /* Played-portion fill: the sanctioned gradient accent of the seekbar. */
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(trackThickness)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(colors = flexibleGradient))
                            .align(CenterStart)
                    )

                    val showChapterDots by SHOW_CHAPTER_DOTS.watchPref()
                    val chapterDotsClickable by CHAPTER_DOTS_CLICKABLE.watchPref()
                    if (showChapterDots) {
                        chapters.forEach { chapter ->
                            if (chapter.timeOffsetMillis / 1000 != 0L) {
                                val positionFraction = (chapter.timeOffsetMillis / videoFullDurationMs.toFloat().coerceAtLeast(1f))
                                /* 20dp touch target around an 8dp visual dot. */
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            val offsetAdjustment = 10.dp.toPx()
                                            IntOffset((positionFraction * trackWidthPx).toInt() - offsetAdjustment.toInt(), 0)
                                        }.align(CenterStart)
                                        .size(20.dp).clip(CircleShape)
                                        .then(
                                            if (chapterDotsClickable) Modifier.clickable(
                                                interactionSource = null,
                                                indication = ripple()
                                            ) {
                                                scope.launch(Dispatchers.Main.immediate) {
                                                    viewmodel.player.jumpToChapter(chapter)
                                                }
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.9f))
                                    )
                                }
                            }
                        }
                    }

                    if (!isSliderBeingDragged) {
                        Text(
                            modifier = Modifier.alpha(0.9f).padding(horizontal = 10.dp).align(CenterStart),
                            text = currentTimeText,
                            fontSize = 11.sp,
                            color = Color.White,
                        )

                        Text(
                            modifier = Modifier.alpha(0.9f).padding(horizontal = 10.dp).align(Alignment.CenterEnd),
                            text = fullTimeText,
                            fontSize = 11.sp,
                            color = Color.White,
                        )
                    }
                }
            },
        )

        if (videoFullDurationMs > 0 && isSliderBeingDragged) {
            val density = LocalDensity.current
            var bubbleTextWidth by remember { mutableIntStateOf(0) }

            val bubbleOffset by derivedStateOf {
                val sliderFraction = sliderValue / videoFullDurationMs.toFloat().coerceAtLeast(1f)
                val sliderPx = trackWidthPx * sliderFraction
                val bubbleHalfPx = bubbleTextWidth / 2

                // Clamp so the bubble stays fully visible
                val rawOffset = sliderPx - bubbleHalfPx
                rawOffset.coerceIn(0f, (trackWidthPx - bubbleTextWidth).toFloat())
            }

            Box(
                modifier = Modifier
                    .offset(y = (-28).dp) // Move the bubble above the thumb needle
                    .offset(x = with(density) { bubbleOffset.toDp() })
                    .align(CenterStart)
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .onGloballyPositioned {
                        bubbleTextWidth = it.size.width
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$currentSliderValueText / $fullTimeText",
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }
    }
}
