package com.yuroyami.syncplay.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_ERRORMSG
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_FRIENDTAG
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_SELFTAG
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_SYSTEMMSG
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_TIMESTAMP
import com.yuroyami.syncplay.managers.preferences.Preferences.COLOR_USERMSG
import com.yuroyami.syncplay.managers.preferences.Preferences.MSG_ACTIVATE_STAMP
import com.yuroyami.syncplay.managers.preferences.watchPref
import com.yuroyami.syncplay.models.MessagePalette

val messagePalette: State<MessagePalette>
    @Composable get() {
        val colorTimestamp = COLOR_TIMESTAMP.watchPref()
        val colorSelftag = COLOR_SELFTAG.watchPref()
        val colorFriendtag = COLOR_FRIENDTAG.watchPref()
        val colorSystem = COLOR_SYSTEMMSG.watchPref()
        val colorUserchat = COLOR_USERMSG.watchPref()
        val colorError = COLOR_ERRORMSG.watchPref()
        val msgIncludeTimestamp = MSG_ACTIVATE_STAMP.watchPref()

        return derivedStateOf {
            MessagePalette(
                timestampColor = Color(colorTimestamp.value),
                selftagColor = Color(colorSelftag.value),
                friendtagColor = Color(colorFriendtag.value),
                systemmsgColor = Color(colorSystem.value),
                usermsgColor = Color(colorUserchat.value),
                errormsgColor = Color(colorError.value),
                includeTimestamp = msgIncludeTimestamp.value
            )
        }
    }