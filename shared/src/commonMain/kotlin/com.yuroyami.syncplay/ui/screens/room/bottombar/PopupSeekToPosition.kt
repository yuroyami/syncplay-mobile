package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.managers.preferences.Preferences.CUSTOM_SEEK_AMOUNT
import com.yuroyami.syncplay.managers.preferences.watchPref
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.SyncplayPopup
import com.yuroyami.syncplay.ui.components.syncplayFont
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.utils.timeStamper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.done
import syncplaymobile.shared.generated.resources.room_custom_skip_button

object PopupSeekToPosition {

    @Composable
    fun SeekToPositionPopup(visibilityState: MutableState<Boolean>) {
        SyncplayPopup(
            dialogOpen = visibilityState.value,
            strokeWidth = 0.5f,
            onDismiss = { visibilityState.value = false }
        ) {
            val viewmodel = LocalRoomViewmodel.current
            val focusManager = LocalFocusManager.current

            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                /* The title */
                FlexibleText(
                    text = "Seek to Precise Position", //TODO Localize
                    strokeColors = listOf(Color.Black),
                    fillingColors = Theming.SP_GRADIENT,
                    size = 18f,
                    font = syncplayFont
                )

                /* Title's subtext */
                Text(
                    text = "Hours:Minutes:Seconds", //TODO Localize
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextField(
                        modifier = Modifier.width(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        value = hours.value,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.moveFocus(FocusDirection.Next)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.DarkGray,
                            unfocusedContainerColor = Color.DarkGray,
                            disabledContainerColor = Color.DarkGray,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        onValueChange = { hours.value = it },
                        textStyle = TextStyle(
                            brush = Brush.linearGradient(colors = Theming.SP_GRADIENT),
                            fontFamily = FontFamily(syncplayFont),
                            fontSize = 16.sp,
                        ),
                        label = { Text("HH", color = Color.Gray) }
                    )

                    Spacer(Modifier.width(12.dp))


                    TextField(
                        modifier = Modifier.width(64.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        singleLine = true,
                        value = minutes.value,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.moveFocus(FocusDirection.Next)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.DarkGray,
                            unfocusedContainerColor = Color.DarkGray,
                            disabledContainerColor = Color.DarkGray,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        onValueChange = { minutes.value = it },
                        textStyle = TextStyle(
                            brush = Brush.linearGradient(colors = Theming.SP_GRADIENT),
                            fontFamily = FontFamily(syncplayFont),
                            fontSize = 16.sp,
                        ),
                        label = { Text("MM", color = Color.Gray) }
                    )

                    Spacer(Modifier.width(12.dp))

                    TextField(
                        modifier = Modifier.width(64.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        singleLine = true,
                        value = seconds.value,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.moveFocus(FocusDirection.Next)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.DarkGray,
                            unfocusedContainerColor = Color.DarkGray,
                            disabledContainerColor = Color.DarkGray,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        onValueChange = { seconds.value = it },
                        textStyle = TextStyle(
                            brush = Brush.linearGradient(colors = Theming.SP_GRADIENT),
                            fontFamily = FontFamily(syncplayFont),
                            fontSize = 16.sp,
                        ),
                        label = { Text("ss", color = Color.Gray) }
                    )
                }

                /* Custom Skip intro */
                val customSkipAmount by CUSTOM_SEEK_AMOUNT.watchPref()
                val customSkipAmountString by derivedStateOf { timeStamper(customSkipAmount) }

                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(width = 1.dp, color = Color.Black),
                    modifier = Modifier.Companion,
                    onClick = {
                        visibilityState.value = false
                        viewmodel.player.playerScopeIO?.launch {
                            val currentMs = withContext(Dispatchers.Main) { viewmodel.player.currentPositionMs() }
                            val newPos = (currentMs) + (customSkipAmount * 1000L)

                            viewmodel.actionManager.sendSeek(newPos)
                            viewmodel.player.seekTo(newPos)

                            if (viewmodel.isSoloMode) {
                                viewmodel.seeks.add(Pair((currentMs), newPos * 1000))
                            }

                            //TODO: Localize
                            viewmodel.osdManager.dispatchOSD { "Skipping  of time" }
                        }
                    },
                ) {
                    Icon(imageVector = Icons.Filled.AvTimer, "")
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(stringResource(Res.string.room_custom_skip_button, customSkipAmountString), fontSize = 14.sp)
                }

                /* Ok button */
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(width = 1.dp, color = Color.Black),
                    modifier = Modifier.Companion,
                    onClick = {
                        visibilityState.value = false

                        var ss = seconds.value.toLongOrNull() ?: 0
                        var mm = minutes.value.toLongOrNull() ?: 0
                        val hh = hours.value.toLongOrNull() ?: 0

                        if (ss >= 60) ss = 59
                        if (mm >= 60) mm = 59

                        viewmodel.player.playerScopeMain.launch {
                            val ssMs = ss * 1000
                            val mmMs = mm * 60 * 1000
                            val hhMs = hh * 3600 * 1000
                            val result = ssMs + mmMs + hhMs

                            if (viewmodel.isSoloMode) {
                                viewmodel.seeks.add(Pair(viewmodel.player.currentPositionMs(), result))
                            }

                            viewmodel.player.seekTo(result)

                            //TODO: Localize
                            viewmodel.osdManager.dispatchOSD { "Seeking to ${timeStamper(result)}" }
                        }

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