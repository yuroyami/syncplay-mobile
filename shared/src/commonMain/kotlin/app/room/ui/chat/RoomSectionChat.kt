package app.room.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalChatPalette
import app.LocalRoomViewmodel
import app.preferences.Preferences.MSG_BG_OPACITY
import app.preferences.Preferences.MSG_BOX_ACTION
import app.preferences.Preferences.MSG_FONTSIZE
import app.preferences.Preferences.MSG_MAXCOUNT
import app.preferences.Preferences.MSG_OUTLINE_ACTIVATE
import app.preferences.Preferences.MSG_OUTLINE_THICKNESS
import app.preferences.Preferences.MSG_SHADOW_ACTIVATE
import app.preferences.watchPref
import app.room.RoomViewmodel
import app.theme.Theming.flexibleGradient
import app.uicomponents.FlexibleAnnotatedText
import app.uicomponents.gradientOverlay
import app.utils.isEmoji
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_type_message

@Composable
fun RoomChatSection(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val isChatSupported by viewmodel.protocol.supportsChat.collectAsState()
    val gifPanelVisible by viewmodel.uiState.gifPanelVisible.collectAsState()
    val msg by viewmodel.uiState.msg.collectAsState()

    if (isChatSupported) {
        Column(modifier = modifier) {
            ChatTextField(
                viewmodel = viewmodel,
                modifier = Modifier.fillMaxWidth(),
                gifPanelVisible = gifPanelVisible
            )

            if (gifPanelVisible) {
                GifPanel(
                    query = msg,
                    onGifSelected = { gifUrl ->
                        viewmodel.dispatcher.sendMessage(gifUrl)
                        viewmodel.uiState.gifPanelVisible.value = false
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ChatBox(viewmodel = viewmodel, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun ChatTextField(
    modifier: Modifier = Modifier,
    viewmodel: RoomViewmodel,
    gifPanelVisible: Boolean
) {
    val focusManager = LocalFocusManager.current
    val msg by viewmodel.uiState.msg.collectAsState()
    val canSendWithKeyboardOK by MSG_BOX_ACTION.watchPref()
    val gradientBrush = Brush.linearGradient(colors = flexibleGradient)
    val msgIsNotEmpty by derivedStateOf { msg.isNotEmpty() }

    fun send() {
        val msgToSend = msg.replace("\\", "").take(149)
        if (msgToSend.isNotBlank()) {
            viewmodel.dispatcher.sendMessage(msgToSend)
        }

        viewmodel.uiState.msg.value = ""
        viewmodel.uiState.gifPanelVisible.value = false
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = modifier.alpha(0.75f).weight(1f),
            singleLine = true,
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (canSendWithKeyboardOK && !gifPanelVisible) {
                        send()
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            label = {
                Text(
                    text = stringResource(Res.string.room_type_message),
                    fontSize = 12.sp,
                    maxLines = 1,
                    style = TextStyle(brush = gradientBrush)
                )
            },
            trailingIcon = {
                Row {
                    if (msg.isNotBlank() && !gifPanelVisible) {
                        /* Send button */
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                send()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.gradientOverlay()
                            )
                        }
                    } else {
                        /* GIF button */
                        IconButton(onClick = {
                            viewmodel.uiState.gifPanelVisible.value = !viewmodel.uiState.gifPanelVisible.value
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.GifBox,
                                contentDescription = null,
                                modifier = Modifier.gradientOverlay()
                            )
                        }
                    }
                }
            },
            value = msg,
            onValueChange = { viewmodel.uiState.msg.value = it },
            visualTransformation = VisualTransformation { text ->
                val annotatedString = buildAnnotatedString {
                    text.text.forEach { char ->
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
            }
        )

        /* Clear (X) button */
        IconButton(
            modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            enabled = msgIsNotEmpty,
            onClick = {
                viewmodel.uiState.msg.value = ""
                viewmodel.uiState.gifPanelVisible.value = false
            },
        ) {
            if (msgIsNotEmpty) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.gradientOverlay()
                )
            }
        }
    }
}

@Composable
fun ChatBox(modifier: Modifier = Modifier, viewmodel: RoomViewmodel) {
    val hasVideo by viewmodel.hasVideo.collectAsState()

    val chatMessages = remember { if (!viewmodel.isSoloMode) viewmodel.session.messageSequence else mutableStateListOf() }

    val msgBoxOpacity = MSG_BG_OPACITY.watchPref()
    val msgOutlineActivate by MSG_OUTLINE_ACTIVATE.watchPref()
    val msgOutlineThickness by MSG_OUTLINE_THICKNESS.watchPref()
    val msgShadowActivate by MSG_SHADOW_ACTIVATE.watchPref()
    val msgFontSize = MSG_FONTSIZE.watchPref()
    val msgMaxCount by MSG_MAXCOUNT.watchPref()

    Box(
        modifier = modifier
            .background(
                color = if (hasVideo) Color(50, 50, 50, msgBoxOpacity.value) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        val latestChatMessages by remember {
            derivedStateOf {
                chatMessages.takeLast(msgMaxCount)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(latestChatMessages) { chatMessage ->
                /* Once seen, don't use it in fading message */
                SideEffect {
                    chatMessage.seen = true
                }

                FlexibleAnnotatedText(
                    modifier = Modifier.fillMaxWidth().focusable(enabled = false).clickable(enabled = false) {},
                    text = chatMessage.factorize(LocalChatPalette.current),
                    size = /* TODO if (pipModeObserver) 6f else*/ (msgFontSize.value.toFloat()),
                    shadowColors = if (msgShadowActivate) listOf(Color.Black) else listOf(),
                    strokeColors = if (msgOutlineActivate) listOf(Color.Black, Color.Black) else listOf(),
                    strokeWidth = msgOutlineThickness.toFloat()
                )
            }
        }
    }
}