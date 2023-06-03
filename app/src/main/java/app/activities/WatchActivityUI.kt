package app.activities

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.AddToQueue
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import app.R
import app.compose.CardRoomPrefs.InRoomSettingsCard
import app.compose.CardSharedPlaylist.SharedPlaylistCard
import app.compose.CardUserInfo.UserInfoCard
import app.compose.MyComposables.AddVideoButton
import app.compose.MyComposables.RoomTab
import app.compose.PopupAddUrl.AddUrlPopup
import app.compose.PopupChatHistory.ChatHistoryPopup
import app.compose.PopupSeekToPosition.SeekToPositionPopup
import app.datastore.DataStoreKeys
import app.datastore.DataStoreKeys.DATASTORE_INROOM_PREFERENCES
import app.datastore.DataStoreKeys.DATASTORE_MISC_PREFS
import app.datastore.DataStoreKeys.MISC_NIGHTMODE
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_SELFTAG
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP
import app.datastore.DataStoreKeys.PREF_INROOM_COLOR_USERMSG
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_ACTIVATE_STAMP
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_BG_OPACITY
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_BOX_ACTION
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_FADING_DURATION
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_FONTSIZE
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_MAXCOUNT
import app.datastore.DataStoreKeys.PREF_INROOM_MSG_OUTLINE
import app.datastore.DataStoreKeys.PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP
import app.datastore.DataStoreKeys.PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP
import app.datastore.DataStoreUtils.booleanFlow
import app.datastore.DataStoreUtils.ds
import app.datastore.DataStoreUtils.intFlow
import app.datastore.DataStoreUtils.obtainBoolean
import app.datastore.DataStoreUtils.writeBoolean
import app.protocol.JsonSender
import app.ui.Paletting
import app.ui.Paletting.ROOM_ICON_SIZE
import app.ui.Paletting.backgroundGradient
import app.utils.ComposeUtils
import app.utils.ComposeUtils.FancyIcon2
import app.utils.ComposeUtils.FlexibleFancyAnnotatedText
import app.utils.ComposeUtils.FlexibleFancyText
import app.utils.ComposeUtils.gradientOverlay
import app.utils.MiscUtils.getFileName
import app.utils.MiscUtils.string
import app.utils.MiscUtils.timeStamper
import app.utils.MiscUtils.toasty
import app.utils.PlayerUtils.pausePlayback
import app.utils.PlayerUtils.playPlayback
import app.utils.RoomUtils.sendMessage
import app.utils.RoomUtils.sendSeek
import com.google.accompanist.flowlayout.FlowColumn
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

object WatchActivityUI {

    @SuppressLint("Range")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun WatchActivity.RoomUI() {
        /** Remembering coroutine scope to launch functions later */
        val generalScope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }

        val hasVideo = remember { hasVideoG }
        val hudVisibility = remember { hudVisibilityState }
        var pipModeObserver by remember { pipMode }
        val locked = remember { mutableStateOf(false) }

        val addurlpopupstate = remember { mutableStateOf(false) }
        val chathistorypopupstate = remember { mutableStateOf(false) }
        val seektopopupstate = remember { mutableStateOf(false) }

