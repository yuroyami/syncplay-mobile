package app.room.ui.bottombar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalRoomViewmodel
import app.preferences.Preferences.CUSTOM_SEEK_AMOUNT
import app.preferences.value
import app.preferences.watchPref
import app.room.RoomViewmodel
import app.theme.Theming
import app.uicomponents.FlexibleText
import app.uicomponents.SyncplayPopup
import app.uicomponents.syncplayFont
import app.utils.timestampFromMillis
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.done
import syncplaymobile.shared.generated.resources.room_custom_skip_button
import syncplaymobile.shared.generated.resources.room_seek_toposition_success
import syncplaymobile.shared.generated.resources.room_seek_toposition_timeformat
import syncplaymobile.shared.generated.resources.room_seek_toposition_title

object PopupSeekToPosition {

    @Composable
    fun SeekToPositionPopup() {
        val viewmodel = LocalRoomViewmodel.current
        val visible by viewmodel.uiState.popupSeekToPosition.collectAsState()

        SyncplayPopup(
            dialogOpen = visible,
            onDismiss = { viewmodel.uiState.popupSeekToPosition.value = false }
        ) {
            val viewmodel = LocalRoomViewmodel.current
            val focusManager = LocalFocusManager.current

            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                /* The title */
                FlexibleText(
                    text = stringResource(Res.string.room_seek_toposition_title),
                    strokeColors = listOf(Color.Black),
                    fillingColors = Theming.flexibleGradient,
                    size = 18f,
                    font = syncplayFont
                )

                /* Title's subtext */
                Text(
                    text = stringResource(Res.string.room_seek_toposition_timeformat),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily(syncplayFont),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )

                /* The boxes row */
                val hours = remember { mutableStateOf("") }
                val minutes = remember { mutableStateOf("") }
                val seconds = remember { mutableStateOf("") }

                val hhRequester = remember { FocusRequester() }
                val mmRequester = remember { FocusRequester() }
                val ssRequester = remember { FocusRequester() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val fieldColors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    )
                    val fieldTextStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

                    TextField(
                        modifier = Modifier.width(72.dp).focusRequester(hhRequester),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        value = hours.value,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(onDone = {
                            mmRequester.requestFocus()
                        }),
                        colors = fieldColors,
                        onValueChange = { hours.value = it },
                        textStyle = fieldTextStyle,
                        label = { Text("HH", color = labelColor) }
                    )

                    Spacer(Modifier.width(12.dp))

                    TextField(
                        modifier = Modifier.width(72.dp).focusRequester(mmRequester),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        value = minutes.value,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(onDone = {
                            ssRequester.requestFocus()
                        }),
                        colors = fieldColors,
                        onValueChange = { minutes.value = it },
                        textStyle = fieldTextStyle,
                        label = { Text("MM", color = labelColor) }
                    )

                    Spacer(Modifier.width(12.dp))

                    TextField(
                        modifier = Modifier.width(72.dp).focusRequester(ssRequester),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        value = seconds.value,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus(true)
                        }),
                        colors = fieldColors,
                        onValueChange = { seconds.value = it },
                        textStyle = fieldTextStyle,
                        label = { Text("SS", color = labelColor) }
                    )
                }

                LaunchedEffect(Unit) {
                    hhRequester.requestFocus()
                }

                /* Custom Skip intro */
                val customSkipAmount by CUSTOM_SEEK_AMOUNT.watchPref()
                val customSkipAmountString by derivedStateOf { timestampFromMillis(customSkipAmount * 1000) }

                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.Companion,
                    onClick = {
                        viewmodel.uiState.popupSeekToPosition.value = false

                        viewmodel.customSkip()
                    },
                ) {
                    Icon(imageVector = Icons.Filled.AvTimer, "")
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(stringResource(Res.string.room_custom_skip_button, customSkipAmountString), fontSize = 14.sp)
                }

                /* Ok button */
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.Companion,
                    onClick = {
                        viewmodel.uiState.popupSeekToPosition.value = false

                        var ss = seconds.value.toLongOrNull() ?: 0
                        var mm = minutes.value.toLongOrNull() ?: 0
                        val hh = hours.value.toLongOrNull() ?: 0

                        if (ss >= 60) ss = 59
                        if (mm >= 60) mm = 59

                        val result = ss * 1000 + mm * 60 * 1000 + hh * 3600 * 1000
                        // The one seek path: announce first so SYNC_REWIND does not yank us back.
                        viewmodel.dispatcher.seek(result)
                        viewmodel.dispatchOSD { getString(Res.string.room_seek_toposition_success, timestampFromMillis(result)) }

                    },
                ) {
                    Icon(imageVector = Icons.Filled.Done, "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.done), fontSize = 14.sp)
                }
            }
        }
    }
}

fun RoomViewmodel.customSkip() {
    player.playerScopeMain.launch {
        val currentMs = player.currentPositionMs()
        val newPos = currentMs + CUSTOM_SEEK_AMOUNT.value() * 1000L
        dispatcher.seek(newPos, fromMs = currentMs)
        // The same notice as seek-to, so it gets the same timecode shape, not a bare count.
        dispatchOSD { getString(Res.string.room_seek_toposition_success, timestampFromMillis(newPos)) }
    }
}