package app.wrappers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import app.utils.MiscUtils.generateTimestamp

/***************************************************************************************************
 * Message wrapper class. It encapsulates all information and data we need about a single message  *
 ***************************************************************************************************/
class Message {

    /** The sender of the message. Null when it's not a chat message */
    var sender: String? = null

    /** The timestamp at which the message is sent/declared */
    var timestamp: String = generateTimestamp()

    /** Content of the message */
    var content: String = ""

    /** If the message refers to a chat/action by the app user themself */
    var isMainUser: Boolean = false

    /** Returns an AnnotatedString to use with Compose Text
     * @param includeTimestamp Whether the timestamp should be included, true by default
     * @param context Context to fetch color preferences from
     * @param isError Whether the system refers to an error, to display it in the corresponding error color */
    fun factorize(
        timestampColor: Color,
        selftagColor: Color,
        friendtagColor: Color,
        systemmsgColor: Color,
        usermsgColor: Color,
        errormsgColor: Color,
        includeTimestamp: Boolean = true,
        isError: Boolean = false,
    ): AnnotatedString {

        /* An AnnotatedString builder that will append child AnnotatedStings together */
        val builder = AnnotatedString.Builder()

        /* First, an AnnotatedString instance of the timestamp */
        val timestampAS = AnnotatedString(
            text = if (includeTimestamp) "[$timestamp] " else "- ",
            spanStyle = SpanStyle(timestampColor)
        )

        /* Now, the AnnotatedString instance of the message content */
        val contentAS = if (sender != null) {
            val minibuilder = AnnotatedString.Builder()

            val tag = AnnotatedString(
                text = ("$sender: "),
                spanStyle = SpanStyle(
                    color = if (isMainUser) selftagColor else friendtagColor,
                    fontWeight = FontWeight.SemiBold
                )
            )
            minibuilder.append(tag)

            val chatTEXT = AnnotatedString(
                text = content,
                spanStyle = SpanStyle(
                    color = usermsgColor,
                    fontWeight = FontWeight.Medium
                )
            )
            minibuilder.append(chatTEXT)

            minibuilder.toAnnotatedString()
        } else {
            AnnotatedString(
                text = content,
                spanStyle = SpanStyle(
                    if (isError) errormsgColor else systemmsgColor
                )
            )
        }

        /* Finally, we append our little annotatedStrings into our builder */
        builder.append(timestampAS)
        builder.append(contentAS)

        return builder.toAnnotatedString()
    }
}