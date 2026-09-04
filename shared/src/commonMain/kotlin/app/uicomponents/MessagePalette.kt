package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import app.preferences.Preferences.COLOR_ERRORMSG
import app.preferences.Preferences.COLOR_FRIENDTAG
import app.preferences.Preferences.COLOR_SELFTAG
import app.preferences.Preferences.COLOR_SYSTEMMSG
import app.preferences.Preferences.COLOR_TIMESTAMP
import app.preferences.Preferences.COLOR_USERMSG
import app.preferences.watchPref
import app.room.models.MessagePalette

/**
 * The stored colour meaning "no override, follow the theme". Fully transparent black is not a
 * colour anyone picks for text, so it is safe to spend as the sentinel, and it lets the chat
 * colours stay per-theme by default while a user who wants a fixed colour still gets one.
 */
const val CHAT_COLOR_FOLLOWS_THEME: Int = 0

private fun Int.asOverride(): Color? = if (this == CHAT_COLOR_FOLLOWS_THEME) null else Color(this)

val messagePalette: State<MessagePalette>
    @Composable get() {
        val colorTimestamp = COLOR_TIMESTAMP.watchPref()
        val colorSelftag = COLOR_SELFTAG.watchPref()
        val colorFriendtag = COLOR_FRIENDTAG.watchPref()
        val colorSystem = COLOR_SYSTEMMSG.watchPref()
        val colorUserchat = COLOR_USERMSG.watchPref()
        val colorError = COLOR_ERRORMSG.watchPref()

        return derivedStateOf {
            MessagePalette(
                timestampColor = colorTimestamp.value.asOverride(),
                selftagColor = colorSelftag.value.asOverride(),
                friendtagColor = colorFriendtag.value.asOverride(),
                systemmsgColor = colorSystem.value.asOverride(),
                usermsgColor = colorUserchat.value.asOverride(),
                errormsgColor = colorError.value.asOverride(),
                includeTimestamp = true,
            )
        }
    }
