package com.yuroyami.syncplay.ui.screens.room.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.managers.protocol.creator.PacketOut
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.SyncplayPopup
import com.yuroyami.syncplay.ui.components.syncplayFont
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.utils.RandomStringGenerator
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.cancel
import syncplaymobile.shared.generated.resources.okay
import syncplaymobile.shared.generated.resources.room_overflow_create_managed_room
import syncplaymobile.shared.generated.resources.room_overflow_identify_as_operator

enum class ManagedRoomPopupPurpose {
    CREATE_MANAGED_ROOM, IDENTIFY_AS_OPERATOR
}

@Composable
fun ManagedRoomPopup(purpose: ManagedRoomPopupPurpose) {
    val viewmodel = LocalRoomViewmodel.current

    val state = when (purpose) {
        ManagedRoomPopupPurpose.CREATE_MANAGED_ROOM -> viewmodel.uiManager.popupCreateManagedRoom
        ManagedRoomPopupPurpose.IDENTIFY_AS_OPERATOR -> viewmodel.uiManager.popupIdentifyAsRoomOperator
    }

    val visible by state.collectAsState()

    return SyncplayPopup(
        dialogOpen = visible,
        widthPercent = 0.7f,
        strokeWidth = 0.5f,
        alpha = 0.9f,
        onDismiss = {
            state.value = false
        }
    ) {
        var inputValue by remember {
            mutableStateOf(
                value = when (purpose) {
                    ManagedRoomPopupPurpose.CREATE_MANAGED_ROOM -> viewmodel.session.currentRoom
                    ManagedRoomPopupPurpose.IDENTIFY_AS_OPERATOR -> ""
                }
            )
        }

        val focusManager = LocalFocusManager.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {

            /* The title */
            FlexibleText(
                modifier = Modifier.padding(6.dp), text = when (purpose) {
                    ManagedRoomPopupPurpose.CREATE_MANAGED_ROOM -> stringResource(Res.string.room_overflow_create_managed_room)
                    ManagedRoomPopupPurpose.IDENTIFY_AS_OPERATOR -> stringResource(Res.string.room_overflow_identify_as_operator)
                }, shadowColors = listOf(Color.Black), fillingColors = Theming.SP_GRADIENT, size = 18f, font = syncplayFont
            )

            TextField(
                modifier = Modifier.fillMaxWidth().padding(6.dp), singleLine = true, value = inputValue, keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus(true)
                }), colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.DarkGray,
                    unfocusedContainerColor = Color.DarkGray,
                    disabledContainerColor = Color.DarkGray,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ), onValueChange = { inputValue = it }, textStyle = TextStyle(
                    brush = Brush.linearGradient(colors = Theming.SP_GRADIENT),
                    fontSize = 16.sp,
                )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Spacer(Modifier.weight(1f))

                /* Cancel button */
                Button(
                    modifier = Modifier.padding(vertical = 8.dp).weight(3f),
                    onClick = {
                        state.value = false
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Close, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.cancel), fontSize = 14.sp, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))

                /* OK button */
                Button(
                    modifier = Modifier.padding(vertical = 8.dp).weight(3f),
                    onClick = {
                        state.value = false

                        viewmodel.protocolManager.isRoomChanging = true

                        viewmodel.networkManager.sendAsync<PacketOut.ControllerAuth> {
                            when (purpose) {
                                ManagedRoomPopupPurpose.CREATE_MANAGED_ROOM -> {
                                    room = inputValue
                                    password = RandomStringGenerator.generateRoomPassword()
                                }

                                ManagedRoomPopupPurpose.IDENTIFY_AS_OPERATOR -> {
                                    password = inputValue
                                }
                            }

                        }
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Done, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.okay), fontSize = 14.sp, maxLines = 1)
                }

                Spacer(Modifier.weight(1f))
            }
        }
    }
}