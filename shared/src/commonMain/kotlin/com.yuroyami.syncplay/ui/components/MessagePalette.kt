package com.yuroyami.syncplay.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.DatastoreManager.Companion.watch
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.ui.theme.Theming

val messagePalette: State<MessagePalette>
    @Composable get() {
        val colorTimestamp = DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP.watch(Theming.MSG_TIMESTAMP.toArgb())
        val colorSelftag = DataStoreKeys.PREF_INROOM_COLOR_SELFTAG.watch(Theming.MSG_SELF_TAG.toArgb())
        val colorFriendtag = DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG.watch(Theming.MSG_FRIEND_TAG.toArgb())
        val colorSystem = DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG.watch(Theming.MSG_SYSTEM.toArgb())
        val colorUserchat = DataStoreKeys.PREF_INROOM_COLOR_USERMSG.watch(Theming.MSG_CHAT.toArgb())
        val colorError = DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG.watch(Theming.MSG_ERROR.toArgb())
        val msgIncludeTimestamp = DataStoreKeys.PREF_INROOM_MSG_ACTIVATE_STAMP.watch(true)

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