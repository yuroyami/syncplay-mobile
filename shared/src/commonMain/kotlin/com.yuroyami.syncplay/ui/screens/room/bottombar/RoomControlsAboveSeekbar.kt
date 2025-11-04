package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.MISC_GESTURES
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_PLAYER_CUSTOM_SEEK_AMOUNT
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_PLAYER_CUSTOM_SEEK_FRONT
import com.yuroyami.syncplay.managers.datastore.valueAsState
import com.yuroyami.syncplay.managers.datastore.valueFlow
import com.yuroyami.syncplay.ui.components.FlexibleIcon
import com.yuroyami.syncplay.ui.components.gradientOverlay
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.theme.Theming.ROOM_ICON_SIZE
import com.yuroyami.syncplay.utils.timeStamper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_custom_skip_button

@Composable
fun RoomBottomBarVideoControlRow(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current

    val gesturesEnabled by valueFlow(MISC_GESTURES, true).collectAsState(initial = true)

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!gesturesEnabled) {
            FlexibleIcon(
                icon = Icons.Filled.FastRewind,
                size = ROOM_ICON_SIZE + 6,
                shadowColors = listOf(Color.Black)
            ) {
                viewmodel.actionManager.seekBckwd()
            }

            FlexibleIcon(
                icon = Icons.Filled.FastForward,
                size = ROOM_ICON_SIZE + 6,
                shadowColors = listOf(Color.Black)
            ) {
                viewmodel.actionManager.seekFrwrd()
            }
        }
        val customSkipToFront by PREF_INROOM_PLAYER_CUSTOM_SEEK_FRONT.valueAsState(true)
        val customSkipAmount by PREF_INROOM_PLAYER_CUSTOM_SEEK_AMOUNT.valueAsState(90)
        if (customSkipToFront) {
            val customSkipAmountString by derivedStateOf {
                timeStamper(
                    customSkipAmount
                )
            }
            TextButton(
                modifier = Modifier.gradientOverlay().background(
                    brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map {
                        it.copy(alpha = 0.1f)
                    }), shape = CircleShape
                ),
                onClick = {
                    viewmodel.player.playerScopeIO.launch {
                        val currentMs = withContext(Dispatchers.Main) { viewmodel.player.currentPositionMs() }
                        val newPos = (currentMs) + (customSkipAmount * 1000L)

                        viewmodel.actionManager.sendSeek(newPos)
                        viewmodel.player.seekTo(newPos)

                        if (viewmodel.isSoloMode) {
                            viewmodel.seeks.add(
                                Pair(
                                    (currentMs), newPos * 1000
                                )
                            )
                        }

                        viewmodel.osdManager.dispatchOSD {
                            getString(Res.string.room_custom_skip_button, customSkipAmountString)
                        }
                    }
                },
            ) {
                Icon(imageVector = Icons.Filled.AvTimer, "")
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = stringResource(Res.string.room_custom_skip_button, customSkipAmountString),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }

        if (viewmodel.media?.chapters?.isNotEmpty() == true) {
            TextButton(
                modifier = Modifier.gradientOverlay().background(
                    brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map {
                        it.copy(alpha = 0.1f)
                    }), shape = CircleShape
                ), onClick = {
                    viewmodel.player.playerScopeIO.launch {
                        val currentMs =
                            withContext(Dispatchers.Main) { viewmodel.player.currentPositionMs() }
                        val nextChapter =
                            viewmodel.media?.chapters?.filter { it.timestamp > currentMs }
                                ?.minByOrNull { it.timestamp }
                        if (nextChapter != null) {
                            viewmodel.player.seekTo(nextChapter.timestamp)
                        } else {
                            // fallback if no chapter is ahead
                            viewmodel.player.seekTo(currentMs + (customSkipAmount * 1000L))
                        }
                    }
                }) {
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = "Skip chapter",
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }

}