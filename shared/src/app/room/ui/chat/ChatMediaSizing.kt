package app.room.ui.chat

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val CHAT_MEDIA_COLUMNS = 4
internal val CHAT_MEDIA_GAP = 4.dp
internal fun chatMediaCellSize(drawerWidth: Dp): Dp =
    ((drawerWidth - CHAT_MEDIA_GAP * (CHAT_MEDIA_COLUMNS + 1)) / CHAT_MEDIA_COLUMNS).coerceAtLeast(1.dp)
internal val LocalChatMediaSize = staticCompositionLocalOf { chatMediaCellSize(300.dp) }
