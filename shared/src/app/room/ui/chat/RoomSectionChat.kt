package app.room.ui.chat

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.klipy.KlipyMediaType
import app.preferences.Preferences.GIF_PANEL_TYPE
import app.theme.BrandSpan
import app.theme.brandBrush
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.SearchGlyph
import app.uicomponents.controls.WELL_FILL
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import app.uicomponents.controls.touchTarget
import androidx.compose.ui.platform.LocalDensity

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.room.holdsHudWhileHovered
import app.LocalChatPalette
import app.LocalRoomViewmodel
import app.i18n.Localization
import app.i18n.strings
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import app.uicomponents.controls.Icon
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.Text

/**
 * The chat dock (the area of the room screen that holds the chat): the composer (the chat input
 * row) on top, then the message list or the GIF drawer.
 */
@Composable
fun RoomChatSection(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val density = LocalDensity.current
    val isChatSupported by viewmodel.protocol.supportsChat.collectAsState()
    val gifPanelVisible by viewmodel.uiState.gifPanelVisible.collectAsState()
    val msg by viewmodel.uiState.msg.collectAsState()
    val isHUDVisible by viewmodel.uiState.visibleHUD.collectAsState()

    if (isChatSupported) {
        /* The cutout inset and side margins go on each child, not on the column. So the strip
         * beside a camera notch belongs to the row in front of it, not to the HUD's tap-to-hide
         * handler underneath. */
        val cutoutInsets = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)

        Column(modifier = modifier) {
            ChatComposer(
                viewmodel = viewmodel,
                modifier = Modifier
                    .fillMaxWidth()
                    .holdsHudWhileHovered(viewmodel.uiState)
                    // Tap shield first, then the insets, so a missed tap around the input does
                    // nothing.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .windowInsetsPadding(cutoutInsets)
                    .padding(horizontal = 8.dp),
                gifPanelVisible = gifPanelVisible,
                isHUDVisible = isHUDVisible,
            )

            if (gifPanelVisible) {
                GifPanel(
                    query = msg,
                    onGifSelected = viewmodel::sendChatMedia,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        // Tap shield first, as on the composer, so a missed tap on the margin
                        // around the drawer does not hide the HUD.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .windowInsetsPadding(cutoutInsets).padding(horizontal = 8.dp)
                        .onSizeChanged { size ->
                            viewmodel.uiState.chatMediaSizeDp.value = chatMediaCellSize(with(density) { size.width.toDp() }).value
                        },
                    isHUDVisible = isHUDVisible,
                )
            } else {
                ChatBox(
                    viewmodel = viewmodel,
                    modifier = Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(cutoutInsets).padding(horizontal = 8.dp)
                        .onSizeChanged { size ->
                            viewmodel.uiState.chatMediaSizeDp.value = chatMediaCellSize(with(density) { size.width.toDp() }).value
                        },
                    isHUDVisible = isHUDVisible,
                )
            }
        }
    }
}

/**
 * The chat in the main box of the portrait room (the area under the video). The message list stays
 * composed under a page, so its scroll position survives. A page is a panel that the main box
 * shows in place of chat. [showing] is false while a page covers the list, and its lines count as
 * seen only while it shows. Each step of [newestRequests] scrolls the list to the newest line.
 */
@Composable
fun RoomChatPane(showing: Boolean, newestRequests: Int, modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val density = LocalDensity.current
    val measured = modifier.padding(horizontal = 8.dp).onSizeChanged { size ->
        viewmodel.uiState.chatMediaSizeDp.value = chatMediaCellSize(with(density) { size.width.toDp() }).value
    }
    ChatBox(viewmodel = viewmodel, modifier = measured, isHUDVisible = showing, onGround = true, newestRequests = newestRequests)
}

/** The GIF drawer as a page of the portrait room, with a close key. The composer gives it its search text. */
@Composable
fun RoomGifPage(modifier: Modifier, shape: Shape) {
    val viewmodel = LocalRoomViewmodel.current
    val msg by viewmodel.uiState.msg.collectAsState()
    GifPanel(
        query = msg,
        onGifSelected = viewmodel::sendChatMedia,
        modifier = modifier,
        shape = shape,
        onClose = { viewmodel.uiState.toggleGifPanel(false) },
    )
}

/**
 * The "New messages" marker at the bottom of the main box of the portrait room, while a page
 * covers chat and a line from someone else arrived after the page opened. Own lines and lines of a
 * muted person do not count.
 */
