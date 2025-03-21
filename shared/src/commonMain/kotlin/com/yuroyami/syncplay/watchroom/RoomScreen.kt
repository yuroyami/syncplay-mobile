package com.yuroyami.syncplay.watchroom

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Bottom
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.yuroyami.syncplay.compose.ComposeUtils
import com.yuroyami.syncplay.compose.ComposeUtils.ChatAnnotatedText
import com.yuroyami.syncplay.compose.ComposeUtils.FancyIcon2
import com.yuroyami.syncplay.compose.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.compose.cards.CardRoomPrefs.InRoomSettingsCard
import com.yuroyami.syncplay.compose.cards.CardSharedPlaylist.SharedPlaylistCard
import com.yuroyami.syncplay.compose.cards.CardUserInfo.UserInfoCard
import com.yuroyami.syncplay.compose.getSyncplayFont
import com.yuroyami.syncplay.compose.popups.PopupAddUrl.AddUrlPopup
import com.yuroyami.syncplay.compose.popups.PopupChatHistory.ChatHistoryPopup
import com.yuroyami.syncplay.compose.popups.PopupSeekToPosition.SeekToPositionPopup
import com.yuroyami.syncplay.filepicking.FilePicker
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.player.BasePlayer.TRACKTYPE
import com.yuroyami.syncplay.player.PlayerUtils.pausePlayback
import com.yuroyami.syncplay.player.PlayerUtils.playPlayback
import com.yuroyami.syncplay.player.PlayerUtils.seekBckwd
import com.yuroyami.syncplay.player.PlayerUtils.seekFrwrd
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.settings.DataStoreKeys.MISC_GESTURES
import com.yuroyami.syncplay.settings.DataStoreKeys.MISC_NIGHTMODE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_BG_OPACITY
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_BOX_ACTION
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_FONTSIZE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_MAXCOUNT
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_OUTLINE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_MSG_SHADOW
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PLAYER_CUSTOM_SEEK_AMOUNT
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PLAYER_CUSTOM_SEEK_FRONT
import com.yuroyami.syncplay.settings.SettingCategory
import com.yuroyami.syncplay.settings.settingBooleanState
import com.yuroyami.syncplay.settings.settingIntState
import com.yuroyami.syncplay.settings.sgROOM
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueFlow
import com.yuroyami.syncplay.settings.writeValue
import com.yuroyami.syncplay.ui.AppTheme
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.ui.Paletting.ROOM_ICON_SIZE
import com.yuroyami.syncplay.utils.CommonUtils
import com.yuroyami.syncplay.utils.RoomUtils
import com.yuroyami.syncplay.utils.RoomUtils.sendSeek
import com.yuroyami.syncplay.utils.ScreenSizeInfo
import com.yuroyami.syncplay.utils.getScreenSizeInfo
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.timeStamper
import com.yuroyami.syncplay.watchroom.RoomComposables.AddVideoButton
import com.yuroyami.syncplay.watchroom.RoomComposables.ComposedMessagePalette
import com.yuroyami.syncplay.watchroom.RoomComposables.FreeAnimatedVisibility
import com.yuroyami.syncplay.watchroom.RoomComposables.PingRadar
import com.yuroyami.syncplay.watchroom.RoomComposables.RoomArtwork
import com.yuroyami.syncplay.watchroom.RoomComposables.RoomTab
import com.yuroyami.syncplay.watchroom.RoomComposables.fadingMessageLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

val osdMsg = mutableStateOf("")
var osdJob: Job? = null
fun CoroutineScope.dispatchOSD(s: String) {
    osdJob?.cancel(null)
    osdJob = launch(Dispatchers.IO) {
        osdMsg.value = s
        delay(2000) //TODO Option to change delay
        osdMsg.value = ""
    }
}

