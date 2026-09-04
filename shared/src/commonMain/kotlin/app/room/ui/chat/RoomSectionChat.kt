package app.room.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.LocalChatPalette
import app.LocalRoomViewmodel
import app.room.OSDCategory
import app.preferences.Preferences.MSG_BG_OPACITY
import app.preferences.Preferences.MSG_BOX_ACTION
import app.preferences.Preferences.MSG_FONTSIZE
import app.preferences.Preferences.MSG_OUTLINE_THICKNESS
import app.preferences.Preferences.MSG_SHADOW_ACTIVATE
import app.preferences.watchPref
import app.room.RoomViewmodel
import app.theme.Radius
import app.room.SlashCommand
import app.room.parseSlashCommand
import app.room.runSlashCommand
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.Field
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.SendGlyph
import app.utils.Platform
import app.utils.platform
import androidx.compose.foundation.text.selection.SelectionContainer
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_chat_input
import syncplaymobile.shared.generated.resources.room_chat_too_long
import syncplaymobile.shared.generated.resources.room_gif_open
import syncplaymobile.shared.generated.resources.room_send

/** The chat dock: the composer on top, then either the message list or the GIF drawer. */
@Composable
fun RoomChatSection(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val isChatSupported by viewmodel.protocol.supportsChat.collectAsState()
    val gifPanelVisible by viewmodel.uiState.gifPanelVisible.collectAsState()
    val msg by viewmodel.uiState.msg.collectAsState()
    val isHUDVisible by viewmodel.uiState.visibleHUD.collectAsState()

    if (isChatSupported) {
        /* The cutout inset and side margins go on each child, not the column, so the strip beside
         * a camera notch belongs to the row in front of it instead of the HUD dismiss underneath. */
        val cutoutInsets = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)

        Column(modifier = modifier) {
            ChatComposer(
                viewmodel = viewmodel,
                modifier = Modifier
                    .fillMaxWidth()
                    // Tap shield first, then the insets: a fat-finger miss around the input is a no-op.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .windowInsetsPadding(cutoutInsets)
                    .padding(horizontal = 8.dp),
                gifPanelVisible = gifPanelVisible,
                isHUDVisible = isHUDVisible,
            )

            if (gifPanelVisible) {
                GifPanel(
                    query = msg,
                    onGifSelected = { gifUrl ->
                        viewmodel.dispatcher.sendMessage(gifUrl)
                        viewmodel.uiState.gifPanelVisible.value = false
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(cutoutInsets).padding(horizontal = 8.dp),
                    isHUDVisible = isHUDVisible,
                )
            } else {
                ChatBox(
                    viewmodel = viewmodel,
                    modifier = Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(cutoutInsets).padding(horizontal = 8.dp),
                    isHUDVisible = isHUDVisible,
                )
            }
        }
    }
}

/**
 * The composer: a GIF glyph, the hairline field, and a send glyph that turns accent once there
 * is text. The keyboard's send action keeps its switch and is suppressed while the drawer is
 * open, because the text is then a search query. A blank send changes nothing.
 */
