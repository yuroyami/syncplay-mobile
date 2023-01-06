package app.ui.activities

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Subtitles
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.lifecycleScope
import app.R
import app.ui.Paletting
import app.ui.Paletting.ROOM_ICON_SIZE
import app.ui.compose.ComposeUtils
import app.ui.compose.ComposeUtils.FancyIcon2
import app.ui.compose.ComposeUtils.syncplayGradient
import app.utils.ExoUtils.nextAspectRatio
import app.utils.ExoUtils.pausePlayback
import app.utils.ExoUtils.playPlayback
import app.utils.ExoUtils.selectTrack
import app.utils.MiscUtils.getFileName
import app.utils.MiscUtils.string
import app.utils.MiscUtils.timeStamper
import app.utils.RoomUtils.sendMessage
import app.utils.RoomUtils.sendSeek
import app.utils.UIUtils.toasty
import app.wrappers.MediaFile
import com.google.accompanist.flowlayout.FlowColumn
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.util.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Collections
import kotlin.math.roundToInt

object WatchActivityUI {

    @OptIn(ExperimentalTextApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun WatchActivity.RoomUI() {
        /** Remembering coroutine scope to launch functions later */
        val generalScope = rememberCoroutineScope()
        val hudScope = rememberCoroutineScope()
        val context = LocalContext.current
        val focusManager = LocalFocusManager.current
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val screenHeightDp = configuration.screenHeightDp.dp
        val screenWidthDp = configuration.screenWidthDp.dp
        val screenHeightPx = with(density) { screenHeightDp.roundToPx() }
        val screenWidthPx = with(density) { screenWidthDp.roundToPx() }
        val videoVisibility = remember { videoSurfaceVisibility }
        val hasVideo = remember { mutableStateOf(false) }

        val hudVisibility = remember { mutableStateOf(true) }

        val blurStuff = remember { mutableStateOf(false) }

        /** Adding artwork layout */
        val artworkVisible = remember { mutableStateOf(true) }
        if (artworkVisible.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = Brush.linearGradient(colors = Paletting.BG_Gradient_DARK)),
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
                            .height(84.dp)
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
                                fontSize = 26.sp,
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
                                fontSize = 26.sp,
                            )
                        )
                    }
                }
            }
        }

        /** ExoPlayer video surface, we use AnimatedVisibility to keep it hidden with no video on */
        val seekLeftInteraction: MutableInteractionSource = remember { MutableInteractionSource() }
        val seekRightInteraction: MutableInteractionSource = remember { MutableInteractionSource() }

        if (videoVisibility.value) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { playerView }
            )
        }

        /** ExoPlayer overlay layout for controls, messages, etc. */
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {

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

            /* Top-level variables which scaffold things together */

            val msg = remember { mutableStateOf("") }
            val msgCanSend = remember { mutableStateOf(false) }
            val msgs = remember { p.session.messageSequence }
            val ready = remember { mutableStateOf(false) }
            val controlcardvisible = remember { mutableStateOf(false) }
            val addmediacardvisible = remember { mutableStateOf(false) }
            val gestures = remember { mutableStateOf(true) }

            /** Now for Activity intent contract variables */
            val mediaPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult

                    /* Changing UI (hiding artwork, showing media controls, hiding add media popup */
                    artworkVisible.value = false
                    hasVideo.value = true
                    addmediacardvisible.value = false
                    videoVisibility.value = true

                    lifecycleScope.launch(Dispatchers.Main) {
                        /* Hiding the popup card for media adding */

                        /* Creating a media file from the selected file */
                        media = MediaFile()
                        media?.uri = uri

                        /* Obtaining info from it (size and name) */
                        media?.collectInfo(applicationContext)

                        /* Checking mismatches with others in room */
                        //checkFileMismatches(p) TODO

                        /* Injecting the media into exoplayer */
                        try {
                            /* This is the builder responsible for building a MediaItem component for ExoPlayer **/
                            val vid = MediaItem.Builder().setUri(uri).also {
                                /* Seeing if we have any loaded external sub **/
                                if (media?.externalSub != null) {
                                    it.setSubtitleConfigurations(Collections.singletonList(media!!.externalSub!!))
                                }
                            }.build()

                            /* Injecting it into ExoPlayer and getting relevant info **/
                            myExoPlayer.setMediaItem(vid) /* This loads the media into ExoPlayer **/
                            myExoPlayer.prepare() /* This prepares it and makes the first frame visible */

                            /* Goes back to the beginning */
                            p.currentVideoPosition = 0.0

                            /* Updating play button */
                            timeFull.value = if (myExoPlayer.duration < 0) 0 else myExoPlayer.duration

                            /* Seeing if we have to start over TODO **/
                            //if (startFromPosition != (-3.0).toLong()) myExoPlayer?.seekTo(startFromPosition)


                            /* TODO Updating our Shared Playlist fragment content (if it exists on foreground) */
                            //sharedPlaylistCallback?.onUpdate()

                        } catch (e: IOException) {
                            /* If, for some reason, the video didn't wanna load */
                            e.printStackTrace()
                            toasty("There was a problem loading this file.")
                        }

                        /* Finally, show a a toast to the user that the media file has been added */
                        delay(1000)
                        toasty(string(R.string.room_selected_vid, "${media?.fileName}"))
                    }
                }
            )

            val subtitlePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult;

                    if (myExoPlayer.mediaItemCount != 0) {
                        val filename = getFileName(uri = uri).toString()
                        val extension = filename.substring(filename.length - 4)

                        val mimeType =
                            if (extension.contains("srt")) MimeTypes.APPLICATION_SUBRIP
                            else if ((extension.contains("ass"))
                                || (extension.contains("ssa"))
                            ) MimeTypes.TEXT_SSA
                            else if (extension.contains("ttml")) MimeTypes.APPLICATION_TTML
                            else if (extension.contains("vtt")) MimeTypes.TEXT_VTT else ""

                        if (mimeType != "") {
                            media!!.externalSub = MediaItem.SubtitleConfiguration.Builder(uri)
                                .setUri(uri)
                                .setMimeType(mimeType)
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()
                            toasty(string(R.string.room_selected_sub, filename))
                        } else {
                            toasty(getString(R.string.room_selected_sub_error))
                        }
                    } else {
                        toasty(getString(R.string.room_sub_error_load_vid_first))
                    }
                }
            )

            /** Messages */
            if (hudVisibility.value) {
                LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    userScrollEnabled = false,
                    modifier = Modifier
                        .constrainAs(msg_box) {
                            top.linkTo(msg_input.bottom, 4.dp)
                            absoluteLeft.linkTo(parent.absoluteLeft, 12.dp)
                            absoluteRight.linkTo(msg_input.absoluteRight)
                            width = Dimension.fillToConstraints
                        }
                        .focusable(false)
                    //.clickable(enabled = false) {}
                ) {
                    //TODO: AnnotatedString and colors
                    //TODO: change number of taken msgs
                    //TODO: Show errors in red
                    items(msgs.takeLast(10)) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = false) {}
                                .focusable(false),
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis,
                            text = it.content,
                            fontSize = 10.sp)
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
                                        myExoPlayer.seekBack()

                                        val press = PressInteraction.Press(Offset.Zero)
                                        seekLeftInteraction.emit(press)
                                        delay(150)
                                        seekLeftInteraction.emit(PressInteraction.Release(press))
                                    }
                                    if (offset.x > screenWidthPx.times(0.85f)) {
                                        myExoPlayer.seekForward()

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


            /** Message input box TODO: IME's ok will send msg too depending on setting */
            if (hudVisibility.value) {
                OutlinedTextField(
                    modifier = Modifier
                        .alpha(0.75f)
                        .syncplayGradient()
                        .fillMaxWidth(0.32f)
                        .constrainAs(msg_input) {
                            top.linkTo(parent.top)
                            absoluteLeft.linkTo(parent.absoluteLeft, 12.dp)
                        },
                    singleLine = true,
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

            if (hudVisibility.value) {
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
                    Row {
                        Text(text = "Connected - ", color = Paletting.SP_INTENSE_PINK)
                        Text(text = "30ms", color = Paletting.SP_INTENSE_PINK)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            painterResource(R.drawable.ping_1), "", Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(text = "Room: ${p.session.currentRoom}", fontSize = 11.sp, color = Paletting.SP_INTENSE_PINK)

                }
            }

            /** Now, for the elements that are only visible during media playback */
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
                Slider(value = slidervalue.value.toFloat(),
                    valueRange = (0f..(slidermax.value.toFloat())),
                    onValueChange = { f ->
                        generalScope.launch {
                            val newPos = f.toLong() * 1000L
                            myExoPlayer.seekTo(newPos)
                            slidervalue.value = f.toLong()
                            sendSeek(newPos)
                        }
                    }, modifier = Modifier
                        .alpha(0.82f)
                        .padding(horizontal = 12.dp)
                        .constrainAs(slider) {
                            bottom.linkTo(parent.bottom, 8.dp)
                            start.linkTo(ready_button.end, 108.dp)
                            end.linkTo(vidcontrolbutton.start, 108.dp)
                            width = Dimension.fillToConstraints
                        },
                    interactionSource = interactionSource,
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
                    text = timeStamper(remember { timeCurrent }.value),
                    modifier = Modifier
                        .alpha(0.85f)
                        .syncplayGradient()
                        .constrainAs(media_time_current) {
                            top.linkTo(slider.top)
                            bottom.linkTo(slider.bottom)
                            start.linkTo(ready_button.end, 4.dp)
                            end.linkTo(slider.start, 4.dp)
                        },
                )
                Text(
                    text = timeStamper(remember { timeFull }.value),
                    modifier = Modifier
                        .alpha(0.85f)
                        .syncplayGradient()
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
                        absoluteLeft.linkTo(slider.absoluteLeft)
                        absoluteRight.linkTo(slider.absoluteRight)
                    }) {
                        FancyIcon2(icon = Icons.Filled.FastRewind, size = ROOM_ICON_SIZE + 6, shadowColor = Color.Black) {
                            //TODO: get seek increment from settings
                            myExoPlayer.seekBack()
                        }
                        Spacer(Modifier.width(24.dp))
                        FancyIcon2(icon = Icons.Filled.FastForward, size = ROOM_ICON_SIZE + 6, shadowColor = Color.Black) {
                            //TODO: get seek increment from settings
                            myExoPlayer.seekForward()
                        }
                    }
                }

                val playing = remember { exoPlaying }
                FancyIcon2(icon = when (playing.value) {
                    true -> Icons.Filled.Pause
                    false -> Icons.Filled.PlayArrow
                }, size = (ROOM_ICON_SIZE * 2.25).roundToInt(), shadowColor = Color.Black,
                    modifier = Modifier.constrainAs(play_button) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        absoluteLeft.linkTo(slider.absoluteLeft)
                        absoluteRight.linkTo(slider.absoluteRight)
                    }) {
                    if (myExoPlayer.playbackState == Player.STATE_READY && myExoPlayer.playWhenReady) {
                        pausePlayback()

                    } else {
                        playPlayback()
                    }
                }

                IconToggleButton(modifier = Modifier
                    .width(112.dp)
                    .padding(8.dp)
                    .constrainAs(ready_button) {
                        top.linkTo(slider.top)
                        bottom.linkTo(slider.bottom)
                        absoluteLeft.linkTo(parent.absoluteLeft, 26.dp)
                    }, checked = ready.value,
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        containerColor = Color.DarkGray,
                        contentColor = MaterialTheme.colorScheme.primary,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = Color.White
                    ),
                    onCheckedChange = { ready.value = !ready.value }) {
                    when (ready.value) {
                        true -> Text("Ready")
                        false -> Text("Not Ready")
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
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.DarkGray)
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
                                val newAspectRatio = nextAspectRatio()
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
                            }

                            /* Seek To */
                            FancyIcon2(icon = Icons.Filled.BrowseGallery, size = ROOM_ICON_SIZE, shadowColor = Color.Black)

                            /* Undo Last Seek */
                            FancyIcon2(icon = Icons.Filled.History, size = ROOM_ICON_SIZE, shadowColor = Color.Black)

                            /* Subtitle Tracks */
                            Box {
                                val tracksPopup = remember { mutableStateOf(false) }

                                FancyIcon2(icon = Icons.Filled.Subtitles, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                    media?.analyzeTracks(myExoPlayer)
                                    tracksPopup.value = true
                                }


                                DropdownMenu(
                                    modifier = Modifier.background(color = Color.DarkGray),
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
                                        text = { Text("Disable subtitles.", color = Color.LightGray) },
                                        onClick = {
                                            selectTrack(C.TRACK_TYPE_TEXT, -1)
                                            tracksPopup.value = false
                                        }
                                    )

                                    for ((index, track) in (media?.subtitleTracks)?.withIndex() ?: listOf()) {
                                        DropdownMenuItem(text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = track.selected.value, onCheckedChange = {
                                                    selectTrack(C.TRACK_TYPE_TEXT, index)
                                                    tracksPopup.value = false
                                                })

                                                val trackName = track.format?.label ?: getString(R.string.room_track_track)
                                                Text(
                                                    color = Color.LightGray,
                                                    text = "$trackName [${track.format?.language?.uppercase() ?: "UND"}]"
                                                )
                                            }
                                        },
                                            onClick = {
                                                selectTrack(C.TRACK_TYPE_TEXT, index)
                                                tracksPopup.value = false
                                            }
                                        )
                                    }
                                }
                            }

                            /* Audio Tracks */
                            Box {
                                val tracksPopup = remember { mutableStateOf(false) }

                                FancyIcon2(icon = Icons.Filled.SpeakerGroup, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                    media?.analyzeTracks(myExoPlayer)
                                    tracksPopup.value = true
                                }

                                DropdownMenu(
                                    modifier = Modifier.background(color = Color.DarkGray),
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

                                    for ((index, track) in (media?.audioTracks)?.withIndex() ?: listOf()) {
                                        DropdownMenuItem(text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = track.selected.value, onCheckedChange = {
                                                    selectTrack(C.TRACK_TYPE_AUDIO, index)
                                                    tracksPopup.value = false
                                                })

                                                val trackName = track.format?.label ?: getString(R.string.room_track_track)
                                                Text(
                                                    color = Color.LightGray,
                                                    text = "$trackName [${track.format?.language?.uppercase() ?: "UND"}]"
                                                )
                                            }
                                        },
                                            onClick = {
                                                selectTrack(C.TRACK_TYPE_AUDIO, index)
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
                FancyIcon2(
                    modifier = Modifier.constrainAs(addmediabutton) {
                        bottom.linkTo(parent.bottom, 8.dp)
                        end.linkTo(parent.end, 12.dp)
                    },
                    icon = Icons.Filled.AddToQueue, size = ROOM_ICON_SIZE + 6, shadowColor = Color.Black,
                    onClick = {
                        addmediacardvisible.value = !addmediacardvisible.value
                        controlcardvisible.value = false
                    })

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
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.DarkGray)
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            /* Add file from storage */
                            FancyIcon2(icon = Icons.Filled.CreateNewFolder, size = ROOM_ICON_SIZE, shadowColor = Color.Black) {
                                mediaPicker.launch(arrayOf("video/*"))
                            }

                            /* Add link */
                            FancyIcon2(icon = Icons.Filled.AddLink, size = ROOM_ICON_SIZE, shadowColor = Color.Black)
                        }
                    }
                }


                /** The tabs in the top-right corner */

                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(50.dp)
                        .constrainAs(room_tabs) {
                            top.linkTo(parent.top, 14.dp)
                            absoluteRight.linkTo(parent.absoluteRight)
                        }) {

                    /** In-room settings */
                    Card(
                        modifier = Modifier
                            .width(48.dp)
                            .aspectRatio(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(color = Paletting.SP_ORANGE)
                            ) {

                            },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.AutoFixHigh,
                                contentDescription = "",
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.Center)
                                    .syncplayGradient()
                            )

                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    /** Shared Playlist */
                    Card(
                        modifier = Modifier
                            .width(48.dp)
                            .aspectRatio(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(color = Paletting.SP_ORANGE)
                            ) {

                            },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.PlaylistPlay,
                                contentDescription = "",
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.Center)
                                    .syncplayGradient()
                            )

                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    /** User Info card tab */
                    Card(
                        modifier = Modifier
                            .width(48.dp)
                            .aspectRatio(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(color = Paletting.SP_ORANGE)
                            ) {

                            },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.Diversity3,
                                contentDescription = "",
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.Center)
                                    .syncplayGradient()
                            )

                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    /** Lock card */
                    Card(
                        modifier = Modifier
                            .width(48.dp)
                            .aspectRatio(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(color = Paletting.SP_ORANGE)
                            ) {

                            },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "",
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.Center)
                                    .syncplayGradient()
                            )

                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    FancyIcon2(icon = Icons.Filled.MoreVert, size = ROOM_ICON_SIZE, shadowColor = Color.Black)

                }
            }

            /** Popup for in-room settings */

        }
    }
}