val LocalScreenSize = compositionLocalOf<ScreenSizeInfo> { error("No Screen Size Info provided") }
val LocalChatPalette = compositionLocalOf<MessagePalette> { error("No Chat Palette provided") }
val LocalRoomSettings =
    staticCompositionLocalOf<MutableList<SettingCategory>> { error("No Room Settings provided") }

@Composable
fun RoomUI() {
    CompositionLocalProvider(
        LocalRoomSettings provides sgROOM(),
        LocalScreenSize provides getScreenSizeInfo(),
        LocalChatPalette provides ComposedMessagePalette()
    ) {
        RoomUIImpl()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RoomUIImpl() {

    val nightMode = valueFlow(MISC_NIGHTMODE, true).collectAsState(initial = true)

    val directive = getSyncplayFont()

    val composeScope = rememberCoroutineScope { Dispatchers.IO }

    LaunchedEffect(null) {
        /** Starting ping update */
        if (viewmodel?.pingUpdateJob == null && !isSoloMode) {
            viewmodel?.pingUpdateJob = composeScope.launch(Dispatchers.IO) {
                CommonUtils.beginPingUpdate()
            }
        }
    }

    AppTheme(nightMode.value) {
        val focusManager = LocalFocusManager.current
        val screensizeinfo = LocalScreenSize.current

        val hasVideo = remember { viewmodel!!.hasVideoG }
        var hudVisibility by remember { viewmodel!!.hudVisibilityState }

        val pipModeObserver by remember { viewmodel!!.pipMode }
        var locked by remember { mutableStateOf(false) }

        val addurlpopupstate = remember { mutableStateOf(false) }
        val chathistorypopupstate = remember { mutableStateOf(false) }
        val seektopopupstate = remember { mutableStateOf(false) }

        val msgBoxOpacity = PREF_INROOM_MSG_BG_OPACITY.settingIntState()
        val msgOutline by PREF_INROOM_MSG_OUTLINE.settingBooleanState()
        val msgShadow by PREF_INROOM_MSG_SHADOW.settingBooleanState()
        val msgFontSize = PREF_INROOM_MSG_FONTSIZE.settingIntState()
        val msgMaxCount by PREF_INROOM_MSG_MAXCOUNT.settingIntState()
        val keyboardOkFunction by PREF_INROOM_MSG_BOX_ACTION.settingBooleanState()

        /* Some file picking stuff */
        var showVideoPicker by remember { mutableStateOf(false) }
        FilePicker(show = showVideoPicker, fileExtensions = CommonUtils.vidExs) { file ->
            showVideoPicker = false
            file?.path?.let {
                loggy(it, 0)
                viewmodel?.player?.injectVideo(it, false)
            }
        }

        var showSubtitlePicker by remember { mutableStateOf(false) }
        FilePicker(show = showSubtitlePicker, fileExtensions = CommonUtils.ccExs) { file ->
            showSubtitlePicker = false
            file?.path?.let {
                viewmodel?.player?.loadExternalSub(it)
            }
        }

        /** Room artwork underlay (when no video is loaded) */
        if (!hasVideo.value) {
            RoomArtwork(pipModeObserver)
        }

        /** video surface */
        viewmodel?.player?.VideoPlayer(Modifier.fillMaxSize().alpha(if (hasVideo.value) 1f else 0f))

        /** Lock layout, This is what appears when the user locks the screen */
        val unlockButtonVisibility = remember { mutableStateOf(false) }

        if (locked) {
            /** The touch interceptor to switch unlock button visibility */
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        unlockButtonVisibility.value = !unlockButtonVisibility.value
                    })
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(top = 15.dp, end = 44.dp),
                horizontalAlignment = Alignment.End
            ) {
                /** Unlock Card */
                if (unlockButtonVisibility.value && !pipModeObserver) {
                    Card(
                        modifier = Modifier.width(48.dp).alpha(0.5f).aspectRatio(1f).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = Paletting.SP_ORANGE)
                        ) {
                            locked = false
                            hudVisibility = true
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)
                        ),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.NoEncryption,
                                contentDescription = "",
                                modifier = Modifier.size(32.dp).align(Alignment.Center)
                                    .gradientOverlay()
                            )
                        }
                    }
                }
            }
        } else {
            /** ACTUAL ROOM HUD AND LAYOUT */

            var msg by remember { mutableStateOf("") }
            var msgCanSend by remember { mutableStateOf(false) }
            val msgs =
                remember { if (!isSoloMode) viewmodel!!.p.session.messageSequence else mutableStateListOf() }
            var ready by remember { mutableStateOf(viewmodel!!.setReadyDirectly) }
            var controlcardvisible by remember { mutableStateOf(false) }
            var addmediacardvisible by remember { mutableStateOf(false) }

            val gestures = valueFlow(MISC_GESTURES, true).collectAsState(initial = true)

            val userinfoVisibility = remember { mutableStateOf(false) }
            val sharedplaylistVisibility = remember { mutableStateOf(false) }
            val inroomprefsVisibility = remember { mutableStateOf(false) }

            if (!viewmodel!!.startupSlide) {
                LaunchedEffect(null) {
                    composeScope.launch {
                        delay(600)
                        userinfoVisibility.value = true
                        viewmodel!!.startupSlide = true
                    }
                }
            }

            AnimatedVisibility(hudVisibility, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    /* Top row (Message input box + Messages) */
                    Column(
                        modifier = Modifier.fillMaxWidth(0.32f).align(Alignment.TopStart),
                        horizontalAlignment = Alignment.Start
                    ) {
                        /* Messages */
                        if (!isSoloMode) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(
                                    color = if (hasVideo.value || MaterialTheme.colorScheme.primary != Paletting.OLD_SP_YELLOW) Color(
                                        50, 50, 50, msgBoxOpacity.value
                                    ) else Color.Transparent
                                ).padding(top = 64.dp)
                            ) {
                                //val lastMessages = msgs.toList().takeLast(msgMaxCount)
                                val lastMessages = msgs.takeLast(msgMaxCount)

                                LazyColumn(
                                    contentPadding = PaddingValues(8.dp),
                                    userScrollEnabled = false,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(lastMessages) {
                                        LaunchedEffect(null) {
                                            it.seen =
                                                true /* Once seen, don't use it in fading message */
                                        }

                                        ChatAnnotatedText(
                                            modifier = Modifier.fillMaxWidth(),
                                            text = it.factorize(LocalChatPalette.current),
                                            size = if (pipModeObserver) 6f else (msgFontSize.value.toFloat()),
                                            hasShadow = msgShadow,
                                            hasStroke = msgOutline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /* Gestures Interceptor */
            GestureInterceptor(
                gestureState = gestures, videoState = viewmodel!!.hasVideoG, onSingleTap = {
                    hudVisibility = !hudVisibility
                    if (!hudVisibility) {/* Hide any popups */
                        controlcardvisible = false
                        addmediacardvisible = false
                    }
                })

            /* HUD below: We resort to using a combination of Boxes, Rows, and Columns. */
            AnimatedVisibility(
                hudVisibility && hasVideo.value, enter = fadeIn(), exit = fadeOut()
            ) {
                Box(Modifier.fillMaxSize()) {
                    val blacky = Color.Black.copy(alpha = 0.8F)
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .height(84.dp).background(
                                brush = Brush.verticalGradient(
                                    listOf(Color.Transparent, blacky, blacky, blacky)
                                )
                            ).clickable(false) {})
                }
            }

            AnimatedVisibility(hudVisibility, enter = fadeIn(), exit = fadeOut()) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    /* Top row (Message input box + Messages) */
                    Column(
                        modifier = Modifier.fillMaxWidth(0.32f).align(Alignment.TopStart),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            /** Message input box */
                            if (!isSoloMode && !pipModeObserver) {
                                val onSend = {
                                    val msgToSend = msg.run {
                                        var t = replace("\\", "")
                                        if (t.length > 150) t = t.substring(0, 149)
                                        t
                                    }
                                    if (msgToSend.isNotBlank()) {
                                        RoomUtils.sendMessage(msgToSend)
                                    }
                                    msg = ""
                                    msgCanSend = false

                                    focusManager.clearFocus()
                                }

                                GradientTextField(
                                    value = msg,
                                    onValueChange = { s ->
                                        msg = s
                                        msgCanSend = s.isNotBlank()
                                    },
                                    label = lyricist.strings.roomTypeMessage,
                                    onSend = onSend,
                                    msgCanSend = msgCanSend,
                                    singleLine = true,
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (keyboardOkFunction) {
                                            onSend()
                                        }
                                    }),
                                    colors = Paletting.SP_GRADIENT
                                )
                            }
                        }
                    }

                    /* Top-Center info: Overall info (PING + ROOMNAME + OSD Messages) */
                    if (!isSoloMode && !pipModeObserver) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.wrapContentWidth().align(Alignment.TopCenter)
                        ) {
                            if (!isSoloMode) {
                                Row(verticalAlignment = CenterVertically) {
                                    val pingo by remember { viewmodel!!.p.ping }
                                    Text(

                                        text = if (pingo == null) lyricist.strings.roomPingDisconnected else lyricist.strings.roomPingConnected(
                                            pingo!!.toInt().toString()
                                        ), color = Paletting.OLD_SP_PINK
                                    )
                                    Spacer(Modifier.width(4.dp))

                                    PingRadar(pingo)
                                }

                                Text(
                                    text = lyricist.strings.roomDetailsCurrentRoom(viewmodel!!.p.session.currentRoom),
                                    fontSize = 11.sp,
                                    color = Paletting.OLD_SP_PINK
                                )
                                lyricist.strings.roomDetailsCurrentRoom
                            }

                            val osd by remember { osdMsg }
                            Text(
                                modifier = Modifier.fillMaxWidth(0.3f),
                                fontSize = 11.sp,
                                lineHeight = (Paletting.USER_INFO_TXT_SIZE + 4).sp,
                                color = Paletting.SP_PALE,
                                text = osd,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.W300
                            )
                        }
                    }

                    /* Card tabs (Top-right row) and the Cards below them  */
                    Column(
                        modifier = Modifier.align(Alignment.TopEnd),
                        horizontalAlignment = Alignment.End
                    ) {
                        /* The tabs row */
                        Row(
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                                .padding(top = 15.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = CenterVertically
                        ) {

                            /* The tabs in the top-right corner *//* In-room settings */
                            RoomTab(
                                icon = Icons.Filled.AutoFixHigh,
                                visibilityState = inroomprefsVisibility.value
                            ) {
                                sharedplaylistVisibility.value = false
                                userinfoVisibility.value = false
                                inroomprefsVisibility.value = !inroomprefsVisibility.value
                            }

                            Spacer(Modifier.width(12.dp))

                            /* Shared Playlist */
                            if (!isSoloMode) {
                                RoomTab(
                                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    visibilityState = sharedplaylistVisibility.value
                                ) {
                                    sharedplaylistVisibility.value = !sharedplaylistVisibility.value
                                    userinfoVisibility.value = false
                                    inroomprefsVisibility.value = false
                                }

                                Spacer(Modifier.width(12.dp))
                            }

                            /* User Info card tab */
                            if (!isSoloMode) {
                                RoomTab(
                                    icon = Icons.Filled.Groups,
                                    visibilityState = userinfoVisibility.value
                                ) {
                                    userinfoVisibility.value = !userinfoVisibility.value
                                    sharedplaylistVisibility.value = false
                                    inroomprefsVisibility.value = false
                                }

                                Spacer(Modifier.width(12.dp))
                            }


                            /** Lock card */
                            RoomTab(icon = Icons.Filled.Lock, visibilityState = false) {
                                locked = true
                                hudVisibility = false
                            }

                            Spacer(Modifier.width(6.dp))

                            Box {
                                val overflowmenustate = remember { mutableStateOf(false) }
                                FancyIcon2(
                                    icon = Icons.Filled.MoreVert,
                                    size = ROOM_ICON_SIZE,
                                    shadowColor = Color.Black
                                ) {
                                    overflowmenustate.value = !overflowmenustate.value
                                }

                                DropdownMenu(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(
                                        0.5f
                                    ),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
                                    shape = RoundedCornerShape(8.dp),
                                    expanded = overflowmenustate.value,
                                    properties = PopupProperties(
                                        dismissOnBackPress = true,
                                        focusable = true,
                                        dismissOnClickOutside = true
                                    ),
                                    onDismissRequest = { overflowmenustate.value = false }) {

                                    ComposeUtils.FancyText2(
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                            .padding(horizontal = 2.dp),
                                        string = lyricist.strings.roomOverflowTitle,
                                        solid = Color.Black,
                                        size = 14f,
                                        font = directive
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
                                                    text = lyricist.strings.roomOverflowPip
                                                )
                                            }
                                        }, onClick = {
                                            overflowmenustate.value = false

                                            viewmodel?.roomCallback?.onPictureInPicture(true)
                                        })

                                    /* Chat history item */
                                    if (!isSoloMode) {
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
                                                    text = lyricist.strings.roomOverflowMsghistory
                                                )
                                            }
                                        }, onClick = {
                                            overflowmenustate.value = false
                                            chathistorypopupstate.value = true
                                        })
                                    }

                                    /* Toggle Dark mode */
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
                                                text = lyricist.strings.roomOverflowToggleNightmode
                                            )
                                        }
                                    }, onClick = {
                                        overflowmenustate.value = false

                                        val newMode = !valueBlockingly(MISC_NIGHTMODE, true)

                                        composeScope.launch {
                                            writeValue(MISC_NIGHTMODE, newMode)
                                        }
                                    })

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
                                                text = lyricist.strings.roomOverflowLeaveRoom
                                            )
                                        }
                                    }, onClick = {
                                        viewmodel?.p?.endConnection(true)
                                        viewmodel?.player?.destroy()
                                        viewmodel?.roomCallback?.onLeave()
                                    })
                                }
                            }
                        }

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
                                    enter = slideInHorizontally(initialOffsetX = { (screensizeinfo.wPX * 1.3).toInt() }),
                                    exit = slideOutHorizontally(targetOffsetX = { (screensizeinfo.wPX * 1.3).toInt() }),
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
                                    enter = slideInHorizontally(initialOffsetX = { (screensizeinfo.wPX * 1.3).toInt() }),
                                    exit = slideOutHorizontally(targetOffsetX = { (screensizeinfo.wPX * 1.3).toInt() }),
                                    visible = !inroomprefsVisibility.value && !userinfoVisibility.value && sharedplaylistVisibility.value
                                ) {
                                    SharedPlaylistCard()
                                }
                            }

                            /** In-room card (toggled on and off) */
                            FreeAnimatedVisibility(
                                modifier = Modifier.fillMaxWidth(cardWidth)
                                    .fillMaxHeight(cardHeight),
                                enter = slideInHorizontally(initialOffsetX = { (screensizeinfo.wPX * 1.3).toInt() }),
                                exit = slideOutHorizontally(targetOffsetX = { (screensizeinfo.wPX * 1.3).toInt() }),
                                visible = inroomprefsVisibility.value && !userinfoVisibility.value && !sharedplaylistVisibility.value
                            ) {
                                InRoomSettingsCard()
                            }

                            /** Control card (to control the player) */
                            FreeAnimatedVisibility(
                                modifier = Modifier.zIndex(10f).wrapContentWidth()
                                    .align(Alignment.CenterEnd).fillMaxHeight(cardHeight ),
                                enter = expandIn(),
                                visible = controlcardvisible
                            ) {
                                /** CONTROL CARD ------ PLAYER CONTROL CARD ----- PLAYER CONTROL CARD */
                                Card(
                                    modifier = Modifier.zIndex(10f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f)),
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
                                            val newAspectRatio =
                                                viewmodel?.player?.switchAspectRatio()
                                            if (newAspectRatio != null) {
                                                composeScope.dispatchOSD(newAspectRatio)
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
                                                dispatchOSD(if (gestures.value) "Gestures enabled" else "Gestures disabled")
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
                                            if (viewmodel?.seeks?.isEmpty() == true) {
                                                composeScope.dispatchOSD("There is no recent seek in the room.")
                                                return@FancyIcon2
                                            }

                                            controlcardvisible = false

                                            val lastSeek =
                                                viewmodel?.seeks?.last() ?: return@FancyIcon2
                                            viewmodel?.player?.seekTo(lastSeek.first)
                                            sendSeek(lastSeek.first)
                                            viewmodel?.seeks?.remove(lastSeek)
                                            composeScope.dispatchOSD("Seek undone.")
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
                                                    viewmodel?.player?.analyzeTracks(
                                                        viewmodel?.media ?: return@launch
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
                                                border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
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

                                                    showSubtitlePicker = true
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
                                                    viewmodel?.player?.selectTrack(
                                                        null, TRACKTYPE.SUBTITLE
                                                    )
                                                    tracksPopup.value = false
                                                    controlcardvisible = false

                                                })

                                                for (track in (viewmodel?.media?.subtitleTracks)
                                                    ?: listOf()) {
                                                    DropdownMenuItem(text = {
                                                        Row(verticalAlignment = CenterVertically) {
                                                            Checkbox(
                                                                checked = track.selected.value,
                                                                onCheckedChange = {
                                                                    viewmodel?.player?.selectTrack(
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
                                                        viewmodel?.player?.selectTrack(
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
                                                    viewmodel?.player?.analyzeTracks(
                                                        viewmodel?.media ?: return@launch
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
                                                border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
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

                                                for (track in (viewmodel?.media?.audioTracks
                                                    ?: listOf())) {
                                                    DropdownMenuItem(text = {
                                                        Row(verticalAlignment = CenterVertically) {
                                                            Checkbox(
                                                                checked = track.selected.value,
                                                                onCheckedChange = {
                                                                    viewmodel?.player?.selectTrack(
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
                                                        viewmodel?.player?.selectTrack(
                                                            track, TRACKTYPE.AUDIO
                                                        )
                                                        tracksPopup.value = false
                                                    })
                                                }
                                            }
                                        }


                                        /* Chapters */
                                        if (viewmodel?.player?.supportsChapters == true) {
                                            Box {
                                                var chaptersPopup by remember { mutableStateOf(false) }

                                                FancyIcon2(
                                                    icon = Icons.Filled.Theaters,
                                                    size = ROOM_ICON_SIZE,
                                                    shadowColor = Color.Black
                                                ) {
                                                    composeScope.launch {
                                                        viewmodel?.player?.analyzeChapters(
                                                            viewmodel?.media ?: return@launch
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
                                                    border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
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
                                                        viewmodel?.player?.skipChapter()
                                                        chaptersPopup = false
                                                    })

                                                    for (chapter in (viewmodel?.media?.chapters
                                                        ?: listOf())) {
                                                        DropdownMenuItem(text = {
                                                            Row(verticalAlignment = CenterVertically) {
                                                                Text(
                                                                    color = Color.LightGray,
                                                                    text = chapter.name
                                                                )
                                                            }
                                                        }, onClick = {
                                                            viewmodel?.player?.jumpToChapter(chapter)
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
                                    viewmodel!!.p.ready = b
                                    viewmodel!!.p.sendPacket(JsonSender.sendReadiness(b, true))
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

                        /* Bottom-mid row (Slider + seek buttons + timestamps) */
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp)
                                .fillMaxWidth()
                        ) {
                            var slidervalue by remember { viewmodel!!.timeCurrent }
                            val slidermax by remember { viewmodel!!.timeFull }
                            val interactionSource = remember { MutableInteractionSource() }

                            Row(
                                modifier = Modifier.fillMaxWidth(0.75f), verticalAlignment = Bottom
                            ) {
                                Text(
                                    text = timeStamper(remember { viewmodel!!.timeCurrent }.longValue),
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
                                                seekBckwd()
                                            }

                                            FancyIcon2(
                                                icon = Icons.Filled.FastForward,
                                                size = ROOM_ICON_SIZE + 6,
                                                shadowColor = Color.Black
                                            ) {
                                                seekFrwrd()
                                            }
                                        }
                                        val customSkipToFront by PREF_INROOM_PLAYER_CUSTOM_SEEK_FRONT.settingBooleanState()

                                        if (customSkipToFront) {
                                            val customSkipAmount by PREF_INROOM_PLAYER_CUSTOM_SEEK_AMOUNT.settingIntState()
                                            val customSkipAmountString by derivedStateOf {
                                                timeStamper(
                                                    customSkipAmount
                                                )
                                            }
                                            TextButton(
                                                modifier = Modifier.gradientOverlay(),
                                                onClick = {
                                                    viewmodel?.player?.playerScopeIO?.launch {
                                                        val currentMs =
                                                            withContext(Dispatchers.Main) { viewmodel?.player!!.currentPositionMs() }
                                                        val newPos = (currentMs) + (customSkipAmount * 1000L)

                                                        sendSeek(newPos)
                                                        viewmodel?.player?.seekTo(newPos)

                                                        if (isSoloMode) {
                                                            viewmodel?.seeks?.add(
                                                                Pair(
                                                                    (currentMs), newPos * 1000
                                                                )
                                                            )
                                                        }

                                                        dispatchOSD(
                                                            lyricist.strings.roomCustomSkipButton(
                                                                customSkipAmountString
                                                            )
                                                        )
                                                    }
                                                },
                                            ) {
                                                Icon(imageVector = Icons.Filled.AvTimer, "")
                                                Text(
                                                    modifier = Modifier.padding(start = 4.dp),
                                                    text = lyricist.strings.roomCustomSkipButton(
                                                        customSkipAmountString
                                                    ),
                                                    fontSize = 12.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                val timeFullR = remember { viewmodel!!.timeFull }
                                Text(
                                    text = if (timeFullR.longValue >= Long.MAX_VALUE / 1000L) "???" else timeStamper(
                                        timeFullR.longValue
                                    ),
                                    modifier = Modifier.alpha(0.85f).gradientOverlay(),
                                )

                            }
                            Slider(
                                value = slidervalue.toFloat(),
                                valueRange = (0f..(slidermax.toFloat())),
                                onValueChange = { f ->
                                    viewmodel?.player?.seekTo(f.toLong() * 1000L)
                                    if (isSoloMode) {
                                        viewmodel?.player?.let {
                                            composeScope.launch(Dispatchers.Main) {
                                                viewmodel?.seeks?.add(
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
                                    sendSeek(slidervalue * 1000L)
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
                                track = {
                                    SliderDefaults.Track(
                                        sliderState = it,
                                        modifier = Modifier.scale(scaleX = 1F, scaleY = 0.85F),
                                        thumbTrackGapSize = 0.dp,
                                        drawStopIndicator = null,
                                        drawTick = { _, _ -> })
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
                            AddVideoButton(modifier = Modifier, onClick = {
                                addmediacardvisible = !addmediacardvisible
                                controlcardvisible = false
                            })


                            DropdownMenu(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(
                                    0.5f
                                ),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
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
                                            text = lyricist.strings.roomAddmediaOffline
                                        )
                                    }
                                }, onClick = {
                                    addmediacardvisible = false
                                    showVideoPicker = true
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
                                            text = lyricist.strings.roomAddmediaOnline
                                        )
                                    }
                                }, onClick = {
                                    addmediacardvisible = false
                                    addurlpopupstate.value = true
                                })
                            }
                        }
                    }

                    /** PLAY BUTTON */
                    val playing = remember { viewmodel!!.isNowPlaying }
                    if (hasVideo.value) {
                        FancyIcon2(
                            icon = when (playing.value) {
                                true -> Icons.Filled.Pause
                                false -> Icons.Filled.PlayArrow
                            },
                            size = (ROOM_ICON_SIZE * 2.25).roundToInt(),
                            shadowColor = Color.Black,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            composeScope.launch(Dispatchers.Main) {
                                if (viewmodel?.player?.isPlaying() == true) {
                                    pausePlayback()
                                } else {
                                    playPlayback()
                                }
                            }
                        }
                    }
                }
            }
        }

        /** Fading Message overlay */
        if (!isSoloMode) {
            fadingMessageLayout(
                hudVisibility = hudVisibility, pipModeObserver = pipModeObserver
            )
        }


        /** Popups */
        AddUrlPopup(visibilityState = addurlpopupstate)
        SeekToPositionPopup(visibilityState = seektopopupstate)
        if (!isSoloMode) ChatHistoryPopup(visibilityState = chathistorypopupstate)
    }
}

// Custom composable with gradient text, outline, and icon
@Composable
fun GradientTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onSend: () -> Unit,
    msgCanSend: Boolean = false,
    singleLine: Boolean = false,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    colors: List<Color> = Paletting.SP_GRADIENT
) {
    val gradientBrush = Brush.linearGradient(colors = colors)

    OutlinedTextField(
        modifier = modifier.alpha(0.75f).fillMaxWidth(),
        singleLine = singleLine,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.first(),
            unfocusedBorderColor = colors[1],
            cursorColor = colors.last()
        ),
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                maxLines = 1,
                style = TextStyle(brush = gradientBrush)
            )
        },
        trailingIcon = {
            if (msgCanSend) {
                IconButton(onClick = onSend) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "",
                        modifier = Modifier.graphicsLayer(alpha = 0.99f).drawWithCache {
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = gradientBrush, blendMode = BlendMode.SrcAtop
                                    )
                                }
                            })
                }
            }
        },
        value = value,
        onValueChange = onValueChange,
        visualTransformation = VisualTransformation { text ->
            val annotatedString = buildAnnotatedString {
                text.text.forEachIndexed { index, char ->
                    if (!char.isEmoji()) {
                        withStyle(style = SpanStyle(brush = gradientBrush)) {
                            append(char)
                        }
                    } else {
                        append(char)
                    }
                }
            }
            TransformedText(annotatedString, OffsetMapping.Identity)
        })
}

fun Char.isEmoji(): Boolean {
    val codePoint = this.code
    return when {
        // Basic emoji ranges
        codePoint in 0x2600..0x27BF -> true // Various symbols
        codePoint in 0x1F600..0x1F64F -> true // Emoticons
        codePoint in 0x1F300..0x1F5FF -> true // Misc symbols and pictographs
        codePoint in 0x1F680..0x1F6FF -> true // Transport and map
        codePoint in 0x1F700..0x1F77F -> true // Alchemical symbols
        codePoint in 0x1F780..0x1F7FF -> true // Geometric shapes
        codePoint in 0x1F800..0x1F8FF -> true // Supplemental arrows
        codePoint in 0x1F900..0x1F9FF -> true // Supplemental symbols and pictographs
        codePoint in 0x1FA00..0x1FA6F -> true // Chess symbols
        codePoint in 0x1FA70..0x1FAFF -> true // Symbols and pictographs extended-A
        this.isHighSurrogate() || this.isLowSurrogate() -> true // Surrogate pairs
        else -> false
    }
}