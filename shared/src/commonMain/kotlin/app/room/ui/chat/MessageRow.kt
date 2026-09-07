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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.room.models.BIDI_ISOLATE_END
import app.room.models.BIDI_ISOLATE_START
import app.room.models.Message
import app.room.models.ResolvedMessagePalette
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import app.LocalRoomViewmodel
import app.utils.platformCallback
import app.uicomponents.AnimatedImage

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

/** A chat image reads at a glance without taking the column. Halfway between the old 64 and 96. */
private val CHAT_IMAGE_SIZE = 80.dp

/**
 * One chat line in one of two shapes. A person: the name in the tag colour, the message under it,
 * no bubble; a second message from the same person inside a minute drops the repeated name. An
 * event: a 2dp stub in the gutter and one dim line, red for errors. The time sits in a right hand
 * column only when the switch is on and more than a minute passed since the line above. The
 * spoken description always carries the name and the time.
 */
@Composable
fun MessageRow(
    message: Message,
    previous: Message?,
    chatPalette: ResolvedMessagePalette,
    style: MessageStyle,
    modifier: Modifier = Modifier,
    imageAlpha: Float = 1f,
    announce: Boolean = false,
) {
    val p = palette
    val viewmodel = LocalRoomViewmodel.current
    val copied = strings.roomChatCopied
    val sinceMs = if (previous == null) Long.MAX_VALUE else message.epochMs - previous.epochMs
    val grouped = message.sender != null && previous?.sender == message.sender && sinceMs < GROUP_WINDOW_MS
    val showTime = style.showTime && sinceMs > GROUP_WINDOW_MS
    val body = Type.note.copy(fontSize = style.fontSize.sp, lineHeight = (style.fontSize + 6).sp)
    val name = Type.value.copy(fontSize = (style.fontSize - 1).coerceAtLeast(5).sp)
    val spoken = listOfNotNull(message.sender, message.timestamp, message.content).joinToString(", ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            // A long press copies the line on touch platforms, where no selection container runs.
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = {},
                onLongClick = {
                    platformCallback.copyText(message.content)
                    viewmodel.dispatchOSD { copied }
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
            Row(Modifier.weight(1f).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(if (message.isError) p.bad else p.accent))
                OutlinedText(
                    text = message.content,
                    style = body,
                    color = if (message.isError) chatPalette.errormsgColor else chatPalette.systemmsgColor,
                    outline = style.outline,
                    shadow = style.shadow,
                    modifier = Modifier.padding(start = Space.gapTight + 2.dp),
                )
            }
        } else {
            Column(Modifier.weight(1f)) {
                if (!grouped) {
                    OutlinedText(
                        text = BIDI_ISOLATE_START + message.sender + BIDI_ISOLATE_END,
                        style = name,
                        color = if (message.isMainUser) chatPalette.selftagColor else chatPalette.friendtagColor,
                        outline = style.outline,
                        shadow = style.shadow,
                    )
                }
                if (message.isImageUrl) {
                    val revealed = message.isFromTrustedImageHost || message.content in viewmodel.uiState.revealedImages
                    if (revealed) {
                        /* Alpha is a parameter: the iOS UIImageView ignores Compose alpha modifiers. */
                        AnimatedImage(
                            url = message.content,
                            // A chat image said nothing at all to a screen reader. Now it at
                            // least says who sent it, which is what the eye gets too.
                            contentDescription = strings.roomChatImageFrom(message.sender ?: ""),
                            contentScale = ContentScale.Crop,
                            alpha = imageAlpha,
                            modifier = Modifier.padding(top = 2.dp).size(CHAT_IMAGE_SIZE).clip(Radius.controlShape),
                        )
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
                        text = BIDI_ISOLATE_START + message.content + BIDI_ISOLATE_END,
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

/** Text with the optional black outline and shadow the chat preferences ask for, over video. */
@Composable
private fun OutlinedText(
    text: String,
    style: TextStyle,
    color: Color,
    outline: Float?,
    shadow: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = if (shadow) style.copy(shadow = Shadow(Color.Black, Offset(0f, 1f), blurRadius = 4f)) else style
    Box(modifier) {
        if (outline != null && outline > 0f) {
            Text(text, style = base.copy(color = Color.Black, drawStyle = Stroke(width = outline, join = StrokeJoin.Round)))
        }
        Text(text, style = base.copy(color = color))
    }
}
