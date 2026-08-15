package app.room.models

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import app.utils.generateClockstamp

/** Unicode BiDi "First Strong Isolate" (U+2068): text until the matching PDI resolves its
 *  direction on its own without reordering surrounding text. */
private const val BIDI_ISOLATE_START = "\u2068"

/** Unicode BiDi "Pop Directional Isolate" (U+2069): closes a [BIDI_ISOLATE_START] run. */
private const val BIDI_ISOLATE_END = "\u2069"

/** A single chat or system message and the data needed to render it. */
data class Message(
    /** The sender of the message. Null when it's not a chat message */
    var sender: String? = null,

    /** The timestamp at which the message is sent/declared */
    var timestamp: String = generateClockstamp(),

    /** Content of the message */
    var content: String = "",

    /** If the message refers to a chat/action by the app user themself */
    var isMainUser: Boolean = false,

    /** Whether the message is an error, rendered in the error color (red by default). */
    var isError: Boolean = false
) {

    /** Whether this message has been seen. */
    var seen = false

    /** Whether the message content is a GIF/image URL (for inline rendering in chat).
     *  CDN URLs commonly have query strings (`?token=...`) or fragments (`#frame=...`) that
     *  would defeat a naive `endsWith` check on the raw content — strip them before testing. */
    val isImageUrl: Boolean
        get() {
            if (sender == null || !content.startsWith("http")) return false
            val path = content.substringBefore('?').substringBefore('#')
            return path.endsWith(".gif", ignoreCase = true) ||
                path.endsWith(".webp", ignoreCase = true) ||
                path.endsWith(".png", ignoreCase = true) ||
                path.endsWith(".jpg", ignoreCase = true) ||
                path.endsWith(".jpeg", ignoreCase = true)
        }

    /** Returns an AnnotatedString with only the timestamp + sender tag (no content).
     *  Used when the message content is rendered as an inline image instead of text. */
    fun factorizeSenderTag(msgPalette: MessagePalette): AnnotatedString {
        val builder = AnnotatedString.Builder()
        builder.append(
            AnnotatedString(
                text = if (msgPalette.includeTimestamp) "[$timestamp] " else "- ",
                spanStyle = SpanStyle(msgPalette.timestampColor)
            )
        )
        if (sender != null) {
            builder.append(
                AnnotatedString(
                    text = "$BIDI_ISOLATE_START$sender$BIDI_ISOLATE_END:",
                    spanStyle = SpanStyle(
                        color = if (isMainUser) msgPalette.selftagColor else msgPalette.friendtagColor,
                        fontWeight = FontWeight.Companion.SemiBold
                    )
                )
            )
        }
        return builder.toAnnotatedString()
    }

    /** Returns an AnnotatedString to use with Compose Text
     * @param msgPalette A [MessagePalette] that contains colors and properties
     **/
    fun factorize(msgPalette: MessagePalette): AnnotatedString {
        val builder = AnnotatedString.Builder()

        val timestampAS = AnnotatedString(
            text = if (msgPalette.includeTimestamp) "[$timestamp] " else "- ",
            spanStyle = SpanStyle(msgPalette.timestampColor)
        )

        // Chat messages get a "sender: " tag; system/error messages are content only.
        // Sender and content are each wrapped in Unicode first-strong-isolate marks so an RTL
        // message (or RTL username) reorders only within itself. Without the isolates, the BiDi
        // algorithm merges "[time] name: content" into one run and scrambles the visual order.
        val contentAS = if (sender != null) {
            val minibuilder = AnnotatedString.Builder()

            val tag = AnnotatedString(
                text = "$BIDI_ISOLATE_START$sender$BIDI_ISOLATE_END: ",
                spanStyle = SpanStyle(
                    color = if (isMainUser) msgPalette.selftagColor else msgPalette.friendtagColor,
                    fontWeight = FontWeight.Companion.SemiBold
                )
            )
            minibuilder.append(tag)

            val chatTEXT = AnnotatedString(
                text = "$BIDI_ISOLATE_START$content$BIDI_ISOLATE_END",
                spanStyle = SpanStyle(
                    color = msgPalette.usermsgColor,
                    fontWeight = FontWeight.Companion.Medium
                )
            )
            minibuilder.append(chatTEXT)

            minibuilder.toAnnotatedString()
        } else {
            AnnotatedString(
                text = content,
                spanStyle = if (isError) SpanStyle(msgPalette.errormsgColor) else
                    SpanStyle(msgPalette.systemmsgColor)
            )
        }

        builder.append(timestampAS)
        builder.append(contentAS)

        return builder.toAnnotatedString()
    }
}