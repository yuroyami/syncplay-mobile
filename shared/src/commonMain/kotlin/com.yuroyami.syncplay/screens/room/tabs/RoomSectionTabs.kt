package com.yuroyami.syncplay.screens.room.tabs

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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPicture
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.yuroyami.syncplay.components.FancyIcon2
import com.yuroyami.syncplay.components.FancyText2
import com.yuroyami.syncplay.components.syncplayFont
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.screens.adam.Screen
import com.yuroyami.syncplay.screens.adam.Screen.Companion.navigateTo
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.ui.Paletting.ROOM_ICON_SIZE
import com.yuroyami.syncplay.utils.platformCallback
import com.yuroyami.syncplay.viewmodel.PlatformCallback
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_overflow_leave_room
import syncplaymobile.shared.generated.resources.room_overflow_msghistory
import syncplaymobile.shared.generated.resources.room_overflow_pip
import syncplaymobile.shared.generated.resources.room_overflow_title

class TabController {
    val tabCardUserInfo = MutableStateFlow(false)
    val tabCardSharedPlaylist = MutableStateFlow(false)
    val tabCardRoomPreferences = MutableStateFlow(false)
    val tabLock = MutableStateFlow(false)

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
fun RoomTabSection(modifier: Modifier, tabController: TabController, onShowChatHistory: () -> Unit) {
    val viewmodel = LocalViewmodel.current

    val stateUserInfo by tabController.tabCardUserInfo.collectAsState()
    val stateSharedPlaylist by tabController.tabCardSharedPlaylist.collectAsState()
    val stateRoomPreferences by tabController.tabCardRoomPreferences.collectAsState()
    val stateRoomLock by tabController.tabLock.collectAsState()

    Row(
        modifier = modifier.fillMaxWidth().height(50.dp)
            .padding(top = 15.dp, end = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = CenterVertically
    ) {

        /* The tabs in the top-right corner */
        /* In-room settings */
        RoomTab(
            icon = Icons.Filled.AutoFixHigh,
            visibilityState = stateRoomPreferences,
            onClick = { tabController.toggleRoomPreferences() }
        )

        Spacer(modifier = Modifier.width(8.dp))

        /* Shared Playlist */
        if (!viewmodel.isSoloMode) {
            RoomTab(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                visibilityState = stateSharedPlaylist,
                onClick = {
                    tabController.toggleSharedPlaylist()
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            /* User Info card tab */
            RoomTab(
                icon = Icons.Filled.Groups,
                visibilityState = stateUserInfo, onClick = {
                    tabController.toggleUserInfo()
                }
            )

            Spacer(modifier = Modifier.width(8.dp))
        }

        /** Lock card */
        RoomTab(
            icon = Icons.Filled.Lock,
            visibilityState = false,
            onClick = {
                tabController.tabLock.value = true
                viewmodel.visibleHUD.value = false
            }
        )

        Spacer(Modifier.width(6.dp))

        Box {
            val overflowMenuState = remember { mutableStateOf(false) }
            FancyIcon2(
                icon = Icons.Filled.MoreVert,
                size = ROOM_ICON_SIZE,
                shadowColor = Color.Black
            ) {
                overflowMenuState.value = !overflowMenuState.value
            }

            DropdownMenu(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(
                    0.5f
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map {
                        it.copy(alpha = 0.5f)
                    })
                ),
                shape = RoundedCornerShape(8.dp),
                expanded = overflowMenuState.value,
                properties = PopupProperties(
                    dismissOnBackPress = true,
                    focusable = true,
                    dismissOnClickOutside = true
                ),
                onDismissRequest = { overflowMenuState.value = false }) {

                FancyText2(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .padding(horizontal = 2.dp),
                    string = stringResource(Res.string.room_overflow_title),
                    solid = Color.Black,
                    size = 14f,
                    font = syncplayFont
                )

                /* Picture-in-Picture mode */
                DropdownMenuItem(

                    text = {
                        Row(verticalAlignment = CenterVertically) {
                            Icon(
                                modifier = Modifier.padding(2.dp),
                                imageVector = Icons.Filled.PictureInPicture,
                                contentDescription = "",
                                tint = Color.LightGray
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(
                                color = Color.LightGray,
                                text = stringResource(Res.string.room_overflow_pip),
                            )
                        }
                    }, onClick = {
                        overflowMenuState.value = false

                        platformCallback.onPictureInPicture(true)
                    })

                /* Chat history item */
                if (!viewmodel.isSoloMode) {
                    DropdownMenuItem(text = {
                        Row(verticalAlignment = CenterVertically) {
                            Icon(
                                modifier = Modifier.padding(2.dp),
                                imageVector = Icons.Filled.Forum,
                                contentDescription = "",
                                tint = Color.LightGray
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(
                                color = Color.LightGray,
                                text = stringResource(Res.string.room_overflow_msghistory),
                            )
                        }
                    }, onClick = {
                        overflowMenuState.value = false
                        onShowChatHistory()
                    })
                }

                /* Theme Changer */
                if (false) {//TODO
                    DropdownMenuItem(text = {
                        Row(verticalAlignment = CenterVertically) {
                            Icon(
                                modifier = Modifier.padding(2.dp),
                                imageVector = Icons.Filled.DarkMode,
                                contentDescription = "",
                                tint = Color.LightGray
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(
                                color = Color.LightGray,
                                text = "Change Theme", //TODO Localize
                            )
                        }
                    }, onClick = {
                        overflowMenuState.value = false

                        //TODO Toggle Theme Changer Popup
                    })
                }

                /* Leave room item */
                DropdownMenuItem(text = {
                    Row(verticalAlignment = CenterVertically) {
                        Icon(
                            modifier = Modifier.padding(2.dp),
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "",
                            tint = Color.LightGray
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            color = Color.LightGray,
                            text = stringResource(Res.string.room_overflow_leave_room),
                        )
                    }
                }, onClick = {
                    viewmodel.p.endConnection(true)
                    viewmodel.player?.destroy()
                    viewmodel.nav.navigateTo(Screen.Home)
                    platformCallback.onRoomEnterOrLeave(PlatformCallback.RoomEvent.LEAVE)
                })
            }
        }
    }
}