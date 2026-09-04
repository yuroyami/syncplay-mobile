package app.room.models

import androidx.compose.ui.graphics.Color
import app.theme.Palette

/**
 * The colours chat draws with. A null colour means "follow the theme": it is resolved against the
 * palette in force where the chat is drawn, which is the dark over-video palette inside the room
 * and the plain theme palette everywhere else. The old fixed defaults were tuned for a dark room
 * and turned near-white on near-white the moment a light theme showed chat with no video behind it.
 */
data class MessagePalette(
    val timestampColor: Color? = null,
    val selftagColor: Color? = null,
    val friendtagColor: Color? = null,
    val systemmsgColor: Color? = null,
    val usermsgColor: Color? = null,
    val errormsgColor: Color? = null,
    val includeTimestamp: Boolean = true,
) {
    /** Fills every colour the user has not overridden from [palette]. */
    fun resolve(palette: Palette): ResolvedMessagePalette = ResolvedMessagePalette(
        timestampColor = timestampColor ?: palette.inkFaint,
        selftagColor = selftagColor ?: palette.accent,
        friendtagColor = friendtagColor ?: palette.ok,
        systemmsgColor = systemmsgColor ?: palette.inkDim,
        usermsgColor = usermsgColor ?: palette.ink,
        errormsgColor = errormsgColor ?: palette.bad,
        includeTimestamp = includeTimestamp,
    )
}

/** A [MessagePalette] with every colour decided, which is what the chat rows actually draw with. */
data class ResolvedMessagePalette(
    val timestampColor: Color,
    val selftagColor: Color,
    val friendtagColor: Color,
    val systemmsgColor: Color,
    val usermsgColor: Color,
    val errormsgColor: Color,
    val includeTimestamp: Boolean = true,
)
