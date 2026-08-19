package app.room.ui.tabs

import androidx.compose.ui.Modifier

internal actual fun Modifier.lockedChatKeyboardInput(
    enabled: Boolean,
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    onSend: () -> Unit,
): Modifier = this
