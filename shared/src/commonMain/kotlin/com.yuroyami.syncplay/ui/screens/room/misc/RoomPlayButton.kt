package com.yuroyami.syncplay.ui.screens.room.misc

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.screens.theme.Theming
import com.yuroyami.syncplay.ui.screens.theme.Theming.ROOM_ICON_SIZE
import com.yuroyami.syncplay.ui.components.FlexibleIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun RoomPlayButton(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val hasVideo by viewmodel.hasVideo.collectAsState()

    /** PLAY BUTTON */
    val playing = remember { viewmodel.videoEngineManager.isNowPlaying }
    val animatedColor by animateColorAsState(
        animationSpec = tween(500),
        targetValue = if (playing.value) Theming.SP_GRADIENT.last().copy(alpha = 0.1f)
        else Theming.SP_GRADIENT.first().copy(alpha = 0.1f)
    )
    if (hasVideo) {
        FlexibleIcon(
            icon = when (playing.value) {
                true -> Icons.Filled.Pause
                false -> Icons.Filled.PlayArrow
            },
            size = (ROOM_ICON_SIZE * 2.25).roundToInt(),
            shadowColors = listOf(Color.Black),
            modifier = modifier.background(
                shape = CircleShape, color = animatedColor
            )
        ) {
            viewmodel.viewModelScope.launch(Dispatchers.Main) {
                when (viewmodel.player.isPlaying()) {
                    true -> viewmodel.actionManager.pausePlayback()
                    false -> viewmodel.actionManager.playPlayback()
                }
            }
        }
    }
}