package com.yuroyami.syncplay.ui.screens.room.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.yuroyami.syncplay.logic.SyncplayViewmodel
import com.yuroyami.syncplay.logic.datastore.DataStoreKeys.PREF_INROOM_MSG_BG_OPACITY
import com.yuroyami.syncplay.logic.datastore.DataStoreKeys.PREF_INROOM_MSG_BOX_ACTION
import com.yuroyami.syncplay.logic.datastore.DataStoreKeys.PREF_INROOM_MSG_FONTSIZE
import com.yuroyami.syncplay.logic.datastore.DataStoreKeys.PREF_INROOM_MSG_MAXCOUNT
import com.yuroyami.syncplay.logic.datastore.DataStoreKeys.PREF_INROOM_MSG_OUTLINE
import com.yuroyami.syncplay.logic.datastore.DataStoreKeys.PREF_INROOM_MSG_SHADOW
import com.yuroyami.syncplay.logic.datastore.valueAsState
import com.yuroyami.syncplay.ui.screens.adam.LocalChatPalette
import com.yuroyami.syncplay.ui.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.utils.ChatAnnotatedText
import com.yuroyami.syncplay.utils.CommonUtils.isEmoji
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_type_message


@Composable
fun RoomChatSection(modifier: Modifier) {
    val viewmodel = LocalViewmodel.current

    Column(modifier = modifier) {
        ChatTextField(viewmodel = viewmodel, modifier = Modifier.fillMaxWidth())

        ChatBox(viewmodel = viewmodel, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun ChatTextField(
    modifier: Modifier = Modifier,
    viewmodel: SyncplayViewmodel
) {
    val focusManager = LocalFocusManager.current

    var msg by remember { mutableStateOf("") }
    val canSendWithKeyboardOK by PREF_INROOM_MSG_BOX_ACTION.valueAsState(true)

    val gradientBrush = Brush.linearGradient(colors = Theming.SP_GRADIENT)

    fun send() {
        val msgToSend = msg.replace("\\", "").take(149)
        if (msgToSend.isNotBlank()) viewmodel.actionManager.sendMessage(msgToSend)

        msg = ""
        focusManager.clearFocus()
    }

    OutlinedTextField(
        modifier = modifier.alpha(0.75f).fillMaxWidth(),
        singleLine = true,
        keyboardActions = KeyboardActions(onDone = { if (canSendWithKeyboardOK) send() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Theming.SP_GRADIENT.first(),
            unfocusedBorderColor = Theming.SP_GRADIENT[1],
            cursorColor = Theming.SP_GRADIENT.last()
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
            if (msg.isNotBlank()) {
                IconButton(onClick = {
                    send()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(alpha = 0.99f).drawWithCache {
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    brush = gradientBrush, blendMode = BlendMode.SrcAtop
                                )
                            }
                        }
                    )
                }
            } else {
                IconButton(onClick = {
                    viewmodel.osdManager.dispatchOSD { "GIF Feature is coming soon!" }
                }) {
                    Icon(
                        imageVector = Icons.Outlined.GifBox,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(alpha = 0.99f).drawWithCache {
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    brush = gradientBrush, blendMode = BlendMode.SrcAtop
                                )
                            }
                        }
                    )
                }
            }
        },
        value = msg,
        onValueChange = { msg = it },
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
        }
    )
}

@Composable
fun ChatBox(modifier: Modifier = Modifier, viewmodel: SyncplayViewmodel) {
    val hasVideo by viewmodel.hasVideo.collectAsState()

    val chatMessages = remember { if (!viewmodel.isSoloMode) viewmodel.session.messageSequence else mutableStateListOf() }

    val msgBoxOpacity = PREF_INROOM_MSG_BG_OPACITY.valueAsState(0)
    val msgOutline by PREF_INROOM_MSG_OUTLINE.valueAsState(true)
    val msgShadow by PREF_INROOM_MSG_SHADOW.valueAsState(false)
    val msgFontSize = PREF_INROOM_MSG_FONTSIZE.valueAsState(9)
    val msgMaxCount by PREF_INROOM_MSG_MAXCOUNT.valueAsState(10)

    Box(
        modifier = modifier
            .background(
                color = if (hasVideo) Color(50, 50, 50, msgBoxOpacity.value) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        val latestChatMessages = chatMessages.takeLast(msgMaxCount)

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

                ChatAnnotatedText(
                    modifier = Modifier.fillMaxWidth().focusable(enabled = false).clickable(enabled = false) {},
                    text = chatMessage.factorize(LocalChatPalette.current),
                    size = /* TODO if (pipModeObserver) 6f else*/ (msgFontSize.value.toFloat()),
                    hasShadow = msgShadow,
                    hasStroke = msgOutline
                )
            }
        }
    }
}