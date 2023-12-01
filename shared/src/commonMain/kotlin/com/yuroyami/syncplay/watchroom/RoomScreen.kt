package com.yuroyami.syncplay.watchroom

import androidx.compose.animation.expandIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.ripple.rememberRipple
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.yuroyami.syncplay.compose.ComposeUtils
import com.yuroyami.syncplay.compose.ComposeUtils.FancyIcon2
import com.yuroyami.syncplay.compose.ComposeUtils.FlexibleFancyAnnotatedText
import com.yuroyami.syncplay.compose.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.compose.cards.CardRoomPrefs.InRoomSettingsCard
import com.yuroyami.syncplay.compose.cards.CardUserInfo.UserInfoCard
import com.yuroyami.syncplay.compose.fontDirective
import com.yuroyami.syncplay.compose.fontInter
import com.yuroyami.syncplay.compose.popups.PopupChatHistory.ChatHistoryPopup
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_INROOM_PREFERENCES
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_MISC_PREFS
import com.yuroyami.syncplay.datastore.DataStoreKeys.MISC_NIGHTMODE
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_BG_OPACITY
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_BOX_ACTION
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_FONTSIZE
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_MAXCOUNT
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_INROOM_MSG_OUTLINE
import com.yuroyami.syncplay.datastore.booleanFlow
import com.yuroyami.syncplay.datastore.ds
import com.yuroyami.syncplay.datastore.intFlow
import com.yuroyami.syncplay.datastore.obtainBoolean
import com.yuroyami.syncplay.datastore.writeBoolean
import com.yuroyami.syncplay.player.PlayerUtils.pausePlayback
import com.yuroyami.syncplay.player.PlayerUtils.playPlayback
import com.yuroyami.syncplay.player.PlayerUtils.seekBckwd
import com.yuroyami.syncplay.player.PlayerUtils.seekFrwrd
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.ui.AppTheme
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.ui.Paletting.ROOM_ICON_SIZE
import com.yuroyami.syncplay.utils.CommonUtils
import com.yuroyami.syncplay.utils.RoomUtils.sendMessage
import com.yuroyami.syncplay.utils.RoomUtils.sendSeek
import com.yuroyami.syncplay.utils.getScreenSizeInfo
import com.yuroyami.syncplay.utils.timeStamper
import com.yuroyami.syncplay.watchroom.RoomComposables.AddVideoButton
import com.yuroyami.syncplay.watchroom.RoomComposables.ComposedMessagePalette
import com.yuroyami.syncplay.watchroom.RoomComposables.FreeAnimatedVisibility
import com.yuroyami.syncplay.watchroom.RoomComposables.PingRadar
import com.yuroyami.syncplay.watchroom.RoomComposables.RoomArtwork
import com.yuroyami.syncplay.watchroom.RoomComposables.RoomTab
import com.yuroyami.syncplay.watchroom.RoomComposables.fadingMessageLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/** TODO: Ship these to a platform-agnostic viewmodel */
val hasVideoG = mutableStateOf(false)
val hudVisibilityState = mutableStateOf(true)
val pipMode = mutableStateOf(false)

var setReadyDirectly = false
val seeks = mutableListOf<Pair<Long, Long>>()

var startupSlide = false

/* Related to playback status */
val isNowPlaying = mutableStateOf(false)
val timeFull = mutableLongStateOf(0L)
val timeCurrent = mutableLongStateOf(0L)

interface PickerCallback {
    fun goPickVideo()
    fun goPickFolder()
}

