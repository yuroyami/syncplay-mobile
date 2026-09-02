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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.room.models.BIDI_ISOLATE_END
import app.room.models.BIDI_ISOLATE_START
import app.room.models.Message
import app.room.models.MessagePalette
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.AnimatedImage

/** How chat text is drawn: the size preference (floored at 11), the outline and shadow switches. */
class MessageStyle(fontSize: Int, val outline: Float?, val shadow: Boolean, val showTime: Boolean) {
    val fontSize = fontSize.coerceAtLeast(11)
}

private const val GROUP_WINDOW_MS = 60_000L

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
    chatPalette: MessagePalette,
    style: MessageStyle,
    modifier: Modifier = Modifier,
    imageAlpha: Float = 1f,
) {
    val p = palette
    val sinceMs = if (previous == null) Long.MAX_VALUE else message.epochMs - previous.epochMs
    val grouped = message.sender != null && previous?.sender == message.sender && sinceMs < GROUP_WINDOW_MS
    val showTime = style.showTime && sinceMs > GROUP_WINDOW_MS
    val body = Type.note.copy(fontSize = style.fontSize.sp, lineHeight = (style.fontSize + 6).sp)
    val name = Type.value.copy(fontSize = (style.fontSize - 1).coerceAtLeast(11).sp)
    val spoken = listOfNotNull(message.sender, message.timestamp, message.content).joinToString(", ")

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) { contentDescription = spoken },
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
                    /* Alpha is a parameter: the iOS UIImageView ignores Compose alpha modifiers. */
                    AnimatedImage(
                        url = message.content,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = imageAlpha,
                        modifier = Modifier.padding(top = 2.dp).size(96.dp).clip(Radius.controlShape),
                    )
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
                text = message.timestamp.take(5),
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
