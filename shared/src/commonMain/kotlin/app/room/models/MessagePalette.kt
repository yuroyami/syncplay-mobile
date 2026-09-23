package app.room.models

import androidx.compose.ui.graphics.Color

/**
 * The colors that chat draws with, one per chat color setting. The defaults below belong to chat.
 * No theme reads or changes them, and only the chat color settings override them.
 */
data class MessagePalette(
    val timestampColor: Color = Color(0xFF737373),
    val selftagColor: Color = Color(0xFF9879EF),
    val friendtagColor: Color = Color(0xFF6ECB5A),
    val systemmsgColor: Color = Color(0xFFA3A3A3),
    val usermsgColor: Color = Color.White,
    val errormsgColor: Color = Color(0xFFE85455),
    val includeTimestamp: Boolean = true,
)
