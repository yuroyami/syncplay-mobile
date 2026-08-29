package app.room.ui.tabs

import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

internal actual fun Modifier.lockedChatKeyboardInput(
    enabled: Boolean,
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    onSend: () -> Unit,
): Modifier {
    if (!enabled) return this

    return onPreviewKeyEvent { event ->
        val nativeEvent = event.nativeKeyEvent
        if (nativeEvent.action != KeyEvent.ACTION_DOWN) {
            return@onPreviewKeyEvent false
        }

        when (nativeEvent.keyCode) {
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                onSend()
                true
            }

            KeyEvent.KEYCODE_DEL -> {
                onBackspace()
                true
            }

            else -> {
                if (nativeEvent.isMetaPressed ||
                    (nativeEvent.isCtrlPressed && !nativeEvent.isAltPressed)
                ) {
                    return@onPreviewKeyEvent false
                }
                val codePoint = nativeEvent.unicodeChar
                if (codePoint <= 0 || Character.isISOControl(codePoint)) {
                    false
                } else {
                    onText(String(Character.toChars(codePoint)))
                    true
                }
            }
        }
    }
}