        val colorTimestamp = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_COLOR_TIMESTAMP, Paletting.MSG_TIMESTAMP.toArgb())
            .collectAsState(initial = Paletting.MSG_TIMESTAMP.toArgb())
        val colorSelftag = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_COLOR_SELFTAG, Paletting.MSG_SELF_TAG.toArgb())
            .collectAsState(initial = Paletting.MSG_SELF_TAG.toArgb())
        val colorFriendtag = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_COLOR_FRIENDTAG, Paletting.MSG_FRIEND_TAG.toArgb())
            .collectAsState(initial = Paletting.MSG_FRIEND_TAG.toArgb())
        val colorSystem = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_COLOR_SYSTEMMSG, Paletting.MSG_SYSTEM.toArgb())
            .collectAsState(initial = Paletting.MSG_SYSTEM.toArgb())
        val colorUserchat = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_COLOR_USERMSG, Paletting.MSG_CHAT.toArgb())
            .collectAsState(initial = Paletting.MSG_CHAT.toArgb())
        val colorError = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_COLOR_ERRORMSG, Paletting.MSG_ERROR.toArgb())
            .collectAsState(initial = Paletting.MSG_ERROR.toArgb())
        val msgIncludeTimestamp = DATASTORE_INROOM_PREFERENCES.ds().booleanFlow(PREF_INROOM_MSG_ACTIVATE_STAMP, true).collectAsState(initial = true)
        val fadingTimeout = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_MSG_FADING_DURATION, 2).collectAsState(initial = 3)
        val msgBoxOpacity = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_MSG_BG_OPACITY, 0).collectAsState(initial = 0)
        val msgOutline = DATASTORE_INROOM_PREFERENCES.ds().booleanFlow(PREF_INROOM_MSG_OUTLINE, true).collectAsState(initial = true)
        val msgFontSize = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_MSG_FONTSIZE, 9).collectAsState(initial = 9)
        val msgMaxCount by DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_MSG_MAXCOUNT, 10).collectAsState(initial = 0)
        val keyboardOkFunction by DATASTORE_INROOM_PREFERENCES.ds().booleanFlow(PREF_INROOM_MSG_BOX_ACTION, true).collectAsState(initial = true)
        val seekIncrement = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_PLAYER_SEEK_FORWARD_JUMP, 10).collectAsState(initial = 10)
        val seekDecrement = DATASTORE_INROOM_PREFERENCES.ds().intFlow(PREF_INROOM_PLAYER_SEEK_BACKWARD_JUMP, 10).collectAsState(initial = 10)


        val seekBckwd = fun() {
            if (player == null) return

            var newPos = (player!!.getPositionMs()) - seekDecrement.value * 1000
            if (newPos < 0) {
                newPos = 0
            }
            if (isSoloMode()) {
                seeks.add(Pair(player!!.getPositionMs(), newPos * 1000))
            }

            player?.seekTo(newPos)

            sendSeek(newPos)
        }

        val seekFwd = fun() {
            if (player == null) return

            var newPos = (player!!.getPositionMs()) + seekIncrement.value * 1000
            if (media != null) {
                if (newPos > media?.fileDuration!!.toLong()) {
                    newPos = media?.fileDuration!!.toLong()
                }
            }
            if (isSoloMode()) {
                seeks.add(Pair((player!!.getPositionMs()), newPos * 1000))
            }

            player?.seekTo(newPos)

            sendSeek(newPos)
        }

        /** Adding artwork layout */
        if (!hasVideo.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = backgroundGradient()
                        )
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .wrapContentWidth()
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.syncplay_logo_gradient), contentDescription = "",
                        modifier = Modifier
                            .height(if (pipModeObserver) 40.dp else 84.dp)
                            .aspectRatio(1f)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Box(modifier = Modifier.padding(bottom = 6.dp)) {
                        Text(
                            modifier = Modifier.wrapContentWidth(),
                            text = "Syncplay",
                            style = TextStyle(
                                color = Paletting.SP_PALE,
                                drawStyle = Stroke(
                                    miter = 10f,
                                    width = 2f,
                                    join = StrokeJoin.Round
                                ),
                                shadow = Shadow(
                                    color = Paletting.SP_INTENSE_PINK,
                                    offset = Offset(0f, 10f),
                                    blurRadius = 5f
                                ),
                                fontFamily = FontFamily(Font(R.font.directive4bold)),
                                fontSize = if (pipModeObserver) 8.sp else 26.sp,
                            )
                        )
                        Text(
                            modifier = Modifier.wrapContentWidth(),
                            text = "Syncplay",
                            style = TextStyle(
                                brush = Brush.linearGradient(
                                    colors = Paletting.SP_GRADIENT
                                ),
                                fontFamily = FontFamily(Font(R.font.directive4bold)),
                                fontSize = if (pipModeObserver) 8.sp else 26.sp,
                            )
                        )
                    }
                }
            }
        }

        /** video surface */
        val seekLeftInteraction: MutableInteractionSource = remember { MutableInteractionSource() }
        val seekRightInteraction: MutableInteractionSource = remember { MutableInteractionSource() }
        LaunchedEffect(hasVideo.value) {
            if (hasVideo.value) {
                unalphizePlayer(engine.value)
            }
        }

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
                        })
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
                        modifier = Modifier
                            .width(48.dp)
                            .alpha(0.5f)
                            .aspectRatio(1f)
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
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.Center)
                                    .gradientOverlay()
                            )
                        }
                    }
                }
            }
        } else {
            /** ExoPlayer overlay layout for controls, messages, etc. */
            ConstraintLayout {

                /* Constraint layout references */
                val (
                    seek_detector_left, seek_detector_right,
                    play_button, ready_button,
                    vidcontrolbutton, vidcontrolcard, addmediabutton, addmediacard,
                    msg_input, msg_box, overall_info,
                    slider, media_time_current, media_time_full,
                    room_tabs,
                ) = createRefs()

                val (shadergradbottom, fast_seek_buttons) = createRefs()

                val (userinfocard, sharedplaylistcard, inroomsettingscard) = createRefs()

                /* Top-level variables which scaffold things together */

                val msg = remember { mutableStateOf("") }
                val msgCanSend = remember { mutableStateOf(false) }
                val msgs = if (!isSoloMode()) remember { p.session.messageSequence } else remember { mutableStateListOf() }
                val ready = remember { mutableStateOf(setReadyDirectly) }
                val controlcardvisible = remember { mutableStateOf(false) }
                val addmediacardvisible = remember { mutableStateOf(false) }

                val gestures = remember { mutableStateOf(false) }
                val userinfoVisibility = remember { mutableStateOf(false) }
                val sharedplaylistVisibility = remember { mutableStateOf(false) }
                val inroomprefsVisibility = remember { mutableStateOf(false) }

                if (!startupSlide) {
                    LaunchedEffect(null) {
                        generalScope.launch {
                            delay(600)
                            userinfoVisibility.value = true
                            startupSlide = true
                        }
                    }
                }

                /** Now for Activity intent contract variables */
                val mediaPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        wentForFilePick = false

                        if (uri == null) return@rememberLauncherForActivityResult
                        addmediacardvisible.value = false

                        player?.injectVideo(this@RoomUI, uri)
                    }
                )

                val subtitlePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        wentForFilePick = false

                        if (uri == null) return@rememberLauncherForActivityResult
                        if (media == null) return@rememberLauncherForActivityResult

                        with(player ?: return@rememberLauncherForActivityResult) {
                            loadExternalSub(uri)
                        }
                    }
                )


                /** Messages */
                if (hudVisibility.value && !isSoloMode()) {
                    Card(
                        modifier = Modifier
                            .constrainAs(msg_box) {
                                top.linkTo(msg_input.bottom)
                                absoluteLeft.linkTo(msg_input.absoluteLeft)
                                absoluteRight.linkTo(msg_input.absoluteRight)
                                width = Dimension.fillToConstraints
                            }
                            .focusable(false),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusable(false)
                        ) {
                            //TODO: Show errors in red
                            val lastMessages = msgs.toList().takeLast(msgMaxCount)
                            items(lastMessages) {
                                LaunchedEffect(null) {
                                    /* If this msg is composed then it's seen, don't use it in fading message */
                                    it.seen = true
                                }

                                Box {
                                    val text = it.factorize(
                                        timestampColor = Color(colorTimestamp.value),
                                        selftagColor = Color(colorSelftag.value),
                                        friendtagColor = Color(colorFriendtag.value),
                                        systemmsgColor = Color(colorSystem.value),
                                        usermsgColor = Color(colorUserchat.value),
                                        errormsgColor = Color(colorError.value),
                                        includeTimestamp = msgIncludeTimestamp.value
                                    )

                                    FlexibleFancyAnnotatedText(
                                        modifier =  Modifier.fillMaxWidth().clickable(enabled = false) {}.focusable(false),
                                        text = text,
                                        size = if (pipModeObserver) 6f else (msgFontSize.value.toFloat()),
                                        font = Font(R.font.inter),
                                        lineHeight = (msgFontSize.value + 4).sp,
                                        overflow = TextOverflow.Ellipsis,
                                        shadowSize = 1.5f,
                                        shadowColors = if (msgOutline.value) listOf(Color.Black) else listOf()
                                    )

                                    /*
                                        Text(
                                            text = text.toString(),
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = false) {}
                                                .focusable(false),
                                            lineHeight = (msgFontSize.value + 4).sp,
                                            fontSize = if (pipModeObserver) 6.sp else (msgFontSize.value).sp
                                        ) */
                                }

                            }
                        }
                    }
                }

                /** Seek animators, their only purpose is to animate a seek action */
                if (gestures.value) {
                    Box(modifier = Modifier
                        .clickable(
                            enabled = false,
                            interactionSource = seekLeftInteraction,
                            indication = rememberRipple(
                                bounded = false,
                                color = Color(100, 100, 100, 190)
                            )
                        ) {}
                        .constrainAs(seek_detector_left) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            absoluteLeft.linkTo(parent.absoluteLeft)
                            height = Dimension.fillToConstraints
                            width = Dimension.percent(0.1f)
                        })

                    Box(modifier = Modifier
                        .clickable(
                            interactionSource = seekRightInteraction,
                            indication = rememberRipple(
                                bounded = false,
                                color = Color(100, 100, 100, 190)
                            )
                        ) {}
                        .constrainAs(seek_detector_right) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            absoluteRight.linkTo(parent.absoluteRight)
                            height = Dimension.fillToConstraints
                            width = Dimension.percent(0.1f)
                        })

                }

                /** Now for the UI touch handler (Seek back + Seek forward + Hide/Show UI) */
                if (gestures.value && hasVideo.value) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    generalScope.launch {
                                        if (offset.x < screenWidthPx.times(0.25f)) {
                                            seekBckwd()

                                            val press = PressInteraction.Press(Offset.Zero)
                                            seekLeftInteraction.emit(press)
                                            delay(150)
                                            seekLeftInteraction.emit(PressInteraction.Release(press))
                                        }
                                        if (offset.x > screenWidthPx.times(0.85f)) {
                                            seekFwd()

                                            val press = PressInteraction.Press(Offset.Zero)
                                            seekRightInteraction.emit(press)
                                            delay(150)
                                            seekRightInteraction.emit(PressInteraction.Release(press))
                                        }
                                    }
                                },
                                onTap = {
                                    hudVisibility.value = !hudVisibility.value
                                    if (!hudVisibility.value) {
                                        /* Hide any popups */
                                        controlcardvisible.value = false
                                        addmediacardvisible.value = false
                                    }
                                })
                        })
                } else {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    hudVisibility.value = !hudVisibility.value
                                    if (!hudVisibility.value) {
                                        /* Hide any popups */
                                        controlcardvisible.value = false
                                        addmediacardvisible.value = false
                                    }
                                })
                        })
                }

                /** Message input box */
                if (hudVisibility.value && !isSoloMode() && !pipModeObserver) {
                    OutlinedTextField(
                        modifier = Modifier
                            .alpha(0.75f)
                            .gradientOverlay()
                            .fillMaxWidth(0.32f)
                            .constrainAs(msg_input) {
                                top.linkTo(parent.top)
                                absoluteLeft.linkTo(parent.absoluteLeft, 12.dp)
                            },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = {
                            if (keyboardOkFunction) {
                                val msgToSend = msg.value.also {
                                    it.replace("\\", "")
                                    if (it.length > 150) it.substring(0, 149)
                                }
                                if (msgToSend.isNotBlank()) {
                                    sendMessage(msgToSend)
                                }
                                msg.value = ""
                                msgCanSend.value = false
                            }
                            focusManager.clearFocus()
                        }),
                        label = { Text(text = "Type your message...", fontSize = 12.sp) },
                        trailingIcon = {
                            if (msgCanSend.value) {
                                IconButton(onClick = {
                                    val msgToSend = msg.value.also {
                                        it.replace("\\", "")
                                        if (it.length > 150) it.substring(0, 149)
                                    }
                                    if (msgToSend.isNotBlank()) {
                                        sendMessage(msgToSend)
                                    }
                                    msg.value = ""
                                    msgCanSend.value = false
                                    focusManager.clearFocus()
                                }) {
                                    Icon(imageVector = Icons.Filled.Send, "")
                                }
                            }
                        },
                        value = msg.value,
                        onValueChange = { s ->
                            msg.value = s
                            msgCanSend.value = s.isNotBlank()
                        }
                    )
                }

                /** Overall info (PING + ROOMNAME + OSD Messages) */
                if (hudVisibility.value && !isSoloMode() && !pipModeObserver) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .wrapContentWidth()
                            .constrainAs(overall_info) {
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                                top.linkTo(parent.top, 6.dp)
                            }
                    ) {
                        if (!isSoloMode()) {
                            Row {
                                Text(
                                    text = if (p.ping.doubleValue < 0) {
                                        "Disconnected"
                                    } else {
                                        "Connected - ${p.ping.doubleValue.toInt()}ms"
                                    },
                                    color = Paletting.OLD_SP_PINK
                                )
                                Spacer(Modifier.width(4.dp))
                                Image(
                                    when (p.ping.doubleValue) {
                                        (-1.0) -> painterResource(R.drawable.icon_unconnected)
                                        in (0.0..100.0) -> painterResource(R.drawable.ping_3)
                                        in (100.0..199.0) -> painterResource(R.drawable.ping_2)
                                        else -> painterResource(R.drawable.ping_1)
                                    }, "", Modifier.size(16.dp)
                                )

                            }
                            Text(text = "Room: ${p.session.currentRoom}", fontSize = 11.sp, color = Paletting.OLD_SP_PINK)
                        }

                    }
                }

                /** HUD ELEMENTS ONLY VISIBLE DURING VIDEO PLAYBACK */
                if (hasVideo.value && hudVisibility.value) {
                    val slidervalue = remember { timeCurrent }
                    val slidermax = remember { timeFull }
                    val interactionSource = remember { MutableInteractionSource() }

                    /** Shader behind the slider, and other adjacent elements to provide better constrast */
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = Paletting.SHADER_GRADIENT
                                )
                            )
                            .constrainAs(shadergradbottom) {
                                top.linkTo(ready_button.top)
                                bottom.linkTo(ready_button.bottom)
                                absoluteRight.linkTo(parent.absoluteRight)
                                absoluteLeft.linkTo(parent.absoluteLeft)
                                width = Dimension.fillToConstraints
                                height = Dimension.fillToConstraints
                            },
                    )
                    Slider(
                        value = slidervalue.longValue.toFloat(),
                        valueRange = (0f..(slidermax.longValue.toFloat())),
                        onValueChange = { f ->
                            lifecycleScope.launch {
                                if (isSoloMode()) {
                                    if (player == null) return@launch

                                    seeks.add(Pair(player!!.getPositionMs(), f.toLong() * 1000))
                                }

                                player?.seekTo(f.toLong() * 1000L)

                                sendSeek(f.toLong() * 1000L)
                            }
                            slidervalue.longValue = f.toLong()
                        },
                        modifier = Modifier
                            .alpha(0.82f)
                            .padding(horizontal = 12.dp)
                            .constrainAs(slider) {
                                bottom.linkTo(parent.bottom, 8.dp)
                                start.linkTo(ready_button.end, 108.dp)
                                end.linkTo(vidcontrolbutton.start, 108.dp)
                                width = Dimension.fillToConstraints
                            },
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
                    Text(
                        text = timeStamper(remember { timeCurrent }.longValue),
                        modifier = Modifier
                            .alpha(0.85f)
                            .gradientOverlay()
                            .constrainAs(media_time_current) {
                                top.linkTo(slider.top)
                                bottom.linkTo(slider.bottom)
                                start.linkTo(ready_button.end, 4.dp)
                                end.linkTo(slider.start, 4.dp)
                            },
                    )

                    val timeFullR = remember { timeFull }
                    Text(
                        text = if (timeFullR.longValue >= Long.MAX_VALUE / 1000L) "???" else timeStamper(timeFullR.longValue),
                        modifier = Modifier
                            .alpha(0.85f)
                            .gradientOverlay()
                            .constrainAs(media_time_full) {
                                top.linkTo(slider.top)
                                bottom.linkTo(slider.bottom)
                                start.linkTo(slider.end, 4.dp)
                                end.linkTo(vidcontrolbutton.start, 12.dp)
                            },
                    )

                    /** Alternative buttons to seek back/forward in case gestures are disabled */
                    if (!gestures.value) {
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.constrainAs(fast_seek_buttons) {
                            bottom.linkTo(slider.top, 2.dp)
                            absoluteLeft.linkTo(play_button.absoluteLeft)
                            absoluteRight.linkTo(play_button.absoluteRight)
                        }) {
                            FancyIcon2(icon = Icons.Filled.FastRewind, size = ROOM_ICON_SIZE + 6, shadowColor = Color.Black) {
                                seekBckwd()
                            }
                            Spacer(Modifier.width(24.dp))
                            FancyIcon2(icon = Icons.Filled.FastForward, size = ROOM_ICON_SIZE + 6, shadowColor = Color.Black) {
                                seekFwd()
                            }
                        }
                    }

                    /** PLAY BUTTON */
                    val playing = remember { isNowPlaying }
                    FancyIcon2(icon = when (playing.value) {
                        true -> Icons.Filled.Pause
                        false -> Icons.Filled.PlayArrow
                    }, size = (ROOM_ICON_SIZE * 2.25).roundToInt(), shadowColor = Color.Black,
                        modifier = Modifier.constrainAs(play_button) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            absoluteLeft.linkTo(parent.absoluteLeft)
                            absoluteRight.linkTo(parent.absoluteRight)
                        }) {
                        if (player?.isInPlayState() == true) {
                            pausePlayback()
                        } else {
                            playPlayback()
                        }
                    }

                    /** READY BUTTON */
                    if (!isSoloMode()) {
                        IconToggleButton(modifier = Modifier
                            .width(112.dp)
                            .padding(8.dp)
                            .constrainAs(ready_button) {
                                top.linkTo(slider.top)
                                bottom.linkTo(slider.bottom)
                                absoluteLeft.linkTo(parent.absoluteLeft, 26.dp)
                            }, checked = ready.value,
                            colors = IconButtonDefaults.iconToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = Color.Black
                            ),
                            onCheckedChange = { b ->
                                ready.value = b
                                p.ready = b
                                p.sendPacket(JsonSender.sendReadiness(b, true))
                            }) {
                            when (ready.value) {
                                true -> Text("Ready")
                                false -> Text("Not Ready")
                            }
                        }
                    }

                    FancyIcon2(
                        modifier = Modifier.constrainAs(vidcontrolbutton) {
                            top.linkTo(slider.top)
                            bottom.linkTo(slider.bottom)
                            end.linkTo(addmediabutton.start, 12.dp)
                        },
                        icon = Icons.Filled.VideoSettings, size = ROOM_ICON_SIZE + 6, shadowColor = Color.Black,
                        onClick = {
                            controlcardvisible.value = !controlcardvisible.value
                            addmediacardvisible.value = false
                        }
                    )

                    AnimatedVisibility(controlcardvisible.value,
                        enter = expandIn(),
                        modifier = Modifier
                            .zIndex(10f)
                            .constrainAs(vidcontrolcard) {
                                //top.linkTo(parent.top, 4.dp)
                                bottom.linkTo(vidcontrolbutton.top)
                                absoluteRight.linkTo(vidcontrolbutton.absoluteRight)
                                absoluteLeft.linkTo(vidcontrolbutton.absoluteLeft)
                                //height = Dimension.fillToConstraints
                            }) {
                        Card(
                            modifier = Modifier.fillMaxHeight(0.8f),
                            shape = CardDefaults.outlinedShape,
                            border = BorderStroke(2.dp, Color.Gray),
                            elevation = CardDefaults.outlinedCardElevation(defaultElevation = 8.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            FlowColumn(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxHeight(),
                                mainAxisAlignment = FlowMainAxisAlignment.SpaceBetween,
                                crossAxisAlignment = FlowCrossAxisAlignment.Center,
                                crossAxisSpacing = 12.dp
                            ) {
                                /* Aspect Ratio */
                                FancyIcon2(icon = Icons.Filled.AspectRatio, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                    val newAspectRatio = player?.switchAspectRatio(this@RoomUI)
                                    //TODO: Inform user about his new aspect ratio
                                }

                                /* Seek Gesture (DoNotTouch for disabling it) */
                                FancyIcon2(
                                    icon = when (gestures.value) {
                                        true -> Icons.Filled.TouchApp
                                        false -> Icons.Filled.DoNotTouch
                                    }, size = ROOM_ICON_SIZE, shadowColor = Color.Black
                                ) {
                                    gestures.value = !gestures.value
                                    if (gestures.value) {
                                        toasty("Double-tap to seek mode enabled.")
                                    }
                                }

                                /* Seek To */
                                FancyIcon2(icon = Icons.Filled.BrowseGallery, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                    controlcardvisible.value = false
                                    seektopopupstate.value = true
                                }

                                /* Undo Last Seek */
                                FancyIcon2(icon = Icons.Filled.History, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                    if (seeks.isEmpty()) {
                                        toasty("There is no recent seek in the room.")
                                        return@FancyIcon2
                                    }

                                    controlcardvisible.value = false

                                    val lastSeek = seeks.last()
                                    player?.seekTo(lastSeek.first)
                                    sendSeek(lastSeek.first)
                                    seeks.remove(lastSeek)
                                    toasty("Seek undone.")
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
                                            font = Font(R.font.directive4bold)
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
                                                wentForFilePick = true
                                                subtitlePicker.launch(arrayOf("*/*"))
                                                tracksPopup.value = false
                                                controlcardvisible.value = false
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
                                                with(player ?: return@DropdownMenuItem) {
                                                    selectTrack(C.TRACK_TYPE_TEXT, -1)
                                                }
                                                tracksPopup.value = false
                                                controlcardvisible.value = false

                                            }
                                        )

                                        for (track in (media?.subtitleTracks) ?: listOf()) {
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(checked = track.selected.value, onCheckedChange = {
                                                            with(player ?: return@Checkbox) {
                                                                selectTrack(C.TRACK_TYPE_TEXT, track.index)
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
                                                    with(player ?: return@DropdownMenuItem) {
                                                        selectTrack(C.TRACK_TYPE_TEXT, track.index)
                                                    }
                                                    tracksPopup.value = false
                                                    controlcardvisible.value = false

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
                                            font = Font(R.font.directive4bold)
                                        )

                                        for (track in (media?.audioTracks ?: listOf())) {
                                            DropdownMenuItem(text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(checked = track.selected.value, onCheckedChange = {
                                                        with(player ?: return@Checkbox) {
                                                            selectTrack(C.TRACK_TYPE_AUDIO, track.index)
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
                                                    with(player ?: return@DropdownMenuItem) {
                                                        selectTrack(C.TRACK_TYPE_AUDIO, track.index)
                                                    }
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

                if (hudVisibility.value) {
                    /** The button of adding media and its card */
                    AddVideoButton(
                        Modifier.constrainAs(addmediabutton) {
                            bottom.linkTo(parent.bottom, 8.dp)
                            end.linkTo(parent.end, 12.dp)
                        },
                        onClick = {
                            addmediacardvisible.value = !addmediacardvisible.value
                            controlcardvisible.value = false
                        }
                    )

                    AnimatedVisibility(addmediacardvisible.value,
                        modifier = Modifier
                            .zIndex(10f)
                            .constrainAs(addmediacard) {
                                bottom.linkTo(addmediabutton.top)
                                absoluteRight.linkTo(addmediabutton.absoluteRight)
                                absoluteLeft.linkTo(addmediabutton.absoluteLeft)
                            }) {
                        Card(
                            shape = CardDefaults.outlinedShape,
                            border = BorderStroke(2.dp, Color.Gray),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                /* Add file from storage */
                                FancyIcon2(icon = Icons.Filled.CreateNewFolder, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                    wentForFilePick = true
                                    mediaPicker.launch(arrayOf("video/*"))
                                }

                                /* Add link */
                                FancyIcon2(icon = Icons.Filled.AddLink, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                    addurlpopupstate.value = true
                                }
                            }
                        }
                    }


                    /** The tabs in the top-right corner */
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(50.dp)
                            .constrainAs(room_tabs) {
                                top.linkTo(parent.top, 14.dp)
                                absoluteRight.linkTo(parent.absoluteRight, 6.dp)
                            }) {

                        /** In-room settings */
                       RoomTab(icon = Icons.Filled.AutoFixHigh, visibilityState = inroomprefsVisibility.value) {
                            sharedplaylistVisibility.value = false
                            userinfoVisibility.value = false
                            inroomprefsVisibility.value = !inroomprefsVisibility.value
                        }

                        Spacer(Modifier.width(12.dp))

                        /** Shared Playlist */
                        if (!isSoloMode()) {
                            RoomTab(icon = Icons.Filled.PlaylistPlay, visibilityState = sharedplaylistVisibility.value) {
                                sharedplaylistVisibility.value = !sharedplaylistVisibility.value
                                userinfoVisibility.value = false
                                inroomprefsVisibility.value = false
                            }

                            Spacer(Modifier.width(12.dp))
                        }

                        /** User Info card tab */
                        if (!isSoloMode()) {
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
                                    font = Font(R.font.directive4bold)
                                )

                                /* Chat history item */
                                if (!isSoloMode()) {
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

                                        if (newMode) {
                                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                                        } else {
                                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                        }

                                        lifecycleScope.launch {
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
                                        terminate()
                                    }
                                )
                            }
                        }

                    }

                    /** User-info card (toggled on and off) */
                    if (!isSoloMode()) {
                        AnimatedVisibility(
                            modifier = Modifier.constrainAs(userinfocard) {
                                top.linkTo(room_tabs.bottom, 12.dp)
                                absoluteRight.linkTo(parent.absoluteRight, (-12).dp)
                                absoluteLeft.linkTo(room_tabs.absoluteLeft, 2.dp)
                                bottom.linkTo(addmediabutton.top, 8.dp)
                                height = if (p.session.userList.size < 4) Dimension.wrapContent else Dimension.fillToConstraints
                                width = Dimension.fillToConstraints
                                verticalBias = 0.0F
                            },
                            enter = slideInHorizontally(initialOffsetX = { (screenWidthPx + screenWidthPx * 0.3).toInt() }),
                            exit = slideOutHorizontally(targetOffsetX = { (screenWidthPx + screenWidthPx * 0.3).toInt() }),
                            visible = !inroomprefsVisibility.value && userinfoVisibility.value && !sharedplaylistVisibility.value
                        ) {
                            UserInfoCard()
                        }
                    }

                    /** Shared Playlist card (toggled on and off) */
                    if (!isSoloMode()) {
                        AnimatedVisibility(
                            modifier = Modifier.constrainAs(sharedplaylistcard) {
                                top.linkTo(room_tabs.bottom, 12.dp)
                                absoluteRight.linkTo(parent.absoluteRight, (-12).dp)
                                absoluteLeft.linkTo(room_tabs.absoluteLeft, (-12).dp)
                                bottom.linkTo(addmediabutton.top, 8.dp)
                                height = Dimension.fillToConstraints
                                width = Dimension.fillToConstraints
                                verticalBias = 0.0F
                            },
                            enter = slideInHorizontally(initialOffsetX = { (screenWidthPx + screenWidthPx * 0.3).toInt() }),
                            exit = slideOutHorizontally(targetOffsetX = { (screenWidthPx + screenWidthPx * 0.3).toInt() }),
                            visible = !inroomprefsVisibility.value && !userinfoVisibility.value && sharedplaylistVisibility.value
                        ) {
                            SharedPlaylistCard()
                        }
                    }

                    /** In-room card (toggled on and off) */
                    AnimatedVisibility(
                        modifier = Modifier.constrainAs(inroomsettingscard) {
                            top.linkTo(room_tabs.bottom, 12.dp)
                            absoluteRight.linkTo(parent.absoluteRight, (-2).dp)
                            absoluteLeft.linkTo(room_tabs.absoluteLeft, ((if (isSoloMode()) -124 else -12)).dp)
                            bottom.linkTo(addmediabutton.top, 8.dp)
                            height = Dimension.fillToConstraints
                            width = Dimension.fillToConstraints
                            verticalBias = 0.0F
                        },
                        enter = slideInHorizontally(initialOffsetX = { (screenWidthPx + screenWidthPx * 0.3).toInt() }),
                        exit = slideOutHorizontally(targetOffsetX = { (screenWidthPx + screenWidthPx * 0.3).toInt() }),
                        visible = inroomprefsVisibility.value && !userinfoVisibility.value && !sharedplaylistVisibility.value
                    ) {

                        InRoomSettingsCard()
                    }
                }

            }
        }

        /** The layout for the fading messages & OSD messages (when HUD is hidden, or when screen is locked) */
        if (!isSoloMode()) {
            if (!hudVisibility.value) {
                ConstraintLayout(modifier = Modifier.fillMaxSize()) {

                    val (fadingmsg) = createRefs()

                    var visibility by remember { mutableStateOf(false) }

                    LaunchedEffect(p.session.messageSequence.size) {
                        val lastMsg = p.session.messageSequence.last()

                        if (!lastMsg.isMainUser && !lastMsg.seen) {
                            visibility = true
                            delay(fadingTimeout.value.toLong() * 1000L)
                            visibility = false
                        }
                    }

                    AnimatedVisibility(
                        enter = fadeIn(animationSpec = keyframes { durationMillis = 100 }),
                        exit = fadeOut(animationSpec = keyframes { durationMillis = 500 }),
                        visible = visibility,
                        modifier = Modifier.constrainAs(fadingmsg) {
                            top.linkTo(parent.top, 12.dp)
                            absoluteLeft.linkTo(parent.absoluteLeft, 14.dp)
                        }
                    ) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .focusable(false),
                            overflow = TextOverflow.Ellipsis,
                            text = p.session.messageSequence.last().factorize(
                                timestampColor = Color(colorTimestamp.value),
                                selftagColor = Color(colorSelftag.value),
                                friendtagColor = Color(colorFriendtag.value),
                                systemmsgColor = Color(colorSystem.value),
                                usermsgColor = Color(colorUserchat.value),
                                errormsgColor = Color(colorError.value),
                                includeTimestamp = msgIncludeTimestamp.value
                            ),
                            lineHeight = if (pipModeObserver) 9.sp else 15.sp,
                            fontSize = if (pipModeObserver) 8.sp else 13.sp
                        )
                    }
                }
            }
        }

        /** Popups */

        AddUrlPopup(visibilityState = addurlpopupstate)

        SeekToPositionPopup(visibilityState = seektopopupstate)

        if (!isSoloMode()) ChatHistoryPopup(visibilityState = chathistorypopupstate)
    }
}