@Composable
fun BoxScope.PageNewMessages(pageOpen: Boolean, onClick: () -> Unit) {
    val viewmodel = LocalRoomViewmodel.current
    val messages by viewmodel.session.messageSequence.collectAsState()
    val muted = viewmodel.uiState.mutedUsers
    // The newest line when the page opened. Only the lines after it count.
    val since = remember(pageOpen) { messages.lastOrNull()?.id ?: 0L }
    if (!pageOpen) return
    val unseen = messages.count { line ->
        line.id > since && line.sender != null && !line.isMainUser && !line.seen && line.sender !in muted
    }
    if (unseen > 0) NewMessagesMarker(Modifier.align(Alignment.BottomCenter).padding(bottom = Space.gap), onClick)
}

/**
 * The composer of the portrait room, at the bottom of the screen. It stays on screen while the
 * room controls hide, so it keeps its focus and the keyboard. A tap on its field brings chat back
 * in place of a page, and keeps the GIF drawer, whose search the field then holds.
 */
@Composable
fun RoomChatInput(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val isChatSupported by viewmodel.protocol.supportsChat.collectAsState()
    val gifPanelVisible by viewmodel.uiState.gifPanelVisible.collectAsState()
    if (!isChatSupported) return
    ChatComposer(
        viewmodel = viewmodel,
        modifier = modifier,
        gifPanelVisible = gifPanelVisible,
        isHUDVisible = true,
        bar = true,
    )
}

/** Sends a GIF or a sticker from the drawer, then empties the search and closes the drawer. */
private fun RoomViewmodel.sendChatMedia(url: String) {
    dispatcher.sendMessage(url)
    uiState.msg.value = ""
    uiState.gifPanelVisible.value = false
}

/**
 * The composer: a GIF button, the hairline text field, and a send button that turns to the accent
 * color once there is text. The keyboard's send action follows its setting, and it is off while
 * the GIF drawer is open, because the text is then a search query. A blank send changes nothing.
 * With [bar], it is the composer of the portrait room: see [ComposerBar].
 */
@Composable
fun ChatComposer(
    viewmodel: RoomViewmodel,
    modifier: Modifier = Modifier,
    gifPanelVisible: Boolean,
    isHUDVisible: Boolean,
    bar: Boolean = false,
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
        /* The cap is the server's own limit, with a floor of 1, so a hostile zero cannot block
         * every message. Nothing is stripped: the JSON layer escapes backslashes itself. */
        val maxLen = viewmodel.session.roomFeatures.maxChatMessageLength.coerceAtLeast(1)
        val text = msg.trimEnd()
        if (text.isBlank()) return
        if (text.length > maxLen) {
            // The draft stays in the field, because a silent cut would lose the end of the message.
            val length = text.length
            viewmodel.dispatchOSD(OSDCategory.WARNING) { Localization.strings.roomChatTooLong(length, maxLen) }
            return
        }
        /* A slash command runs here and never reaches the room, so no one else sees a typo.
         * Anything else is an ordinary message. */
        val command = parseSlashCommand(text)
        if (command == SlashCommand.NotACommand) {
            viewmodel.dispatcher.sendMessage(text)
        } else {
            chatScope.launch { viewmodel.runSlashCommand(command) }
        }
        viewmodel.uiState.msg.value = ""
        viewmodel.uiState.gifPanelVisible.value = false
    }

    if (bar) {
        ComposerBar(viewmodel, modifier, gifPanelVisible, keyboardSends, onSend = ::send)
        return
    }

    Row(modifier.heightIn(min = Space.row), verticalAlignment = Alignment.CenterVertically) {
        GlyphButton(
            icon = Icons.Outlined.GifBox,
            name = strings.roomGifOpen,
            tint = if (gifPanelVisible) p.accent else p.inkDim,
        ) { viewmodel.uiState.toggleGifPanel() }
        Field(
            value = msg,
            onValueChange = { viewmodel.uiState.msg.value = it },
            // Not the outer modifier: that would apply the shield and the insets to the field again.
            modifier = Modifier.weight(1f),
            placeholder = strings.roomChatInput,
            imeAction = if (keyboardSends) ImeAction.Send else ImeAction.Done,
            onImeAction = {
                focusManager.clearFocus()
                if (keyboardSends) send()
            },
            textStyle = Type.note,
            focusRequester = viewmodel.uiState.chatFocus,
            name = strings.roomChatInput,
        )
        GlyphButton(
            icon = SendGlyph,
            name = strings.roomSend,
            tint = if (hasText && !gifPanelVisible) p.accent else p.inkFaint,
        ) {
            if (gifPanelVisible) return@GlyphButton
            focusManager.clearFocus()
            send()
        }
    }
}

