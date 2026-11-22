package app.room.ui.chat

import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import app.preferences.Preferences.MSG_OUTLINE_MAX
import app.room.models.eventColor
import app.room.models.timeColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.room.models.BIDI_ISOLATE_END
import app.room.models.BIDI_ISOLATE_START
import app.room.models.Message
import app.room.models.MessagePalette
import app.room.models.LocalPersonShades
import app.room.models.nameColor
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
 * How chat text is drawn: the size preference (at least 5), the width of the black outline, and
 * the shadow and time switches. Over video ([overVideoStyle]), a line can also have a [plate], and
 * an event line takes the brightness of chat text ([overVideo]).
 *
 * A data class, so two instances compare by value. The chat box builds one in its own body and
 * hands it to every visible row. With identity equality, a new instance would arrive on every
 * recomposition, and no row in the list could skip, however little had changed.
 */
data class MessageStyle(
    val fontSizePreference: Int,
    val outline: Dp?,
    val shadow: Boolean,
    val showTime: Boolean,
    /** The dark shape behind each line, only as wide as its text, or null for none. */
    val plate: Color? = null,
    /** True for chat that sits over the video or the room artwork, not on a ground of its own. */
    val overVideo: Boolean = false,
) {
    val fontSize = fontSizePreference.coerceAtLeast(5)
}

/**
 * One step of the outline setting. The stroke lies half under the text, so the edge that shows is
 * half as wide. The step is in dp, so the outline looks the same on every screen.
 */
internal val OutlineStep = 0.75.dp

/**
 * The style of chat lines over video, from the chat settings: [outlineSteps] of [OutlineStep] (a
 * stored value from the old pixel scale counts as the highest step), the [shadow] switch, and
 * [plateOpacity] (0 to 255) for the black plate behind each line.
 */
internal fun overVideoStyle(fontSize: Int, outlineSteps: Int, shadow: Boolean, plateOpacity: Int, showTime: Boolean): MessageStyle {
    val steps = outlineSteps.coerceIn(0, MSG_OUTLINE_MAX)
    val opacity = plateOpacity.coerceIn(0, 255)
    return MessageStyle(
        fontSizePreference = fontSize,
        outline = if (steps > 0) OutlineStep * steps else null,
        shadow = shadow,
        showTime = showTime,
        plate = if (opacity > 0) Color.Black.copy(alpha = opacity / 255f) else null,
        overVideo = true,
    )
}

private const val GROUP_WINDOW_MS = 60_000L

/** Whether [message] continues the group of [previous]: the same person, within a minute. Such a line drops the repeated name. */
internal fun grouped(message: Message, previous: Message?): Boolean =
    previous != null && message.sender != null && previous.sender == message.sender &&
        message.epochMs - previous.epochMs < GROUP_WINDOW_MS

/**
 * The flat tint of the GIF panel over video, so a GIF that is still loading in chat looks like a
 * loading GIF in the panel.
 */
private const val LOADING_TILE_TINT = 0.65f

/** The width of the dash down the start edge of a group. An event line draws the same dash. */
private val DashWidth = 2.dp

/** From the dash to the text, so the text of a group starts where the text of an event line starts. */
private val DashGap = Space.gapTight + 2.dp

/** The space over a line that starts a group, so two groups never share one dash. */
private val GroupGap = 4.dp

/** The inner margin of a band in the portrait chat, at the top and the bottom of its group. */
private val BandInset = Space.gapTight

/** The inner margin of a band at both sides: the margin that the portrait chat had before its bands. */
private val BandSide = 14.dp

/** The plate behind a line over video: square at the dash, round at the other end. */
private val PlateShape = RoundedCornerShape(topStart = 0.dp, topEnd = 6.dp, bottomEnd = 6.dp, bottomStart = 0.dp)

/** From the text to the end of its plate. */
private val PlateEnd = Space.gap

/** How far a plate reaches over and under its line. Two plates in a row keep a thin gap. */
private val PlateRise = 1.dp

/**
 * The plate of a line: [color] behind the content, as wide as the content and [PlateEnd]. It draws
 * [PlateRise] outside the line at the top and the bottom, so the line keeps its height with and
 * without a plate.
 */
private fun Modifier.plate(color: Color?): Modifier = if (color == null) this else this
    .drawBehind {
        val rise = PlateRise.toPx()
        val outline = PlateShape.createOutline(Size(size.width, size.height + rise * 2), layoutDirection, this)
        translate(top = -rise) { drawOutline(outline, color) }
    }
    .padding(end = PlateEnd)

