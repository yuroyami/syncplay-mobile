package app.room.models

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import app.utils.generateClockstamp

/***************************************************************************************************
 * Message wrapper class. It encapsulates all information and data we need about a single message  *
 ***************************************************************************************************/
data class Message(
    /** The sender of the message. Null when it's not a chat message */
    var sender: String? = null,

    /** The timestamp at which the message is sent/declared */
    var timestamp: String = generateClockstamp(),

    /** Content of the message */
    var content: String = "",

    /** If the message refers to a chat/action by the app user themself */
    var isMainUser: Boolean = false,

    /** Whether the message is an error message (and therefore should be colored in Error color (red in default) */
    var isError: Boolean = true
) {

    /** indicates that this message has been seen */
    var seen = false

    /** Whether the message content is a GIF/image URL (for inline rendering in chat) */
    val isImageUrl: Boolean
        get() = sender != null && content.startsWith("http") && (
            content.endsWith(".gif", ignoreCase = true) ||
            content.endsWith(".webp", ignoreCase = true) ||
            content.endsWith(".png", ignoreCase = true) ||
            content.endsWith(".jpg", ignoreCase = true) ||
            content.endsWith(".jpeg", ignoreCase = true)
        )

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
                    text = "$sender:",
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
        /* An AnnotatedString builder that will append child AnnotatedStings together */
        val builder = AnnotatedString.Builder()

        /* First, an AnnotatedString instance of the timestamp */
        val timestampAS = AnnotatedString(
            text = if (msgPalette.includeTimestamp) "[$timestamp] " else "- ",
            spanStyle = SpanStyle(msgPalette.timestampColor)
        )

        /* Now, the AnnotatedString instance of the message content */
        val contentAS = if (sender != null) {
            val minibuilder = AnnotatedString.Builder()

            val tag = AnnotatedString(
                text = "$sender: ",
                spanStyle = SpanStyle(
                    color = if (isMainUser) msgPalette.selftagColor else msgPalette.friendtagColor,
                    fontWeight = FontWeight.Companion.SemiBold
                )
            )
            minibuilder.append(tag)

            val chatTEXT = AnnotatedString(
                text = content,
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

        /* Finally, we append our little annotatedStrings into our builder */
        builder.append(timestampAS)
        builder.append(contentAS)

        return builder.toAnnotatedString()
    }
}