/**
 * The composer of the portrait room: a 54dp bar on the panel colour with a hairline on its top
 * edge. It holds the GIF key, the field drawn as a well, and a send key that fills with the brand
 * ramp once there is text. While the GIF drawer is open, the field is its search.
 */
@Composable
private fun ComposerBar(
    viewmodel: RoomViewmodel,
    modifier: Modifier,
    gifPanelVisible: Boolean,
    keyboardSends: Boolean,
    onSend: () -> Unit,
) {
    val p = palette
    val ui = viewmodel.uiState
    val focusManager = LocalFocusManager.current
    val msg by ui.msg.collectAsState()
    val gifType by GIF_PANEL_TYPE.watchPref()
    val canSend = msg.isNotBlank() && !gifPanelVisible
    val keyboardInput = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    Row(
        modifier
            .fillMaxWidth()
            .height(Space.bar)
            .background(p.panel)
            .drawBehind { drawRect(p.rule, Offset.Zero, Size(size.width, Space.hair.toPx())) }
            .padding(horizontal = Space.gapTight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            icon = Icons.Outlined.GifBox,
            name = strings.roomGifOpen,
            tint = if (gifPanelVisible) p.accent else p.inkDim,
        ) { ui.toggleGifPanel() }
        val search = if (gifType == KlipyMediaType.STICKER.name) strings.roomGifSearchStickers else strings.roomGifSearchGifs
        Field(
            value = msg,
            onValueChange = { ui.msg.value = it },
            modifier = Modifier
                .weight(1f)
                // A press on the field brings chat back in place of a page. It only watches, so the
                // field still gets the press.
                .pointerInput(ui) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        ui.showChat(keepGifs = true)
                    }
                }
                .onFocusChanged { if (it.isFocused && keyboardInput) ui.showChat(keepGifs = true) },
            placeholder = if (gifPanelVisible) search else strings.roomChatInput,
            leading = if (gifPanelVisible) SearchGlyph else null,
            imeAction = if (keyboardSends) ImeAction.Send else ImeAction.Done,
            onImeAction = {
                focusManager.clearFocus()
                if (keyboardSends) onSend()
            },
            textStyle = Type.note,
            focusRequester = ui.chatFocus,
            name = strings.roomChatInput,
            well = true,
        )
        RowGap(Space.gapTight)
        SendKey(filled = canSend) {
            if (gifPanelVisible) return@SendKey
            focusManager.clearFocus()
            onSend()
        }
    }
}

/** The send key of the portrait composer: a 40dp square, faint while empty, on the brand ramp once there is text. */
@Composable
private fun SendKey(filled: Boolean, onClick: () -> Unit) {
    val p = palette
    val name = strings.roomSend
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            // The press area is 48dp, around the 40dp key.
            .clickable(interactionSource = source, indication = null, role = Role.Button) { Feedback.tick(); onClick() }
            .hoverable(source)
            .semantics { contentDescription = name }
            .touchTarget()
            .size(Space.well)
            .clip(Radius.panelShape)
            .drawBehind {
                if (filled) drawRect(brandBrush(p.brandField, size.width, BrandSpan.toPx()))
                else drawRect(p.ink.copy(alpha = WELL_FILL))
            }
            .controlStates(source, Radius.panelShape)
            .pressFeedback(source),
        contentAlignment = Alignment.Center,
    ) {
        Icon(SendGlyph, contentDescription = null, tint = if (filled) p.brandInk else p.inkFaint, modifier = Modifier.size(Space.glyph))
    }
}

/**
 * The message list. It opens at its last line and animates only when it grows. [onGround] is chat
 * on the dark ground of the portrait room, not over video: its text keeps at least the size of a
 * notice, with no outline, shadow or background. Each step of [newestRequests] scrolls to the newest
 * line.
 */