@Composable
fun ChatComposer(
    viewmodel: RoomViewmodel,
    modifier: Modifier = Modifier,
    gifPanelVisible: Boolean,
    isHUDVisible: Boolean,
) {
    val p = palette
    val focusManager = LocalFocusManager.current
    val msg by viewmodel.uiState.msg.collectAsState()
    val canSendWithKeyboard by MSG_BOX_ACTION.watchPref()
    val hasText = msg.isNotBlank()
    val keyboardSends = canSendWithKeyboard && !gifPanelVisible

    // The input must not stay focused behind an invisible overlay with the keyboard up.
    LaunchedEffect(isHUDVisible) {
        if (!isHUDVisible) focusManager.clearFocus(force = true)
    }

    val chatScope = rememberCoroutineScope()

    fun send() {
        /* The cap is the server's own limit, floored at 1 so a hostile zero cannot eat every
         * message. Nothing is stripped: the JSON layer escapes backslashes itself. */
        val maxLen = viewmodel.session.roomFeatures.maxChatMessageLength.coerceAtLeast(1)
        val text = msg.trimEnd()
        if (text.isBlank()) return
        if (text.length > maxLen) {
            // The draft stays in the field; a silent cut lost the end of the message.
            val length = text.length
            viewmodel.dispatchOSD(OSDCategory.WARNING) { getString(Res.string.room_chat_too_long, length, maxLen) }
            return
        }
        /* A slash command is carried out here and never reaches the room, so a typo is
         * nobody else's business. Anything else is an ordinary message. */
        val command = parseSlashCommand(text)
        if (command == SlashCommand.NotACommand) {
            viewmodel.dispatcher.sendMessage(text)
        } else {
            chatScope.launch { viewmodel.runSlashCommand(command) }
        }
        viewmodel.uiState.msg.value = ""
        viewmodel.uiState.gifPanelVisible.value = false
    }

    Row(modifier.heightIn(min = Space.row), verticalAlignment = Alignment.CenterVertically) {
        GlyphButton(
            icon = Icons.Outlined.GifBox,
            name = stringResource(Res.string.room_gif_open),
            tint = if (gifPanelVisible) p.accent else p.inkDim,
        ) { viewmodel.uiState.gifPanelVisible.value = !gifPanelVisible }
        Field(
            value = msg,
            onValueChange = { viewmodel.uiState.msg.value = it },
            // Not the outer modifier: that would re-apply the shield and the insets to the field.
            modifier = Modifier.weight(1f),
            placeholder = stringResource(Res.string.room_chat_input),
            imeAction = if (keyboardSends) ImeAction.Send else ImeAction.Done,
            onImeAction = {
                focusManager.clearFocus()
                if (keyboardSends) send()
            },
            textStyle = Type.note,
            focusRequester = viewmodel.uiState.chatFocus,
            name = stringResource(Res.string.room_chat_input),
        )
        GlyphButton(
            icon = SendGlyph,
            name = stringResource(Res.string.room_send),
            tint = if (hasText && !gifPanelVisible) p.accent else p.inkFaint,
        ) {
            if (gifPanelVisible) return@GlyphButton
            focusManager.clearFocus()
            send()
        }
    }
}

/** The message list, opening at its last line and animating only on growth. */
@Composable
fun ChatBox(viewmodel: RoomViewmodel, modifier: Modifier = Modifier, isHUDVisible: Boolean) {
    val hasVideo by viewmodel.hasVideo.collectAsState()
    val allMessages by viewmodel.session.messageSequence.collectAsState()
    val muted = viewmodel.uiState.mutedUsers
    // A muted peer's lines are dropped from the list rather than blanked, so grouping and the
    // time gutter read as if they were never sent.
    val messages = remember(allMessages, muted.size) { allMessages.filter { it.sender == null || it.sender !in muted } }
    // Resolved here: this is where the palette in force is known (dark over video).
    val chatPalette = LocalChatPalette.current.resolve(palette)

    val bgOpacity by MSG_BG_OPACITY.watchPref()
    val outlineThickness by MSG_OUTLINE_THICKNESS.watchPref()
    val shadowOn by MSG_SHADOW_ACTIVATE.watchPref()
    val fontSize by MSG_FONTSIZE.watchPref()
    val style = MessageStyle(fontSize, outlineThickness.toFloat().takeIf { it > 0f }, shadowOn, chatPalette.includeTimestamp)

    Box(modifier.background(if (hasVideo) Color(50, 50, 50, bgOpacity) else Color.Transparent, Radius.panelShape)) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, messages.size - 1))
        var previousSize by remember { mutableStateOf(messages.size) }
        LaunchedEffect(messages.size) {
            if (messages.size > previousSize) listState.animateScrollToItem(messages.lastIndex)
            previousSize = messages.size
        }

        Selectable {
        LazyColumn(state = listState, contentPadding = PaddingValues(Space.gapTight), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = messages,
                key = { _, message -> message.id },
                // Three shapes share this list. Naming them lets Compose reuse the right one.
                contentType = { _, message ->
                    when {
                        message.isImageUrl -> "image"
                        message.sender == null -> "system"
                        else -> "chat"
                    }
                },
            ) { index, message ->
                /* Seen only while the HUD shows: it stays composed at alpha 0 when hidden, and
                 * marking then would defeat the fading layout. */
                if (isHUDVisible) SideEffect { message.seen = true }
                MessageRow(
                    message = message,
                    previous = messages.getOrNull(index - 1),
                    chatPalette = chatPalette,
                    style = style,
                    imageAlpha = if (isHUDVisible) 1f else 0f,
                    announce = index == messages.lastIndex,
                )
            }
        }
        }
    }
}

/** Chat text can be selected and copied on desktop; touch keeps its long press for the rows. */
@Composable
private fun Selectable(content: @Composable () -> Unit) {
    if (platform == Platform.Desktop) SelectionContainer(content = content) else content()
}
