package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuroyami.syncplay.managers.preferences.Preferences.CUSTOM_SEEK_AMOUNT
import com.yuroyami.syncplay.managers.preferences.Preferences.CUSTOM_SEEK_FRONT
import com.yuroyami.syncplay.managers.preferences.Preferences.GESTURES
import com.yuroyami.syncplay.managers.preferences.watchPref
import com.yuroyami.syncplay.ui.components.FlexibleIcon
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.screens.theme.Theming.ROOM_ICON_SIZE
import com.yuroyami.syncplay.utils.timestampFromMillis
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_chapters_skip
import syncplaymobile.shared.generated.resources.room_custom_skip_button

@Composable
fun RoomBottomBarVideoControlRow(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current

    val gesturesEnabled by GESTURES.watchPref()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        val customSkipAmount by CUSTOM_SEEK_AMOUNT.watchPref()
        val customSkipToFront by CUSTOM_SEEK_FRONT.watchPref()
        if (customSkipToFront) {
            val customSkipAmountString by derivedStateOf {
                timestampFromMillis(
                    customSkipAmount * 1000
                )
            }
            OutlinedButton(
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.3f)
                ),
                border = BorderStroke(Dp.Hairline, color = MaterialTheme.colorScheme.primary),
                modifier = Modifier.zIndex(100f),
                onClick = {
                    viewmodel.customSkip()
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
            OutlinedButton(
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.3f)
                ),
                border = BorderStroke(Dp.Hairline, color = MaterialTheme.colorScheme.primary),
                modifier = Modifier.zIndex(100f),
                onClick = {
                    viewmodel.player.playerScopeMain.launch {
                        viewmodel.player.skipChapter()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = stringResource(Res.string.room_chapters_skip),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}