/** The lighter band of the portrait chat. The groups take turns between it and the room ground. */
internal val ChatBand = Color.White.copy(alpha = 0.04f)

/**
 * The band of each line in the portrait chat. A group of lines (see [grouped]) shares one band, and
 * the groups take turns between the room ground and [ChatBand], so each group stands apart.
 */
internal fun chatBands(messages: List<Message>): List<Color> {
    var lighter = true
    return messages.mapIndexed { index, message ->
        if (!grouped(message, messages.getOrNull(index - 1))) lighter = !lighter
        if (lighter) ChatBand else Color.Transparent
    }
}

/**
 * One chat line, in one of two shapes:
 * - A person's message: the name in the person's color ([nameColor]) and the message under it. A
 *   second message from the same person within a minute drops the repeated name. A GIF or a sticker
 *   shows in place of the text, with no card around it (see [ChatMedia]).
 * - An event: see [EventLine].
 *
 * The lines of one person form a group. A 2dp dash in the person's color runs down the start edge
 * of the whole group, as on an event line, so the group reads as one block. A line that starts a
 * group keeps [GroupGap] above it, so two groups never share one dash.
 *
 * With [band], the line is part of the portrait chat on the room ground. The band fills the full
 * width behind the line, and the whole group shares it (see [chatBands]). The band keeps an inner
 * margin at the top and the bottom of its group, and at both sides. [next] tells the line whether
 * it ends its group.
 *
 * The time sits in a column at the end only when the time switch is on and more than a minute
 * passed since the line above. The spoken description always has the name and the time.
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
    /** Draws the sender's name only. The rest keeps its place, so the name sits where the full line puts it. */
    onlyName: Boolean = false,
    /** The band behind the line in the portrait chat, or null over video. */
    band: Color? = null,
    /** The line under this one. Only a line with a [band] reads it. */
    next: Message? = null,
) {
    val viewmodel = LocalRoomViewmodel.current
    val copied = strings.roomChatCopied
    val sinceMs = if (previous == null) Long.MAX_VALUE else message.epochMs - previous.epochMs
    val grouped = grouped(message, previous)
    val showTime = style.showTime && sinceMs > GROUP_WINDOW_MS
    val body = chatBody(style)
    val name = Type.value.copy(fontSize = (style.fontSize - 1).coerceAtLeast(5).sp)
    val spoken = listOfNotNull(message.sender, message.timestamp, message.content).joinToString(", ")
    val showsImage = message.isImageUrl && (message.isFromTrustedImageHost || message.content in viewmodel.uiState.revealedImages)
    val sender = message.sender
    val shades = LocalPersonShades.current
    val tag = remember(sender, message.isMainUser, chatPalette, shades) { sender?.let { chatPalette.nameColor(it, message.isMainUser, shades) } }
    // A band keeps its margin inside the group. Over video, only a new group gets a gap above it.
    val top = when {
        band != null -> if (grouped) 0.dp else BandInset
        grouped || previous == null -> 0.dp
        else -> GroupGap
    }
    val bottom = if (band != null && (next == null || !grouped(next, message))) BandInset else 0.dp
    val side = if (band != null) BandSide else 0.dp
    var menuOpen by remember { mutableStateOf(false) }
    /* The line takes focus, so a remote or keyboard user must see which line has focus. The
     * source carries that state to the ring. Nothing draws the press state, so a touch shows no
     * change on the line. */
    val source = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (band != null) Modifier.background(band) else Modifier)
            // An event line draws its own dash. A line that shows only its name draws none.
            .then(if (tag != null && !onlyName) Modifier.groupDash(tag, side, top, bottom) else Modifier)
            .padding(start = side, end = side, top = top + 2.dp, bottom = bottom + 2.dp)
            .controlStates(source, Radius.controlShape)
            /* A long press copies the line on touch platforms, where no selection container runs.
             * On a GIF or sticker the link is of no use as text, so the long press opens the
             * image's menu. A remote holds its press key to do the same, which Compose reports as
             * a long click. */
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
        if (sender == null) {
            EventLine(message, chatPalette, style, Modifier.weight(1f))
        } else {
            // The inner column is as wide as its text, so a plate ends where the text ends.
            Box(Modifier.weight(1f)) {
                Column(Modifier.plate(style.plate.takeUnless { onlyName }).padding(start = DashWidth + DashGap)) {
                    if (!grouped) {
                        OutlinedText(
                            text = AnnotatedString(BIDI_ISOLATE_START + sender + BIDI_ISOLATE_END),
                            style = name,
                            color = tag ?: chatPalette.friendtagColor,
                            outline = style.outline,
                            shadow = style.shadow,
                        )
                    }
                    if (message.isImageUrl) {
                        if (showsImage) {
                            ChatMedia(
                                message = message,
                                size = LocalChatMediaSize.current,
                                alpha = imageAlpha,
                                onlyName = onlyName,
                                menuOpen = menuOpen,
                                onMenuDismiss = { menuOpen = false },
                            )
                        } else {
                            /* A peer's image link does not load on its own. Loading it would send the IP
                             * address of every device in the room to a host that the peer chose. One
                             * tap loads it. */
                            OutlinedText(
                                text = AnnotatedString(strings.roomChatImageHidden(message.imageHost)),
                                style = body,
                                color = chatPalette.eventColor(style.overVideo),
                                outline = style.outline,
                                shadow = style.shadow,
                                modifier = Modifier
                                    .then(if (onlyName) Modifier.alpha(0f) else Modifier)
                                    .padding(top = 2.dp)
                                    // No clip here: it would cut the outline and the shadow of the text.
                                    .clickable(
                                        onClickLabel = strings.roomChatShowImage(message.imageHost),
                                    ) { viewmodel.uiState.revealedImages.add(message.content) }
                                    .padding(vertical = 2.dp),
                            )
                        }
                    } else {
                        OutlinedText(
                            modifier = if (onlyName) Modifier.alpha(0f) else Modifier,
                            text = AnnotatedString(BIDI_ISOLATE_START + message.content + BIDI_ISOLATE_END),
                            style = body,
                            color = chatPalette.usermsgColor,
                            outline = style.outline,
                            shadow = style.shadow,
                        )
                    }
                }
            }
        }
        if (showTime) {
            OutlinedText(
                modifier = Modifier.padding(start = Space.gap, top = 2.dp).then(if (onlyName) Modifier.alpha(0f) else Modifier),
                // The whole string, not the first five characters: a 12-hour clock reads "9:05 PM",
                // and a cut at five would show "9:05" for every afternoon message.
                text = AnnotatedString(message.timestamp),
                style = Type.value,
                color = chatPalette.timeColor(style.overVideo),
                outline = style.outline,
                shadow = style.shadow,
                maxLines = 1,
            )
        }
    }
}

