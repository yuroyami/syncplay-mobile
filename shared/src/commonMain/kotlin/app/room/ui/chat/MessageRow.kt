package app.room.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import app.i18n.strings
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.room.models.BIDI_ISOLATE_END
import app.room.models.BIDI_ISOLATE_START
import app.room.models.Message
import app.room.models.MessagePalette
import androidx.compose.foundation.interaction.MutableInteractionSource
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.controlStates
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import app.LocalRoomViewmodel
import app.utils.platformCallback
import app.uicomponents.AnimatedImage
import app.uicomponents.controls.shimmer

/**
 * How chat text is drawn: the size preference (floored at 5), the outline and shadow switches.
 *
 * A data class so two of these compare by what they say. The chat box builds one in its own body
 * and hands it to every visible row, so with identity equality a new instance arrived on every
 * recomposition and no row in the list could skip, however little had changed.
 */
data class MessageStyle(val fontSizePreference: Int, val outline: Float?, val shadow: Boolean, val showTime: Boolean) {
    val fontSize = fontSizePreference.coerceAtLeast(5)
}

private const val GROUP_WINDOW_MS = 60_000L

/** The GIF panel's flat tint over video, so a GIF still loading in chat looks like one in the panel. */
private const val LOADING_TILE_TINT = 0.65f

/**
 * One chat line in one of two shapes. A person: the name in the tag colour, the message under it,
 * no bubble; a second message from the same person inside a minute drops the repeated name. An
 * event: see [EventLine]. The time sits in a right hand column only when the switch is on and more
 * than a minute passed since the line above. The spoken description always carries the name and
 * the time.
 */
@Composable
fun MessageRow(
    message: Message,
    previous: Message?,
    chatPalette: MessagePalette,
    style: MessageStyle,
    modifier: Modifier = Modifier,
    imageAlpha: Float = 1f,
    announce: Boolean = false,
) {
    val viewmodel = LocalRoomViewmodel.current
    val copied = strings.roomChatCopied
    val sinceMs = if (previous == null) Long.MAX_VALUE else message.epochMs - previous.epochMs
    val grouped = message.sender != null && previous?.sender == message.sender && sinceMs < GROUP_WINDOW_MS
    val showTime = style.showTime && sinceMs > GROUP_WINDOW_MS
    val body = chatBody(style)
    val name = Type.value.copy(fontSize = (style.fontSize - 1).coerceAtLeast(5).sp)
    val spoken = listOfNotNull(message.sender, message.timestamp, message.content).joinToString(", ")
    val showsImage = message.isImageUrl && (message.isFromTrustedImageHost || message.content in viewmodel.uiState.revealedImages)
    var menuOpen by remember { mutableStateOf(false) }
    /* The line takes focus, so a remote and a keyboard need to see which one they are on. The
     * source carries that state to the ring; the press state is still drawn by nothing, so a
     * finger sees the line exactly as before. */
    val source = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .controlStates(source, Radius.controlShape)
            /* A long press copies the line on touch platforms, where no selection container runs.
             * On a GIF or sticker the link is no use as text, so it opens the image's menu. A
             * remote holds its press key to do the same, which Compose reports as a long click. */
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = {},
                onLongClick = {
                    if (showsImage) {
                        menuOpen = true
                    } else {
                        platformCallback.copyText(message.content)
                        viewmodel.dispatchOSD { copied }
                    }
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                // Only the newest line is a live region, so a screen reader hears each new message once.
                if (announce) liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.Top,
    ) {
        if (message.sender == null) {
            EventLine(message, chatPalette, style, Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f)) {
                if (!grouped) {
                    OutlinedText(
                        text = AnnotatedString(BIDI_ISOLATE_START + message.sender + BIDI_ISOLATE_END),
                        style = name,
                        color = if (message.isMainUser) chatPalette.selftagColor else chatPalette.friendtagColor,
                        outline = style.outline,
                        shadow = style.shadow,
                    )
                }
                if (message.isImageUrl) {
                    if (showsImage) {
                        var loading by remember(message.content) { mutableStateOf(true) }
                        Box(Modifier.padding(top = 2.dp).size(LocalChatMediaSize.current).clip(Radius.controlShape)) {
                            /* Alpha is a parameter: the iOS UIImageView ignores Compose alpha modifiers. */
                            AnimatedImage(
                                url = message.content,
                                // A chat image said nothing at all to a screen reader. Now it at
                                // least says who sent it, which is what the eye gets too.
                                contentDescription = strings.roomChatImageFrom(message.sender ?: ""),
                                contentScale = ContentScale.Crop,
                                alpha = imageAlpha,
                                onLoaded = { loading = false },
                                onFailed = { loading = false },
                                modifier = Modifier.matchParentSize(),
                            )
                            /* Over the image, not under it: on iOS the image is a native view that
                             * clears its own area of the Compose canvas. Chat has no panel behind
                             * it, so the tile brings its own tint, or the sweep vanishes over video. */
                            if (loading && imageAlpha > 0f) {
                                Box(Modifier.matchParentSize().background(palette.panel.copy(alpha = LOADING_TILE_TINT)).shimmer())
                            }
                            if (menuOpen) ChatMediaMenu(link = message.content, onDismiss = { menuOpen = false })
                        }
                    } else {
                        /* A peer's link is not fetched on sight: that would hand their chosen host
                         * the address of every device in the room. One tap loads it. */
                        Text(
                            text = strings.roomChatImageHidden(message.imageHost),
                            style = body,
                            color = chatPalette.systemmsgColor,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(Radius.controlShape)
                                .clickable(
                                    onClickLabel = strings.roomChatShowImage(message.imageHost),
                                ) { viewmodel.uiState.revealedImages.add(message.content) }
                                .padding(vertical = 2.dp),
                        )
                    }
                } else {
                    OutlinedText(
                        text = AnnotatedString(BIDI_ISOLATE_START + message.content + BIDI_ISOLATE_END),
                        style = body,
                        color = chatPalette.usermsgColor,
                        outline = style.outline,
                        shadow = style.shadow,
                    )
                }
            }
        }
        if (showTime) {
            Text(
                // Whole, not the first five characters: a 12-hour clock reads "9:05 PM", and
                // cutting at five turned that into "9:05" for every afternoon message.
                text = message.timestamp,
                style = Type.value,
                color = chatPalette.timestampColor,
                maxLines = 1,
                modifier = Modifier.padding(start = Space.gap, top = 2.dp),
            )
        }
    }
}

