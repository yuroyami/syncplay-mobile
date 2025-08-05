@file:JvmName("RoomScreenUIKt")

package com.yuroyami.syncplay.screens.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Bottom
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.yuroyami.syncplay.components.ComposeUtils
import com.yuroyami.syncplay.components.ComposeUtils.FancyIcon2
import com.yuroyami.syncplay.components.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.components.cards.CardRoomPrefs.InRoomSettingsCard
import com.yuroyami.syncplay.components.cards.CardSharedPlaylist.SharedPlaylistCard
import com.yuroyami.syncplay.components.cards.CardUserInfo.UserInfoCard
import com.yuroyami.syncplay.components.getSyncplayFont
import com.yuroyami.syncplay.components.popups.PopupAddUrl.AddUrlPopup
import com.yuroyami.syncplay.components.popups.PopupChatHistory.ChatHistoryPopup
import com.yuroyami.syncplay.components.popups.PopupSeekToPosition.SeekToPositionPopup
import com.yuroyami.syncplay.player.BasePlayer.TRACKTYPE
import com.yuroyami.syncplay.protocol.sending.Packet
import com.yuroyami.syncplay.screens.adam.LocalNavigator
import com.yuroyami.syncplay.screens.adam.LocalScreenSize
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.screens.adam.Screen
import com.yuroyami.syncplay.screens.adam.Screen.Companion.navigateTo
import com.yuroyami.syncplay.screens.room.subcomponents.PingIndicator
import com.yuroyami.syncplay.settings.DataStoreKeys.MISC_GESTURES
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PLAYER_CUSTOM_SEEK_AMOUNT
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PLAYER_CUSTOM_SEEK_FRONT
import com.yuroyami.syncplay.settings.valueAsState
import com.yuroyami.syncplay.settings.valueFlow
import com.yuroyami.syncplay.settings.writeValue
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.ui.Paletting.ROOM_ICON_SIZE
import com.yuroyami.syncplay.utils.CommonUtils
import com.yuroyami.syncplay.utils.CommonUtils.beginPingUpdate
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.platformCallback
import com.yuroyami.syncplay.utils.timeStamper
import com.yuroyami.syncplay.viewmodel.PlatformCallback
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_addmedia_offline
import syncplaymobile.shared.generated.resources.room_addmedia_online
import syncplaymobile.shared.generated.resources.room_custom_skip_button
import syncplaymobile.shared.generated.resources.room_details_current_room
import syncplaymobile.shared.generated.resources.room_overflow_leave_room
import syncplaymobile.shared.generated.resources.room_overflow_msghistory
import syncplaymobile.shared.generated.resources.room_overflow_pip
import syncplaymobile.shared.generated.resources.room_overflow_title
import syncplaymobile.shared.generated.resources.room_overflow_toggle_nightmode
import syncplaymobile.shared.generated.resources.room_ping_connected
import syncplaymobile.shared.generated.resources.room_ping_disconnected
import kotlin.math.roundToInt

val osdMsg = mutableStateOf("")
var osdJob: Job? = null

fun CoroutineScope.dispatchOSD(getter: suspend () -> String) {
    osdJob?.cancel(null)
    osdJob = launch(Dispatchers.IO) {
        osdMsg.value = getter()
        delay(2000) //TODO Option to change delay
        osdMsg.value = ""
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoomScreenUI2() {
    val nav = LocalNavigator.current
    val viewmodel = LocalViewmodel.current
    val isSoloMode = viewmodel.isSoloMode


    val composeScope = rememberCoroutineScope { Dispatchers.IO }
    val density = LocalDensity.current

    val focusManager = LocalFocusManager.current
    val screensizeinfo = LocalScreenSize.current


    /* Some file picking stuff */


    val subtitlePicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = CommonUtils.ccExs)) { file ->
        file?.path?.let {
            viewmodel.player?.loadExternalSub(it)
        }
    }


        var controlcardvisible by remember { mutableStateOf(false) }
        var addmediacardvisible by remember { mutableStateOf(viewmodel.media?.fileName.isNullOrEmpty() && viewmodel.p.session.sharedPlaylist.isEmpty() && viewmodel.p.session.spIndex.value == -1) }

        val gestures = valueFlow(MISC_GESTURES, true).collectAsState(initial = true)




        AnimatedVisibility(hudVisibility, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {


                /* Card tabs (Top-right row) and the Cards below them  */
                Column(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalAlignment = Alignment.End
                ) {
                    /* The tabs row */

                    Spacer(Modifier.height(20.dp))

                        /** Control card (to control the player) */
                        FreeAnimatedVisibility(
                            modifier = Modifier.zIndex(10f).wrapContentWidth()
                                .align(Alignment.CenterEnd).fillMaxHeight(cardHeight),
                            enter = expandIn(),
                            visible = controlcardvisible
                        ) {

                            }
                        }
                    }
                }

                if (hasVideo.value) {/* Bottom-left row (Ready button) */




                    /* Bottom-mid row (Slider + seek buttons + timestamps) */
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp)
                            .fillMaxWidth()
                    ) {
                        var slidervalue by remember { viewmodel.timeCurrent }
                        val slidermax by remember { viewmodel.timeFull }
                        val interactionSource = remember { MutableInteractionSource() }

                        Row(
                            modifier = Modifier.fillMaxWidth(0.75f), verticalAlignment = Bottom
                        ) {
                            Text(
                                text = timeStamper(remember { viewmodel.timeCurrent }.longValue),
                                modifier = Modifier.alpha(0.85f).gradientOverlay(),
                            )

                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                            }

                            val timeFullR = remember { viewmodel.timeFull }
                            Text(
                                text = if (timeFullR.longValue >= Long.MAX_VALUE / 1000L) "???" else timeStamper(
                                    timeFullR.longValue
                                ),
                                modifier = Modifier.alpha(0.85f).gradientOverlay(),
                            )

                        }

                    }
                }
            }
    }
}