var pickFuture: CompletableDeferred<String>? = null
var pickerCallback: PickerCallback? = null
var pickerScope = CoroutineScope(Dispatchers.IO)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoomUI() {
    val nightMode = DATASTORE_MISC_PREFS.ds().booleanFlow(MISC_NIGHTMODE, true).collectAsState(initial = true)

    val directive = fontDirective()
    val inter = fontInter()

    val composeScope = rememberCoroutineScope { Dispatchers.IO }

    LaunchedEffect(null) {
        /** Starting ping update */
        if (CommonUtils.pingUpdateJob == null && !isSoloMode) {
            CommonUtils.pingUpdateJob = composeScope.launch(Dispatchers.IO) {
                CommonUtils.beginPingUpdate()
            }
        }
    }

    AppTheme(nightMode.value) {
        val focusManager = LocalFocusManager.current
        val dimensions = getScreenSizeInfo()

        val hasVideo = remember { hasVideoG }
        val hudVisibility = remember { hudVisibilityState }
        val pipModeObserver by remember { pipMode }
        val locked = remember { mutableStateOf(false) }

        val addurlpopupstate = remember { mutableStateOf(false) }
        val chathistorypopupstate = remember { mutableStateOf(false) }
        val seektopopupstate = remember { mutableStateOf(false) }

        val msgPalette = ComposedMessagePalette()

        val msgBoxOpacity = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_MSG_BG_OPACITY, 0).collectAsState(initial = 0)
        val msgOutline = DATASTORE_INROOM_PREFERENCES.ds().booleanFlow(PREF_INROOM_MSG_OUTLINE, true).collectAsState(initial = true)
        val msgFontSize = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_MSG_FONTSIZE, 9).collectAsState(initial = 9)
        val msgMaxCount by DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_MSG_MAXCOUNT, 10).collectAsState(initial = 0)
        val keyboardOkFunction by DATASTORE_INROOM_PREFERENCES.ds().booleanFlow(PREF_INROOM_MSG_BOX_ACTION, true).collectAsState(initial = true)

        /** Room artwork underlay (when no video is loaded) */
        if (!hasVideo.value) {
            RoomArtwork(pipModeObserver)
        }

        /** video surface */
        player?.VideoPlayer(Modifier.fillMaxSize().alpha(if (hasVideo.value) 1f else 0f))

        /** Lock layout, This is what appears when the user locks the screen */
        val unlockButtonVisibility = remember { mutableStateOf(false) }

        if (locked.value) {
            /** The touch interceptor to switch unlock button visibility */
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null, onClick = {
                            unlockButtonVisibility.value = !unlockButtonVisibility.value
                        }
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 15.dp, end = 44.dp),
                horizontalAlignment = Alignment.End
            ) {
                /** Unlock Card */
                if (unlockButtonVisibility.value && !pipModeObserver) {
                    Card(
                        modifier = Modifier.width(48.dp).alpha(0.5f).aspectRatio(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(color = Paletting.SP_ORANGE)
                            ) {
                                locked.value = false
                            },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.NoEncryption,
                                contentDescription = "",
                                modifier = Modifier.size(32.dp).align(Alignment.Center).gradientOverlay()
                            )
                        }
                    }
                }
            }
        } else {
            /** ACTUAL ROOM HUD AND LAYOUT */

            var msg by remember { mutableStateOf("") }
            var msgCanSend by remember { mutableStateOf(false) }
            val msgs = if (!isSoloMode) remember { p.session.messageSequence } else remember { mutableStateListOf() }
            var ready by remember { mutableStateOf(setReadyDirectly) }
            var controlcardvisible by remember { mutableStateOf(false) }
            var addmediacardvisible by remember { mutableStateOf(false) }

            var gestures by remember { mutableStateOf(false) }
            val userinfoVisibility = remember { mutableStateOf(false) }
            val sharedplaylistVisibility = remember { mutableStateOf(false) }
            val inroomprefsVisibility = remember { mutableStateOf(false) }

            if (!startupSlide) {
                LaunchedEffect(null) {
                    composeScope.launch {
                        delay(600)
                        userinfoVisibility.value = true
                        startupSlide = true
                    }
                }
            }

            /* Gestures Interceptor */
            GestureInterceptor(
                gestures = gestures,
                hasVideo = hasVideo.value,
                onSingleTap = {
                    hudVisibility.value = !hudVisibility.value
                    if (!hudVisibility.value) {
                        /* Hide any popups */
                        controlcardvisible = false
                        addmediacardvisible = false
                    }
                }
            )

            /* HUD below: We resort to using a combination of Boxes, Rows, and Columns. */
            if (hudVisibility.value) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {

                    /* Top row (Message input box + Messages) */
                    Column(
                        modifier = Modifier.fillMaxWidth(0.32f).align(Alignment.TopStart),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            /** Message input box */
                            if (!isSoloMode && !pipModeObserver) {
                                val onSend = fun() {
                                    val msgToSend = msg.run {
                                        var t = replace("\\", "")
                                        if (t.length > 150) t = t.substring(0, 149)
                                        t
                                    }
                                    if (msgToSend.isNotBlank()) {
                                        sendMessage(msgToSend)
                                    }
                                    msg = ""
                                    msgCanSend = false

                                    focusManager.clearFocus()
                                }

                                OutlinedTextField(
                                    modifier = Modifier.alpha(0.75f).gradientOverlay().fillMaxWidth(),
                                    singleLine = true,
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (keyboardOkFunction) {
                                            onSend()
                                        }
                                    }),
                                    label = { Text(text = "Type your message...", fontSize = 12.sp) },
                                    trailingIcon = {
                                        if (msgCanSend) {
                                            IconButton(onClick = onSend) {
                                                Icon(imageVector = Icons.Filled.Send, "")
                                            }
                                        }
                                    },
                                    value = msg,
                                    onValueChange = { s ->
                                        msg = s
                                        msgCanSend = s.isNotBlank()
                                    }
                                )
                            }
                        }

                        /* Messages */
                        if (!isSoloMode) {
                            Card(
                                modifier = Modifier.clickable(false){}.focusable(false),
                                shape = RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasVideo.value || MaterialTheme.colorScheme.primary != Paletting.OLD_SP_YELLOW)
                                        Color(50, 50, 50, msgBoxOpacity.value) else Color.Transparent
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            ) {

                                LazyColumn(
                                    contentPadding = PaddingValues(8.dp),
                                    userScrollEnabled = false,
                                    modifier = Modifier.fillMaxWidth().clickable(false){}.focusable(false)
                                ) {
                                    //TODO: Show errors in red
                                    val lastMessages = msgs.toList().takeLast(msgMaxCount)
                                    items(lastMessages) {
                                        LaunchedEffect(null) {
                                            it.seen = true /* Once seen, don't use it in fading message */
                                        }

                                        val text = it.factorize(msgPalette)

                                        FlexibleFancyAnnotatedText(
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable(enabled = false) {}.focusable(false),
                                            text = text,
                                            size = if (pipModeObserver) 6f else (msgFontSize.value.toFloat()),
                                            font = inter,
                                            lineHeight = (msgFontSize.value + 4).sp,
                                            overflow = TextOverflow.Ellipsis,
                                            shadowSize = 1.5f,
                                            shadowColors = if (msgOutline.value) listOf(Color.Black) else listOf()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    /* Top-Center info */
                    /* Overall info (PING + ROOMNAME + OSD Messages) */
                    if (!isSoloMode && !pipModeObserver) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.wrapContentWidth().align(Alignment.TopCenter)
                        ) {
                            if (!isSoloMode) {
                                Row {
                                    val pingo by remember { p.ping }
                                    Text(
                                        text = if (pingo == null) {
                                            "Disconnected"
                                        } else {
                                            "Connected - ${pingo!!.toInt()}ms"
                                        },
                                        color = Paletting.OLD_SP_PINK
                                    )
                                    Spacer(Modifier.width(4.dp))

                                    PingRadar(pingo)
                                }
                                Text(text = "Room: ${p.session.currentRoom}", fontSize = 11.sp, color = Paletting.OLD_SP_PINK)
                            }
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            /* The tabs in the top-right corner */
                            /* In-room settings */
                            RoomTab(icon = Icons.Filled.AutoFixHigh, visibilityState = inroomprefsVisibility.value) {
                                sharedplaylistVisibility.value = false
                                userinfoVisibility.value = false
                                inroomprefsVisibility.value = !inroomprefsVisibility.value
                            }

                            Spacer(Modifier.width(12.dp))

                            /* Shared Playlist */
                            if (!isSoloMode) {
                                RoomTab(icon = Icons.Filled.PlaylistPlay, visibilityState = sharedplaylistVisibility.value) {
                                    sharedplaylistVisibility.value = !sharedplaylistVisibility.value
                                    userinfoVisibility.value = false
                                    inroomprefsVisibility.value = false
                                }

                                Spacer(Modifier.width(12.dp))
                            }

                            /* User Info card tab */
                            if (!isSoloMode) {
                                RoomTab(icon = Icons.Filled.Groups, visibilityState = userinfoVisibility.value) {
                                    userinfoVisibility.value = !userinfoVisibility.value
                                    sharedplaylistVisibility.value = false
                                    inroomprefsVisibility.value = false
                                }

                                Spacer(Modifier.width(12.dp))
                            }


                            /** Lock card */
                            RoomTab(icon = Icons.Filled.Lock, visibilityState = false) {
                                locked.value = true
                                hudVisibility.value = false
                            }

                            Spacer(Modifier.width(6.dp))

                            Box {
                                val overflowmenustate = remember { mutableStateOf(false) }
                                FancyIcon2(icon = Icons.Filled.MoreVert, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                    overflowmenustate.value = !overflowmenustate.value
                                }

                                DropdownMenu(
                                    modifier = Modifier.background(color = MaterialTheme.colorScheme.tertiaryContainer),
                                    expanded = overflowmenustate.value,
                                    properties = PopupProperties(
                                        dismissOnBackPress = true,
                                        focusable = true,
                                        dismissOnClickOutside = true
                                    ),
                                    onDismissRequest = { overflowmenustate.value = false }) {

                                    ComposeUtils.FancyText2(
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .padding(horizontal = 2.dp),
                                        string = "More Options...",
                                        solid = Color.Black,
                                        size = 14f,
                                        font = directive
                                    )

                                    /* Chat history item */
                                    if (!isSoloMode) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        modifier = Modifier.padding(2.dp),
                                                        imageVector = Icons.Filled.Forum, contentDescription = "",
                                                        tint = Color.LightGray
                                                    )

                                                    Spacer(Modifier.width(8.dp))

                                                    Text(
                                                        color = Color.LightGray,
                                                        text = "Chat history"
                                                    )
                                                }
                                            },
                                            onClick = {
                                                overflowmenustate.value = false
                                                chathistorypopupstate.value = true
                                            }
                                        )
                                    }

                                    /* Toggle Dark mode */
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    modifier = Modifier.padding(2.dp),
                                                    imageVector = Icons.Filled.DarkMode, contentDescription = "",
                                                    tint = Color.LightGray
                                                )

                                                Spacer(Modifier.width(8.dp))

                                                Text(
                                                    color = Color.LightGray,
                                                    text = "Toggle night-mode"
                                                )
                                            }
                                        },
                                        onClick = {
                                            overflowmenustate.value = false

                                            val newMode = runBlocking {
                                                !DATASTORE_MISC_PREFS.obtainBoolean(MISC_NIGHTMODE, true)
                                            }

                                            composeScope.launch {
                                                DATASTORE_MISC_PREFS.writeBoolean(MISC_NIGHTMODE, newMode)
                                            }
                                        }
                                    )

                                    /* Leave room item */
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    modifier = Modifier.padding(2.dp),
                                                    imageVector = Icons.Filled.Logout, contentDescription = "",
                                                    tint = Color.LightGray
                                                )

                                                Spacer(Modifier.width(8.dp))

                                                Text(
                                                    color = Color.LightGray,
                                                    text = "Leave room"
                                                )
                                            }
                                        },
                                        onClick = {
                                            //TODO: terminate()
                                        }
                                    )
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
                                    modifier = Modifier
                                        .fillMaxWidth(cardWidth)
                                        .fillMaxHeight(cardHeight),
                                    enter = slideInHorizontally(initialOffsetX = { (dimensions.wPX * 1.3).toInt() }),
                                    exit = slideOutHorizontally(targetOffsetX = { (dimensions.wPX * 1.3).toInt() }),
                                    visible = !inroomprefsVisibility.value && userinfoVisibility.value && !sharedplaylistVisibility.value
                                ) {
                                    UserInfoCard()
                                }
                            }

                            /** Shared Playlist card (toggled on and off) */
                            if (!isSoloMode) {
                                FreeAnimatedVisibility(
                                    modifier = Modifier
                                        .fillMaxWidth(cardWidth)
                                        .fillMaxHeight(cardHeight),
                                    enter = slideInHorizontally(initialOffsetX = { (dimensions.wPX * 1.3).toInt() }),
                                    exit = slideOutHorizontally(targetOffsetX = { (dimensions.wPX * 1.3).toInt() }),
                                    visible = !inroomprefsVisibility.value && !userinfoVisibility.value && sharedplaylistVisibility.value
                                ) {
                                    //TODO: SharedPlaylistCard()
                                }
                            }

                            /** In-room card (toggled on and off) */
                            FreeAnimatedVisibility(
                                modifier = Modifier
                                    .fillMaxWidth(cardWidth)
                                    .fillMaxHeight(cardHeight),
                                enter = slideInHorizontally(initialOffsetX = { (dimensions.wPX * 1.3).toInt() }),
                                exit = slideOutHorizontally(targetOffsetX = { (dimensions.wPX * 1.3).toInt() }),
                                visible = inroomprefsVisibility.value && !userinfoVisibility.value && !sharedplaylistVisibility.value
                            ) {
                                InRoomSettingsCard()
                            }

                            /** Control card (to control the player) */
                            FreeAnimatedVisibility(
                                modifier = Modifier.zIndex(10f)
                                    .wrapContentWidth()
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(cardHeight + 0.1f),
                                enter = expandIn(),
                                visible = controlcardvisible
                            ) {
                                /** CONTROL CARD ------ PLAYER CONTROL CARD ----- PLAYER CONTROL CARD */
                                Card(
                                    modifier = Modifier.zIndex(10f),
                                    shape = CardDefaults.outlinedShape,
                                    border = BorderStroke(2.dp, Color.Gray),
                                    elevation = CardDefaults.outlinedCardElevation(defaultElevation = 8.dp),
                                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                                ) {
                                    FlowColumn(
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .fillMaxHeight(),
                                        verticalArrangement = Arrangement.SpaceEvenly,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        /* Aspect Ratio */
                                        FancyIcon2(icon = Icons.Filled.AspectRatio, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                            val newAspectRatio = player?.switchAspectRatio()
                                            //TODO: Inform user about his new aspect ratio
                                        }

                                        /* Seek Gesture (DoNotTouch for disabling it) */
                                        FancyIcon2(
                                            icon = when (gestures) {
                                                true -> Icons.Filled.TouchApp
                                                false -> Icons.Filled.DoNotTouch
                                            }, size = ROOM_ICON_SIZE, shadowColor = Color.Black
                                        ) {
                                            gestures = !gestures
                                        }

                                        /* Seek To */
                                        FancyIcon2(icon = Icons.Filled.BrowseGallery, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                            controlcardvisible = false
                                            seektopopupstate.value = true
                                        }

                                        /* Undo Last Seek */
                                        FancyIcon2(icon = Icons.Filled.History, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                            if (seeks.isEmpty()) {
                                                //TODO toasty("There is no recent seek in the room.")
                                                return@FancyIcon2
                                            }

                                            controlcardvisible = false

                                            val lastSeek = seeks.last()
                                            player?.seekTo(lastSeek.first)
                                            sendSeek(lastSeek.first)
                                            seeks.remove(lastSeek)
                                            //TODO toasty("Seek undone.")
                                        }

                                        /* Subtitle Tracks */
                                        Box {
                                            val tracksPopup = remember { mutableStateOf(false) }


                                            FancyIcon2(icon = Icons.Filled.Subtitles, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                                player?.analyzeTracks(media ?: return@FancyIcon2)
                                                tracksPopup.value = true
                                            }

                                            DropdownMenu(
                                                modifier = Modifier.background(color = MaterialTheme.colorScheme.tertiaryContainer),
                                                expanded = tracksPopup.value,
                                                properties = PopupProperties(
                                                    dismissOnBackPress = true,
                                                    focusable = true,
                                                    dismissOnClickOutside = true
                                                ),
                                                onDismissRequest = { tracksPopup.value = !tracksPopup.value }) {

                                                ComposeUtils.FancyText2(
                                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                                    string = "Subtitle Track",
                                                    solid = Color.Black,
                                                    size = 14f,
                                                    font = directive
                                                )

                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(imageVector = Icons.Filled.NoteAdd, "", tint = Color.LightGray)

                                                            Spacer(Modifier.width(2.dp))

                                                            Text("Import from file", color = Color.LightGray)
                                                        }
                                                    },
                                                    onClick = {
                                                        tracksPopup.value = false
                                                        controlcardvisible = false

                                                        //TODO: Load external sub
                                                    }
                                                )


                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(imageVector = Icons.Filled.SubtitlesOff, "", tint = Color.LightGray)

                                                            Spacer(Modifier.width(2.dp))

                                                            Text("Disable subtitles.", color = Color.LightGray)

                                                        }
                                                    },
                                                    onClick = {
                                                        //player?.selectTrack(C.TRACK_TYPE_TEXT, -1)
                                                        tracksPopup.value = false
                                                        controlcardvisible = false

                                                    }
                                                )

                                                for (track in (media?.subtitleTracks) ?: listOf()) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Checkbox(checked = track.selected.value, onCheckedChange = {
                                                                    //FIXME: player?.selectTrack(C.TRACK_TYPE_TEXT, track.index)
                                                                    tracksPopup.value = false
                                                                })

                                                                Text(
                                                                    color = Color.LightGray,
                                                                    text = track.name
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            //FIXME player?.selectTrack(C.TRACK_TYPE_TEXT, track.index)
                                                            tracksPopup.value = false
                                                            controlcardvisible = false

                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        /* Audio Tracks */
                                        Box {
                                            val tracksPopup = remember { mutableStateOf(false) }

                                            FancyIcon2(icon = Icons.Filled.SpeakerGroup, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                                player?.analyzeTracks(media ?: return@FancyIcon2)
                                                tracksPopup.value = true
                                            }

                                            DropdownMenu(
                                                modifier = Modifier.background(color = MaterialTheme.colorScheme.tertiaryContainer),
                                                expanded = tracksPopup.value,
                                                properties = PopupProperties(
                                                    dismissOnBackPress = true,
                                                    focusable = true,
                                                    dismissOnClickOutside = true
                                                ),
                                                onDismissRequest = { tracksPopup.value = !tracksPopup.value }) {

                                                ComposeUtils.FancyText2(
                                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                                    string = "Audio Track",
                                                    solid = Color.Black,
                                                    size = 14f,
                                                    font = directive
                                                )

                                                for (track in (media?.audioTracks ?: listOf())) {
                                                    DropdownMenuItem(text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Checkbox(checked = track.selected.value, onCheckedChange = {
                                                                with(player ?: return@Checkbox) {
                                                                    //FIXME: selectTrack(C.TRACK_TYPE_AUDIO, track.index)
                                                                }
                                                                tracksPopup.value = false
                                                            })

                                                            Text(
                                                                color = Color.LightGray,
                                                                text = track.name
                                                            )
                                                        }
                                                    },
                                                        onClick = {
                                                            //FIXME: player?.selectTrack(C.TRACK_TYPE_AUDIO, track.index)
                                                            tracksPopup.value = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (hasVideo.value) {
                        /* Bottom-left row (Ready button) */
                        if (!isSoloMode) {
                            IconToggleButton(modifier = Modifier
                                .width(112.dp)
                                .padding(8.dp)
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
                                    p.ready = b
                                    p.sendPacket(JsonSender.sendReadiness(b, true))
                                }) {
                                when (ready) {
                                    true -> Text("Ready", fontSize = 14.sp)
                                    false -> Text("Not Ready", fontSize = 13.sp)
                                }
                            }
                        }

                        /* Bottom-mid row (Slider + timestamps) */
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp).fillMaxWidth()
                        ) {
                            var slidervalue by remember { timeCurrent }
                            val slidermax by remember { timeFull }
                            val interactionSource = remember { MutableInteractionSource() }

                            Row(modifier = Modifier.fillMaxWidth(0.75f)) {

                                Column(
                                    modifier = Modifier.fillMaxWidth(0.33f)
                                        .offset(x = 25.dp, y = 10.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.Bottom,
                                ) {
                                    Text(
                                        text = timeStamper(remember { timeCurrent }.longValue),
                                        modifier = Modifier
                                            .alpha(0.85f)
                                            .gradientOverlay(),
                                    )
                                }

                                Column(
                                    Modifier.fillMaxWidth(0.5f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (gestures) {
                                        Row(horizontalArrangement = Arrangement.Center) {
                                            FancyIcon2(icon = Icons.Filled.FastRewind, size = ROOM_ICON_SIZE + 6, shadowColor = Color.Black) {
                                                seekBckwd()
                                            }
                                            Spacer(Modifier.width(24.dp))
                                            FancyIcon2(icon = Icons.Filled.FastForward, size = ROOM_ICON_SIZE + 6, shadowColor = Color.Black) {
                                                seekFrwrd()
                                            }
                                        }
                                    }
                                }

                                Column(
                                    Modifier.fillMaxWidth()
                                        .offset(x = (-25).dp, y = 10.dp),
                                    verticalArrangement = Arrangement.Bottom,
                                    horizontalAlignment = Alignment.End
                                ) {
                                    val timeFullR = remember { timeFull }
                                    Text(
                                        text = if (timeFullR.longValue >= Long.MAX_VALUE / 1000L) "???" else timeStamper(timeFullR.longValue),
                                        modifier = Modifier
                                            .alpha(0.85f)
                                            .gradientOverlay(),
                                    )
                                }
                            }
                            Slider(
                                value = slidervalue.toFloat(),
                                valueRange = (0f..(slidermax.toFloat())),
                                onValueChange = { f ->
                                    player?.seekTo(f.toLong() * 1000L)
                                     if (isSoloMode) {
                                         player?.let {
                                             seeks.add(Pair(it.currentPositionMs(), f.toLong() * 1000))
                                         }
                                    }

                                    slidervalue = f.toLong()
                                },
                                onValueChangeFinished = {
                                    sendSeek(slidervalue * 1000L)
                                },
                                modifier = Modifier
                                    .alpha(0.82f)
                                    .fillMaxWidth(0.75f)
                                    .padding(horizontal = 12.dp),
                                interactionSource = interactionSource,
                                steps = 500,
                                thumb = remember(SliderDefaults.colors(), true) {
                                    {
                                        SliderDefaults.Thumb(
                                            interactionSource = interactionSource,
                                            colors = SliderDefaults.colors(),
                                            enabled = true,
                                            modifier = Modifier.alpha(0.8f)
                                        )
                                    }
                                }
                            )
                        }
                    }

                    /* Bottom-right row (Controls) */
                    Row(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                        if (hasVideo.value) {
                            FancyIcon2(
                                modifier = Modifier,
                                icon = Icons.Filled.VideoSettings, size = ROOM_ICON_SIZE + 6, shadowColor = Color.Black,
                                onClick = {
                                    controlcardvisible = !controlcardvisible
                                    addmediacardvisible = false
                                }
                            )
                        }

                        /* The button of adding media */
                        Box {
                            AddVideoButton(
                                modifier = Modifier,
                                onClick = {
                                    addmediacardvisible = !addmediacardvisible
                                    controlcardvisible = false
                                }
                            )


                            DropdownMenu(
                                modifier = Modifier.background(color = MaterialTheme.colorScheme.tertiaryContainer),
                                expanded = addmediacardvisible,
                                properties = PopupProperties(
                                    dismissOnBackPress = true,
                                    focusable = true,
                                    dismissOnClickOutside = true
                                ),
                                onDismissRequest = { addmediacardvisible = false }) {

                                ComposeUtils.FancyText2(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(horizontal = 2.dp),
                                    string = "Add media",
                                    solid = Color.Black,
                                    size = 14f,
                                    font = directive
                                )

                                //From storage
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FancyIcon2(icon = Icons.Filled.CreateNewFolder, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {}
                                            Spacer(Modifier.width(8.dp))
                                            Text(color = Color.LightGray, text = "From storage")
                                        }
                                    },
                                    onClick = {
                                        addmediacardvisible = false

                                        pickerScope.launch {
                                            try {
                                                pickFuture = CompletableDeferred() //We create a future that will be fulfilled
                                                pickerCallback?.goPickVideo() //Ask the platform to pick video
                                                addmediacardvisible = false
                                                val result = pickFuture?.await() ?: return@launch
                                                player?.injectVideo(result, false)
                                            } catch (_: CancellationException) {}
                                        }
                                    }
                                )

                                //From network URL
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FancyIcon2(icon = Icons.Filled.AddLink, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {}
                                            Spacer(Modifier.width(8.dp))
                                            Text(color = Color.LightGray, text = "From network (URL)")
                                        }
                                    },
                                    onClick = {
                                        addmediacardvisible = false
                                        addurlpopupstate.value = true
                                    }
                                )
                            }
                        }
                    }

                    /** PLAY BUTTON */
                    val playing = remember { isNowPlaying }
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
                            if (player?.isPlaying() == true) {
                                pausePlayback()
                            } else {
                                playPlayback()
                            }
                        }
                    }
                }
            }
        }

        /** Fading Message overlay */
        if (!isSoloMode) {
            fadingMessageLayout(
                hudVisibility = hudVisibility.value,
                pipModeObserver = pipModeObserver,
                msgPalette
            )
        }


        /** Popups */
        //AddUrlPopup(visibilityState = addurlpopupstate)
        //SeekToPositionPopup(visibilityState = seektopopupstate)
        if (!isSoloMode) ChatHistoryPopup(visibilityState = chathistorypopupstate, msgPalette)

    }
}