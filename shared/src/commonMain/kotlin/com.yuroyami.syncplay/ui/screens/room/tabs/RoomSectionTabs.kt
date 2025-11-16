package com.yuroyami.syncplay.ui.screens.room.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.yuroyami.syncplay.ui.components.FlexibleIcon
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.jostFont
import com.yuroyami.syncplay.ui.screens.adam.LocalCardController
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.theme.Theming.flexibleGradient
import com.yuroyami.syncplay.utils.platformCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_overflow_create_managed_room
import syncplaymobile.shared.generated.resources.room_overflow_identify_as_operator
import syncplaymobile.shared.generated.resources.room_overflow_leave_room
import syncplaymobile.shared.generated.resources.room_overflow_msghistory
import syncplaymobile.shared.generated.resources.room_overflow_pip
import syncplaymobile.shared.generated.resources.room_overflow_title

class CardController {
    val tabCardUserInfo = MutableStateFlow(false)
    val tabCardSharedPlaylist = MutableStateFlow(false)
    val tabCardRoomPreferences = MutableStateFlow(false)
    val tabLock = MutableStateFlow(false)

    val controlPanel = MutableStateFlow(false)

    fun toggleUserInfo(forcedState: Boolean? = null) {
        tabCardUserInfo.value = forcedState ?: !tabCardUserInfo.value
        if (tabCardUserInfo.value) {
            tabCardSharedPlaylist.value = false
            tabCardRoomPreferences.value = false
        }
    }

    fun toggleSharedPlaylist(forcedState: Boolean? = null) {
        tabCardSharedPlaylist.value = forcedState ?: !tabCardSharedPlaylist.value
        if (tabCardSharedPlaylist.value) {
            tabCardUserInfo.value = false
            tabCardRoomPreferences.value = false
        }
    }

    fun toggleRoomPreferences(forcedState: Boolean? = null) {
        tabCardRoomPreferences.value = forcedState ?: !tabCardRoomPreferences.value
        if (tabCardRoomPreferences.value) {
            tabCardUserInfo.value = false
            tabCardSharedPlaylist.value = false
        }
    }
}

@Composable
fun RoomTabSection(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalCardController.current

    val stateUserInfo by cardController.tabCardUserInfo.collectAsState()
    val stateSharedPlaylist by cardController.tabCardSharedPlaylist.collectAsState()
    val stateRoomPreferences by cardController.tabCardRoomPreferences.collectAsState()
    val stateRoomLock by cardController.tabLock.collectAsState()

    Row(
        modifier = modifier.fillMaxWidth().height(54.dp).padding(top = 18.dp, end = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = CenterVertically
    ) {

        /* The tabs in the top-right corner */
        /* In-room settings */
        RoomTab(
            modifier = Modifier.width(54.dp),
            icon = Icons.Filled.AutoFixHigh,
            visibilityState = stateRoomPreferences
        ) { cardController.toggleRoomPreferences() }

        Spacer(modifier = Modifier.weight(1f))

        /* Shared Playlist */
        if (!viewmodel.isSoloMode) {
            RoomTab(
                modifier = Modifier.width(54.dp),
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                visibilityState = stateSharedPlaylist
            ) {
                cardController.toggleSharedPlaylist()
            }

            Spacer(modifier = Modifier.weight(1f))

            /* User Info card tab */
            RoomTab(
                modifier = Modifier.width(54.dp),
                icon = Icons.Filled.Groups,
                visibilityState = stateUserInfo
            ) {
                cardController.toggleUserInfo()
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        /** Lock card */
        RoomTab(
            modifier = Modifier.width(54.dp),
            icon = Icons.Filled.Lock,
            visibilityState = false
        ) {
            cardController.tabLock.value = true
            viewmodel.uiManager.visibleHUD.value = false
        }

        Spacer(modifier = Modifier.weight(1f))

        Box {
            val overflowMenuState = remember { mutableStateOf(false) }
            FlexibleIcon(
                icon = Icons.Filled.MoreVert,
                size = 48,
                shadowColors = listOf(Color.Black)
            ) {
                overflowMenuState.value = !overflowMenuState.value
            }

            DropdownMenu(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                border = BorderStroke(width = Dp.Hairline, brush = Brush.linearGradient(colors = flexibleGradient)),
                shape = RoundedCornerShape(8.dp),
                expanded = overflowMenuState.value,
                properties = PopupProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
                onDismissRequest = {
                    scope.launch {
                        delay(50)
                        if (overflowMenuState.value) overflowMenuState.value = false
                    }
                }
            ) {
                /* Popup title */
                FlexibleText(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 2.dp),
                    text = stringResource(Res.string.room_overflow_title),
                    strokeColors = listOf(Color.Black),
                    fillingColors = flexibleGradient,
                    size = 14f,
                    font = jostFont
                )

                /* Picture-in-Picture mode */
                RoomOverflowItem(
                    text = stringResource(Res.string.room_overflow_pip),
                    icon = Icons.Filled.PictureInPicture,
                    onClick = {
                        overflowMenuState.value = false
                        platformCallback.onPictureInPicture(true)
                    }
                )

                val managedRoomAreSupported by viewmodel.sessionManager.supportsManagedRooms.collectAsState()

                if (!viewmodel.isSoloMode) {
                    if (managedRoomAreSupported) {
                        /* Create managed room */
                        RoomOverflowItem(
                            text = stringResource(Res.string.room_overflow_create_managed_room),
                            icon = Icons.Filled.SupervisedUserCircle,
                            onClick = {
                                overflowMenuState.value = false
                                viewmodel.uiManager.popupCreateManagedRoom.value = true
                            }
                        )

                        /* Identify as room operator */
                        RoomOverflowItem(
                            text = stringResource(Res.string.room_overflow_identify_as_operator),
                            icon = Icons.Filled.SupervisorAccount,
                            onClick = {
                                overflowMenuState.value = false

                                //TODO: Check if user is already operator
                                viewmodel.uiManager.popupIdentifyAsRoomOperator.value = true
                            }
                        )
                    }

                    /* Chat history item */
                    RoomOverflowItem(
                        text = stringResource(Res.string.room_overflow_msghistory),
                        icon = Icons.Filled.Forum,
                        onClick = {
                            overflowMenuState.value = false
                            viewmodel.uiManager.popupChatHistory.value = true
                        }
                    )
                }

                /* Leave room item */
                RoomOverflowItem(
                    text = stringResource(Res.string.room_overflow_leave_room),
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = {
                        overflowMenuState.value = false
                        viewmodel.leaveRoom()
                    }
                )
            }
        }
    }
}

@Composable
fun RoomOverflowItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        text = { Text(text = text) },
        onClick = onClick
    )
}