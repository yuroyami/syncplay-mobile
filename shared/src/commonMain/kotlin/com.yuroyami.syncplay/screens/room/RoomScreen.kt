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
    val videoPicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = CommonUtils.vidExs)) { file ->
        file?.path?.let {
            loggy(it, 0)
            viewmodel.player?.injectVideo(it, false)
        }
    }

    val subtitlePicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = CommonUtils.ccExs)) { file ->
        file?.path?.let {
            viewmodel.player?.loadExternalSub(it)
        }
    }

        var ready by remember { mutableStateOf(viewmodel.setReadyDirectly) }
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

                    /* The cards below */
                    val cardWidth = 0.36f
                    val cardHeight = 0.72f
                    Box {
                        /** User-info card (toggled on and off) */
                        if (!isSoloMode) {
                            FreeAnimatedVisibility(
                                modifier = Modifier.fillMaxWidth(cardWidth)
                                    .fillMaxHeight(cardHeight),
                                enter = slideInHorizontally(initialOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
                                exit = slideOutHorizontally(targetOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
                                visible = !inroomprefsVisibility.value && userinfoVisibility.value && !sharedplaylistVisibility.value
                            ) {
                                UserInfoCard()
                            }
                        }

                        /** Shared Playlist card (toggled on and off) */
                        if (!isSoloMode) {
                            FreeAnimatedVisibility(
                                modifier = Modifier.fillMaxWidth(cardWidth)
                                    .fillMaxHeight(cardHeight),
                                enter = slideInHorizontally(initialOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
                                exit = slideOutHorizontally(targetOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
                                visible = !inroomprefsVisibility.value && !userinfoVisibility.value && sharedplaylistVisibility.value
                            ) {
                                SharedPlaylistCard()
                            }
                        }

                        /** In-room card (toggled on and off) */
                        FreeAnimatedVisibility(
                            modifier = Modifier.fillMaxWidth(cardWidth)
                                .fillMaxHeight(cardHeight),
                            enter = slideInHorizontally(initialOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
                            exit = slideOutHorizontally(targetOffsetX = { (screensizeinfo.widthPx * 1.3).toInt() }),
                            visible = inroomprefsVisibility.value && !userinfoVisibility.value && !sharedplaylistVisibility.value
                        ) {
                            InRoomSettingsCard()
                        }

                        /** Control card (to control the player) */
                        FreeAnimatedVisibility(
                            modifier = Modifier.zIndex(10f).wrapContentWidth()
                                .align(Alignment.CenterEnd).fillMaxHeight(cardHeight),
                            enter = expandIn(),
                            visible = controlcardvisible
                        ) {
                            /** CONTROL CARD ------ PLAYER CONTROL CARD ----- PLAYER CONTROL CARD */
                            Card(
                                modifier = Modifier.zIndex(10f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map {
                                        it.copy(alpha = 0.5f)
                                    })
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(
                                        0.5f
                                    )
                                ),
                            ) {
                                FlowColumn(
                                    modifier = Modifier.padding(8.dp).fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    /* Aspect Ratio */
                                    FancyIcon2(
                                        icon = Icons.Filled.AspectRatio,
                                        size = ROOM_ICON_SIZE,
                                        shadowColor = Color.Black
                                    ) {
                                        composeScope.launch(Dispatchers.IO) {
                                            val newAspectRatio = viewmodel.player?.switchAspectRatio()
                                            if (newAspectRatio != null) {
                                                composeScope.dispatchOSD { newAspectRatio }
                                            }
                                        }
                                    }

                                    /* Seek Gesture (DoNotTouch for disabling it) */
                                    FancyIcon2(
                                        icon = when (gestures.value) {
                                            true -> Icons.Filled.TouchApp
                                            false -> Icons.Filled.DoNotTouch
                                        }, size = ROOM_ICON_SIZE, shadowColor = Color.Black
                                    ) {
                                        composeScope.launch {
                                            writeValue(MISC_GESTURES, !gestures.value)
                                            dispatchOSD { if (gestures.value) "Gestures enabled" else "Gestures disabled" }
                                        }
                                    }

                                    /* Seek To */
                                    FancyIcon2(
                                        icon = Icons.Filled.BrowseGallery,
                                        size = ROOM_ICON_SIZE,
                                        shadowColor = Color.Black
                                    ) {
                                        controlcardvisible = false
                                        seektopopupstate.value = true
                                    }

                                    /* Undo Last Seek */
                                    FancyIcon2(
                                        icon = Icons.Filled.History,
                                        size = ROOM_ICON_SIZE,
                                        shadowColor = Color.Black
                                    ) {
                                        if (viewmodel.seeks.isEmpty()) {
                                            composeScope.dispatchOSD { "There is no recent seek in the room." }
                                            return@FancyIcon2
                                        }

                                        controlcardvisible = false

                                        val lastSeek = viewmodel.seeks.lastOrNull() ?: return@FancyIcon2
                                        viewmodel.player?.seekTo(lastSeek.first)
                                        viewmodel.sendSeek(lastSeek.first)
                                        viewmodel.seeks.remove(lastSeek)
                                        composeScope.dispatchOSD { "Seek undone." }
                                    }

                                    /* Subtitle Tracks */
                                    Box {
                                        val tracksPopup = remember { mutableStateOf(false) }


                                        FancyIcon2(
                                            icon = Icons.Filled.Subtitles,
                                            size = ROOM_ICON_SIZE,
                                            shadowColor = Color.Black
                                        ) {
                                            composeScope.launch {
                                                viewmodel.player?.analyzeTracks(
                                                    viewmodel.media ?: return@launch
                                                )
                                                tracksPopup.value = true
                                            }
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
                                            expanded = tracksPopup.value,
                                            properties = PopupProperties(
                                                dismissOnBackPress = true,
                                                focusable = true,
                                                dismissOnClickOutside = true
                                            ),
                                            onDismissRequest = {
                                                tracksPopup.value = !tracksPopup.value
                                            }) {

                                            ComposeUtils.FancyText2(
                                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                                string = "Subtitle Track",
                                                solid = Color.Black,
                                                size = 14f,
                                                font = directive
                                            )

                                            DropdownMenuItem(text = {
                                                Row(verticalAlignment = CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                                        "",
                                                        tint = Color.LightGray
                                                    )

                                                    Spacer(Modifier.width(2.dp))

                                                    Text(
                                                        "Import from file",
                                                        color = Color.LightGray
                                                    )
                                                }
                                            }, onClick = {
                                                tracksPopup.value = false
                                                controlcardvisible = false

                                                subtitlePicker.launch()
                                            })


                                            DropdownMenuItem(text = {
                                                Row(verticalAlignment = CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Filled.SubtitlesOff,
                                                        "",
                                                        tint = Color.LightGray
                                                    )

                                                    Spacer(Modifier.width(2.dp))

                                                    Text(
                                                        "Disable subtitles.",
                                                        color = Color.LightGray
                                                    )

                                                }
                                            }, onClick = {
                                                viewmodel.player?.selectTrack(
                                                    null, TRACKTYPE.SUBTITLE
                                                )
                                                tracksPopup.value = false
                                                controlcardvisible = false

                                            })

                                            for (track in (viewmodel.media?.subtitleTracks)
                                                ?: listOf()) {
                                                DropdownMenuItem(text = {
                                                    Row(verticalAlignment = CenterVertically) {
                                                        Checkbox(
                                                            checked = track.selected.value,
                                                            onCheckedChange = {
                                                                viewmodel.player?.selectTrack(
                                                                    track, TRACKTYPE.SUBTITLE
                                                                )
                                                                tracksPopup.value = false
                                                            })

                                                        Text(
                                                            color = Color.LightGray,
                                                            text = track.name
                                                        )
                                                    }
                                                }, onClick = {
                                                    viewmodel.player?.selectTrack(
                                                        track, TRACKTYPE.SUBTITLE
                                                    )

                                                    tracksPopup.value = false
                                                    controlcardvisible = false

                                                })
                                            }
                                        }
                                    }

                                    /* Audio Tracks */
                                    Box {
                                        val tracksPopup = remember { mutableStateOf(false) }

                                        FancyIcon2(
                                            icon = Icons.Filled.SpeakerGroup,
                                            size = ROOM_ICON_SIZE,
                                            shadowColor = Color.Black
                                        ) {
                                            composeScope.launch {
                                                viewmodel.player?.analyzeTracks(
                                                    viewmodel.media ?: return@launch
                                                )
                                                tracksPopup.value = true
                                            }
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
                                            expanded = tracksPopup.value,
                                            properties = PopupProperties(
                                                dismissOnBackPress = true,
                                                focusable = true,
                                                dismissOnClickOutside = true
                                            ),
                                            onDismissRequest = {
                                                tracksPopup.value = !tracksPopup.value
                                            }) {

                                            ComposeUtils.FancyText2(
                                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                                string = "Audio Track",
                                                solid = Color.Black,
                                                size = 14f,
                                                font = directive
                                            )

                                            for (track in (viewmodel.media?.audioTracks
                                                ?: listOf())) {
                                                DropdownMenuItem(text = {
                                                    Row(verticalAlignment = CenterVertically) {
                                                        Checkbox(
                                                            checked = track.selected.value,
                                                            onCheckedChange = {
                                                                viewmodel.player?.selectTrack(
                                                                    track, TRACKTYPE.AUDIO
                                                                )
                                                                tracksPopup.value = false
                                                            })

                                                        Text(
                                                            color = Color.LightGray,
                                                            text = track.name
                                                        )
                                                    }
                                                }, onClick = {
                                                    viewmodel.player?.selectTrack(
                                                        track, TRACKTYPE.AUDIO
                                                    )
                                                    tracksPopup.value = false
                                                })
                                            }
                                        }
                                    }


                                    /* Chapters */
                                    if (viewmodel.player?.supportsChapters == true) {
                                        Box {
                                            var chaptersPopup by remember { mutableStateOf(false) }

                                            FancyIcon2(
                                                icon = Icons.Filled.Theaters,
                                                size = ROOM_ICON_SIZE,
                                                shadowColor = Color.Black
                                            ) {
                                                composeScope.launch {
                                                    viewmodel.player?.analyzeChapters(
                                                        viewmodel.media ?: return@launch
                                                    )
                                                    chaptersPopup = !chaptersPopup
                                                }
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
                                                expanded = chaptersPopup,
                                                properties = PopupProperties(
                                                    dismissOnBackPress = true,
                                                    focusable = true,
                                                    dismissOnClickOutside = true
                                                ),
                                                onDismissRequest = { chaptersPopup = false }) {

                                                ComposeUtils.FancyText2(
                                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                                    string = "Chapters",
                                                    solid = Color.Black,
                                                    size = 14f,
                                                    font = directive
                                                )

                                                DropdownMenuItem(text = {
                                                    Row(verticalAlignment = CenterVertically) {
                                                        Text(
                                                            color = Color.LightGray,
                                                            text = "Skip chapter"
                                                        )
                                                    }
                                                }, onClick = {
                                                    viewmodel.player?.skipChapter()
                                                    chaptersPopup = false
                                                })

                                                for (chapter in (viewmodel.media?.chapters
                                                    ?: listOf())) {
                                                    DropdownMenuItem(text = {
                                                        Row(verticalAlignment = CenterVertically) {
                                                            Text(
                                                                color = Color.LightGray,
                                                                text = chapter.name
                                                            )
                                                        }
                                                    }, onClick = {
                                                        viewmodel.player?.jumpToChapter(chapter)
                                                        chaptersPopup = false
                                                    })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (hasVideo.value) {/* Bottom-left row (Ready button) */
                    if (!isSoloMode) {
                        IconToggleButton(
                            modifier = Modifier.width(112.dp).padding(4.dp)
                                .align(Alignment.BottomStart),
                            checked = ready,
                            colors = IconButtonDefaults.iconToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = Color.Black
                            ),
                            onCheckedChange = { b ->
                                ready = b
                                viewmodel.p.ready = b
                                viewmodel.p.send<Packet.Readiness> {
                                    isReady = b
                                    manuallyInitiated = true
                                }
                            }) {
                            when (ready) {
                                true -> Row(verticalAlignment = CenterVertically) {
                                    Icon(
                                        modifier = Modifier.size(Paletting.USER_INFO_IC_SIZE.dp),
                                        imageVector = if (ready) Icons.Filled.Check else Icons.Filled.Clear,
                                        contentDescription = "",
                                        tint = Paletting.ROOM_USER_READY_ICON


                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Ready", fontSize = 14.sp)
                                    Spacer(Modifier.width(4.dp))

                                }

                                false -> Row(verticalAlignment = CenterVertically) {
                                    Icon(
                                        modifier = Modifier.size(Paletting.USER_INFO_IC_SIZE.dp),
                                        imageVector = if (ready) Icons.Filled.Check else Icons.Filled.Clear,
                                        contentDescription = "",
                                        tint = Paletting.ROOM_USER_UNREADY_ICON

                                    )
                                    Spacer(Modifier.width(4.dp))

                                    Text("Not Ready", fontSize = 13.sp)
                                    Spacer(Modifier.width(4.dp))

                                }
                            }
                        }
                    }

                    val chapters = remember(
                        viewmodel.media?.fileName
                    ) { viewmodel.media?.chapters ?: emptyList() }


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
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (!gestures.value) {
                                        FancyIcon2(
                                            icon = Icons.Filled.FastRewind,
                                            size = ROOM_ICON_SIZE + 6,
                                            shadowColor = Color.Black
                                        ) {
                                            viewmodel.seekBckwd()
                                        }

                                        FancyIcon2(
                                            icon = Icons.Filled.FastForward,
                                            size = ROOM_ICON_SIZE + 6,
                                            shadowColor = Color.Black
                                        ) {
                                            viewmodel.seekFrwrd()
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
                                                brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map {
                                                    it.copy(alpha = 0.1f)
                                                }), shape = CircleShape
                                            ),
                                            onClick = {
                                                viewmodel.player?.playerScopeIO?.launch {
                                                    val currentMs =
                                                        withContext(Dispatchers.Main) { viewmodel.player!!.currentPositionMs() }
                                                    val newPos =
                                                        (currentMs) + (customSkipAmount * 1000L)

                                                    viewmodel.sendSeek(newPos)
                                                    viewmodel.player?.seekTo(newPos)

                                                    if (isSoloMode) {
                                                        viewmodel.seeks.add(
                                                            Pair(
                                                                (currentMs), newPos * 1000
                                                            )
                                                        )
                                                    }

                                                    dispatchOSD {
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
                                                brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map {
                                                    it.copy(alpha = 0.1f)
                                                }), shape = CircleShape
                                            ), onClick = {
                                                viewmodel.player?.playerScopeIO?.launch {
                                                    val currentMs =
                                                        withContext(Dispatchers.Main) { viewmodel.player!!.currentPositionMs() }
                                                    val nextChapter =
                                                        viewmodel.media?.chapters?.filter { it.timestamp > currentMs }
                                                            ?.minByOrNull { it.timestamp }
                                                    if (nextChapter != null) {
                                                        viewmodel.player?.seekTo(nextChapter.timestamp)
                                                    } else {
                                                        // fallback if no chapter is ahead
                                                        viewmodel.player?.seekTo(currentMs + (customSkipAmount * 1000L))
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

                            val timeFullR = remember { viewmodel.timeFull }
                            Text(
                                text = if (timeFullR.longValue >= Long.MAX_VALUE / 1000L) "???" else timeStamper(
                                    timeFullR.longValue
                                ),
                                modifier = Modifier.alpha(0.85f).gradientOverlay(),
                            )

                        }
                        LaunchedEffect(viewmodel.media?.fileName) {
                            composeScope.launch {
                                viewmodel.player?.analyzeChapters(
                                    viewmodel.media ?: return@launch
                                )
                            }
                        }
                        Slider(
                            value = slidervalue.toFloat(),
                            valueRange = (0f..(slidermax.toFloat())),
                            onValueChange = { f ->
                                viewmodel.player?.seekTo(f.toLong() * 1000L)
                                if (isSoloMode) {
                                    viewmodel.player?.let {
                                        composeScope.launch(Dispatchers.Main) {
                                            viewmodel.seeks.add(
                                                Pair(
                                                    it.currentPositionMs(), f.toLong() * 1000
                                                )
                                            )
                                        }
                                    }
                                }

                                slidervalue = f.toLong()
                            },
                            onValueChangeFinished = {
                                viewmodel.sendSeek(slidervalue * 1000L)
                            },
                            modifier = Modifier.alpha(0.82f).fillMaxWidth(0.75f)
                                .padding(horizontal = 12.dp),
                            interactionSource = interactionSource,
                            steps = 500,
                            thumb = {
                                SliderDefaults.Thumb(
                                    interactionSource = interactionSource,
                                    colors = SliderDefaults.colors(),
                                    modifier = Modifier.alpha(0.6f)
                                )
                            },
                            track = { sliderState ->
                                // Assuming media duration is available in ms
                                val mediaDuration = viewmodel.media?.fileDuration ?: 1L

                                // Capture track width
                                var trackWidth by remember { mutableStateOf(0) }

                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .onSizeChanged { trackWidth = it.width }) {
                                    // Draw the slider track
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        modifier = Modifier.fillMaxWidth()
                                            .scale(scaleX = 1F, scaleY = 0.85F),
                                        thumbTrackGapSize = 0.dp,
                                        drawStopIndicator = null,
                                        drawTick = { _, _ -> })


                                    chapters.forEach { chapter ->
                                        if (chapter.timestamp / 1000 != 0L) {
                                            // Calculate horizontal position fraction based on chapter timestamp
                                            val positionFraction =
                                                (chapter.timestamp / mediaDuration.toFloat()) / 1000
                                            Box(
                                                modifier = Modifier.offset {
                                                    // 4.dp to pixels
                                                    val offsetAdjustment =
                                                        with(density) { 4.dp.toPx() }
                                                    IntOffset(
                                                        (positionFraction * trackWidth).toInt() - offsetAdjustment.toInt(),
                                                        0
                                                    )
                                                }.align(Alignment.CenterStart)

                                                    .size(8.dp).clip(CircleShape)
                                                    .background(Color.White).clickable {
                                                        viewmodel.player?.jumpToChapter(chapter)

                                                    })
                                        }
                                    }

                                }
                            })
                    }
                }

                /* Bottom-right row (Controls) */
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                ) {
                    if (hasVideo.value) {
                        FancyIcon2(
                            modifier = Modifier.offset(y = (-2).dp),
                            icon = Icons.Filled.VideoSettings,
                            size = ROOM_ICON_SIZE + 6,
                            shadowColor = Color.Black,
                            onClick = {
                                controlcardvisible = !controlcardvisible
                                addmediacardvisible = false
                            })
                    }

                    /* The button of adding media */
                    Box {
                        AddVideoButton(
                            modifier = Modifier,
                            expanded = viewmodel.hasVideoG?.value != true,
                            onClick = {
                                addmediacardvisible = !addmediacardvisible
                                controlcardvisible = false
                            }
                        )

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
                            expanded = addmediacardvisible,
                            properties = PopupProperties(
                                dismissOnBackPress = true,
                                focusable = true,
                                dismissOnClickOutside = true
                            ),
                            onDismissRequest = { addmediacardvisible = false }) {

                            ComposeUtils.FancyText2(
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                    .padding(horizontal = 2.dp),
                                string = "Add media",
                                solid = Color.Black,
                                size = 14f,
                                font = directive
                            )

                            //From storage
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = CenterVertically) {
                                    FancyIcon2(
                                        icon = Icons.Filled.CreateNewFolder,
                                        size = ROOM_ICON_SIZE,
                                        shadowColor = Color.Black
                                    ) {}
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        color = Color.LightGray,
                                        text = stringResource(Res.string.room_addmedia_offline),
                                    )
                                }
                            }, onClick = {
                                addmediacardvisible = false
                                videoPicker.launch()
                            })

                            //From network URL
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = CenterVertically) {
                                    FancyIcon2(
                                        icon = Icons.Filled.AddLink,
                                        size = ROOM_ICON_SIZE,
                                        shadowColor = Color.Black
                                    ) {}
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        color = Color.LightGray,
                                        text = stringResource(Res.string.room_addmedia_online),
                                    )
                                }
                            }, onClick = {
                                addmediacardvisible = false
                                addurlpopupstate.value = true
                            })
                        }
                    }
                }


            }
    }
}