/**
 * The dash of a group, [side] in from the start edge of the line. It runs the full height of the
 * line but [top] and [bottom], so the lines of one group join into one dash.
 */
private fun Modifier.groupDash(color: Color, side: Dp, top: Dp, bottom: Dp): Modifier = drawWithContent {
    // Over the content, so a plate never dims the dash.
    drawContent()
    val width = DashWidth.toPx()
    val x = if (layoutDirection == LayoutDirection.Ltr) side.toPx() else size.width - side.toPx() - width
    val y = top.toPx()
    drawRect(color, topLeft = Offset(x, y), size = Size(width, size.height - y - bottom.toPx()))
}

/**
 * A shown GIF or sticker, [size] square, with no card around it. The name above it and the dash
 * beside it keep it in its group. A shimmering tile holds its place while it loads. With
 * [onlyName], it keeps its place but loads nothing.
 */
@Composable
private fun ChatMedia(message: Message, size: Dp, alpha: Float, onlyName: Boolean, menuOpen: Boolean, onMenuDismiss: () -> Unit) {
    var loading by remember(message.content) { mutableStateOf(true) }
    Box(Modifier.padding(top = 2.dp).size(size).clip(Radius.controlShape)) {
        if (!onlyName) {
            /* Alpha is a parameter: the iOS UIImageView ignores Compose alpha modifiers. */
            AnimatedImage(
                url = message.content,
                // A screen reader says who sent the image, which is what a sighted user sees too.
                contentDescription = strings.roomChatImageFrom(message.sender ?: ""),
                contentScale = ContentScale.Crop,
                alpha = alpha,
                onLoaded = { loading = false },
                onFailed = { loading = false },
                modifier = Modifier.matchParentSize(),
            )
            /* Over the image, not under it: on iOS the image is a native view that clears its own
             * area of the Compose canvas. Chat has no panel behind it, so the tile brings its own
             * tint, or the shimmer would vanish over video. */
            if (loading && alpha > 0f) {
                Box(Modifier.matchParentSize().background(palette.panel.copy(alpha = LOADING_TILE_TINT)).shimmer())
            }
        }
        if (menuOpen) ChatMediaMenu(link = message.content, onDismiss = onMenuDismiss)
    }
}