@Composable
fun ChatBox(viewmodel: RoomViewmodel, modifier: Modifier = Modifier, isHUDVisible: Boolean, onGround: Boolean = false, newestRequests: Int = 0) {
    val hasVideo by viewmodel.hasVideo.collectAsState()
    val allMessages by viewmodel.session.messageSequence.collectAsState()
    val muted = viewmodel.uiState.mutedUsers
    // A muted peer's lines are dropped from the list rather than blanked, so grouping and the
    // time gutter read as if they were never sent.
    val messages = remember(allMessages, muted.size) { allMessages.filter { it.sender == null || it.sender !in muted } }
    val chatPalette = LocalChatPalette.current

    val bgOpacity by MSG_BG_OPACITY.watchPref()
    val outlineThickness by MSG_OUTLINE_THICKNESS.watchPref()
    val shadowOn by MSG_SHADOW_ACTIVATE.watchPref()
    val fontSize by MSG_FONTSIZE.watchPref()
    val noticeSize = Type.note.fontSize.value.toInt()
    val style = if (onGround) {
        MessageStyle(maxOf(fontSize, noticeSize), outline = null, shadow = false, showTime = chatPalette.includeTimestamp)
    } else {
        MessageStyle(fontSize, outlineThickness.toFloat().takeIf { it > 0f }, shadowOn, chatPalette.includeTimestamp)
    }
    val backdrop = if (hasVideo && !onGround) Color(50, 50, 50, bgOpacity) else Color.Transparent

    Box(modifier.background(backdrop, Radius.panelShape)) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, messages.size - 1))
        val scope = rememberCoroutineScope()
        /* The list follows the newest line until the reader scrolls it out of view. Only a scroll
         * by the reader decides that: the list's own animation to a new message does not count. */
        var following by remember { mutableStateOf(true) }
        var unseen by remember { mutableIntStateOf(0) }
        var ownScrolls by remember { mutableIntStateOf(0) }
        suspend fun toNewest() {
            if (messages.isEmpty()) return
            ownScrolls++
            try {
                // While the HUD is hidden, a new line shows at its place in the list, so the list
                // jumps there at once. An animation would slide the shown line into place.
                if (isHUDVisible) listState.animateScrollToItem(messages.lastIndex) else listState.scrollToItem(messages.lastIndex)
            } finally {
                ownScrolls--
            }
        }
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                if (scrolling || ownScrolls > 0) return@collect
                following = listState.showsLastItem()
                if (following) unseen = 0
            }
        }
        LaunchedEffect(messages.lastOrNull()?.id) {
            if (messages.isEmpty()) return@LaunchedEffect
            // While the HUD is hidden, nobody reads back through the list, and a new line must have
            // a place in view to show at. So the list follows the newest line again.
            if (!isHUDVisible) following = true
            if (following) toNewest() else unseen++
        }
        // A request from outside the list, such as the marker over a page of the portrait room.
        LaunchedEffect(newestRequests) {
            if (newestRequests == 0) return@LaunchedEffect
            following = true
            unseen = 0
            toNewest()
        }

        // Over video, the list tells the room where each line is, for the lines shown while the HUD is hidden.
        val places = if (onGround) null else LocalChatLinePlaces.current
        if (places != null) {
            DisposableEffect(places) {
                places.lists++
                onDispose {
                    places.lists--
                    if (places.lists == 0) places.viewport = null
                }
            }
        }
        Selectable {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(Space.gapTight),
            modifier = Modifier.fillMaxSize().then(
                if (places != null) Modifier.onGloballyPositioned { places.viewport = it.boundsInRoot() } else Modifier,
            ),
        ) {
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
                /* Marked seen only while the HUD shows. The list stays composed at alpha 0 while
                 * hidden, and marking lines then would defeat the fading layout. */
                if (isHUDVisible) SideEffect { message.seen = true }
                if (places != null) {
                    DisposableEffect(places, message.id) { onDispose { places.lines.remove(message.id) } }
                }
                MessageRow(
                    message = message,
                    previous = messages.getOrNull(index - 1),
                    chatPalette = chatPalette,
                    style = style,
                    modifier = if (places != null) Modifier.reportsChatLine(places, message.id) else Modifier,
                    imageAlpha = if (isHUDVisible) 1f else 0f,
                    announce = index == messages.lastIndex,
                )
            }
        }
        }

        if (unseen > 0 && !following) {
            NewMessagesMarker(Modifier.align(Alignment.BottomCenter).padding(bottom = Space.gapTight)) {
                following = true
                unseen = 0
                scope.launch { toNewest() }
            }
        }
    }
}

/** Chat text can be selected and copied on desktop. On touch, the long press stays with the rows. */
@Composable
private fun Selectable(content: @Composable () -> Unit) {
    if (platform == Platform.Desktop) SelectionContainer(content = content) else content()
}

/** Whether the newest line is at least partly on screen. True when there is nothing to show. */
private fun LazyListState.showsLastItem(): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return true
    return last.index >= info.totalItemsCount - 1
}

/** The way back down to the newest line, shown only once something new arrived below. */
@Composable
internal fun NewMessagesMarker(modifier: Modifier, onClick: () -> Unit) {
    val p = palette
    val ink = p.inkOn(p.accent)
    Row(
        modifier
            .clip(Radius.panelShape)
            .background(p.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Space.rowCompact)
            .padding(horizontal = Space.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = ink, modifier = Modifier.size(Space.glyph))
        RowGap(Space.gapTight)
        Text(strings.roomChatNewMessages, style = Type.note, color = ink, maxLines = 1)
    }
}