/**
 * An event: one line with a 2dp stub in the gutter, both in the event colour, red for errors. A
 * neutral event draws each person it names in their chat name colour; an error stays all red.
 */
@Composable
internal fun EventLine(message: Message, chatPalette: MessagePalette, style: MessageStyle, modifier: Modifier = Modifier) {
    val tone = if (message.isError) chatPalette.errormsgColor else chatPalette.systemmsgColor
    val text = remember(message.content, message.people, message.isError, chatPalette) {
        if (message.isError) AnnotatedString(message.content)
        else eventText(message.content, message.people, chatPalette.selftagColor, chatPalette.friendtagColor)
    }
    Row(modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(tone))
        OutlinedText(
            text = text,
            style = chatBody(style),
            color = tone,
            outline = style.outline,
            shadow = style.shadow,
            modifier = Modifier.padding(start = Space.gapTight + 2.dp),
        )
    }
}

/**
 * [content] with each of [people] in [self] or [friend]. A name counts only as a whole isolated
 * run, so a name that is also part of a file name or another word stays uncoloured there.
 */
internal fun eventText(content: String, people: Map<String, Boolean>, self: Color, friend: Color): AnnotatedString {
    if (people.isEmpty()) return AnnotatedString(content)
    return buildAnnotatedString {
        append(content)
        var from = 0
        while (true) {
            val start = content.indexOf(BIDI_ISOLATE_START, from)
            if (start < 0) break
            val end = content.indexOf(BIDI_ISOLATE_END, start + 1)
            if (end < 0) break
            people[content.substring(start + 1, end)]?.let { isSelf ->
                addStyle(SpanStyle(color = if (isSelf) self else friend), start + 1, end)
            }
            from = end + 1
        }
    }
}

/** Chat text at the size the chat preference asks for. */
@Composable
@ReadOnlyComposable
private fun chatBody(style: MessageStyle): TextStyle =
    Type.note.copy(fontSize = style.fontSize.sp, lineHeight = (style.fontSize + 6).sp)

/** Text with the optional black outline and shadow the chat preferences ask for, over video. */
@Composable
private fun OutlinedText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    outline: Float?,
    shadow: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = if (shadow) style.copy(shadow = Shadow(Color.Black, Offset(0f, 1f), blurRadius = 4f)) else style
    Box(modifier) {
        if (outline != null && outline > 0f) {
            // Without the spans: a coloured name would otherwise get a coloured outline.
            Text(AnnotatedString(text.text), style = base.copy(color = Color.Black, drawStyle = Stroke(width = outline, join = StrokeJoin.Round)))
        }
        Text(text, style = base.copy(color = color))
    }
}