/**
 * An event line: one line with a 2dp stub in the gutter, both in the event color (red for errors).
 * A neutral event draws each person it names in that person's name color ([nameColor]). An error
 * stays all red. Over video, a neutral event is as light as chat text ([eventColor]).
 */
@Composable
internal fun EventLine(message: Message, chatPalette: MessagePalette, style: MessageStyle, modifier: Modifier = Modifier) {
    val tone = if (message.isError) chatPalette.errormsgColor else chatPalette.eventColor(style.overVideo)
    val shades = LocalPersonShades.current
    val text = remember(message.content, message.people, message.isError, chatPalette, shades) {
        if (message.isError) AnnotatedString(message.content)
        else eventText(message.content, message.people) { person, self -> chatPalette.nameColor(person, self, shades) }
    }
    // The inner row is as wide as its text, so a plate ends where the text ends.
    Box(modifier) {
        Row(Modifier.height(IntrinsicSize.Min).plate(style.plate), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(DashWidth).fillMaxHeight().background(tone))
            OutlinedText(
                text = text,
                style = chatBody(style),
                color = tone,
                outline = style.outline,
                shadow = style.shadow,
                modifier = Modifier.padding(start = DashGap),
            )
        }
    }
}

/**
 * Colors each of [people] in [content] with the [color] of that person. The value of each entry in
 * [people] says whether the person is the local user. A name counts only as a whole isolated run,
 * so a name that is also part of a file name or another word stays uncolored there.
 */
internal fun eventText(content: String, people: Map<String, Boolean>, color: (name: String, self: Boolean) -> Color): AnnotatedString {
    if (people.isEmpty()) return AnnotatedString(content)
    return buildAnnotatedString {
        append(content)
        var from = 0
        while (true) {
            val start = content.indexOf(BIDI_ISOLATE_START, from)
            if (start < 0) break
            val end = content.indexOf(BIDI_ISOLATE_END, start + 1)
            if (end < 0) break
            val person = content.substring(start + 1, end)
            people[person]?.let { isSelf -> addStyle(SpanStyle(color = color(person, isSelf)), start + 1, end) }
            from = end + 1
        }
    }
}

/** Chat text at the size the chat preference asks for. */
@Composable
@ReadOnlyComposable
private fun chatBody(style: MessageStyle): TextStyle =
    Type.note.copy(fontSize = style.fontSize.sp, lineHeight = (style.fontSize + 6).sp)

/** The shadow of chat text. Its size is in dp, so it looks the same on every screen. */
private val ShadowBlur = 1.5.dp
private val ShadowDrop = 0.4.dp

/** The alpha of the second shadow pass. It makes the shadow a little denser at the same size. */
private const val SHADOW_SECOND_PASS = 0.4f

/**
 * Text with the black outline and the shadow that the chat preferences ask for, over video. The
 * layers under the text are copies of it in black: a faint one that only thickens the shadow, and
 * the outline. Only the top layer takes part in a selection, so a copy holds the text once.
 */
@Composable
private fun OutlinedText(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    outline: Dp?,
    shadow: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val density = LocalDensity.current
    val cast = if (shadow) with(density) { Shadow(Color.Black, Offset(0f, ShadowDrop.toPx()), ShadowBlur.toPx()) } else null
    val base = if (cast != null) style.copy(shadow = cast) else style
    val stroke = if (outline != null && outline > 0.dp) with(density) { outline.toPx() } else 0f
    // One line ends in an ellipsis. Every layer cuts the text at the same place.
    val overflow = if (maxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip
    Box(modifier) {
        if (cast != null || stroke > 0f) {
            // Without the spans: a colored name would otherwise get a colored outline.
            val plain = AnnotatedString(text.text)
            DisableSelection {
                if (cast != null) {
                    val faint = Color.Black.copy(alpha = SHADOW_SECOND_PASS)
                    Text(plain, style = style.copy(color = faint, shadow = cast.copy(color = faint)), maxLines = maxLines, overflow = overflow)
                }
                if (stroke > 0f) {
                    Text(
                        plain,
                        style = base.copy(color = Color.Black, drawStyle = Stroke(width = stroke, join = StrokeJoin.Round)),
                        maxLines = maxLines,
                        overflow = overflow,
                    )
                }
            }
        }
        Text(text, style = base.copy(color = color), maxLines = maxLines, overflow = overflow)
    }
}
