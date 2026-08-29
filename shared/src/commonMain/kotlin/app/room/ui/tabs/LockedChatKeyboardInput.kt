package app.room.ui.tabs

import androidx.compose.ui.Modifier

internal expect fun Modifier.lockedChatKeyboardInput(
    enabled: Boolean,
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    onSend: () -> Unit,
): Modifier

internal fun appendLockedChatText(
    draft: String,
    text: String,
    maxLength: Int,
): String = (draft + text).take(maxLength.coerceAtLeast(1))

internal fun removeLockedChatCharacter(draft: String): String {
    if (draft.isEmpty()) return draft
    val characterCount = if (
        draft.length >= 2 &&
        draft.last().isLowSurrogate() &&
        draft[draft.lastIndex - 1].isHighSurrogate()
    ) {
        2
    } else {
        1
    }
    return draft.dropLast(characterCount)
}

internal fun lockedChatMessageToSend(
    draft: String,
    maxLength: Int,
): String? = draft.take(maxLength.coerceAtLeast(1)).takeIf { it.isNotBlank() }
