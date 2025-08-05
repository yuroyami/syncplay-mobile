package com.yuroyami.syncplay.screens.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueAsState
import com.yuroyami.syncplay.ui.Paletting

//TODO DONT DO THIS TO PRODUCE A MSG PALETTE
@Composable
fun ComposedMessagePalette(): MessagePalette {
    val colorTimestamp = DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP.valueAsState(Paletting.MSG_TIMESTAMP.toArgb())
    val colorSelftag = DataStoreKeys.PREF_INROOM_COLOR_SELFTAG.valueAsState(Paletting.MSG_SELF_TAG.toArgb())
    val colorFriendtag = DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG.valueAsState(Paletting.MSG_FRIEND_TAG.toArgb())
    val colorSystem = DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG.valueAsState(Paletting.MSG_SYSTEM.toArgb())
    val colorUserchat = DataStoreKeys.PREF_INROOM_COLOR_USERMSG.valueAsState(Paletting.MSG_CHAT.toArgb())
    val colorError = DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG.valueAsState(Paletting.MSG_ERROR.toArgb())
    val msgIncludeTimestamp = DataStoreKeys.PREF_INROOM_MSG_ACTIVATE_STAMP.valueAsState(true)

    return MessagePalette(
        timestampColor = Color(colorTimestamp.value),
        selftagColor = Color(colorSelftag.value),
        friendtagColor = Color(colorFriendtag.value),
        systemmsgColor = Color(colorSystem.value),
        usermsgColor = Color(colorUserchat.value),
        errormsgColor = Color(colorError.value),
        includeTimestamp = msgIncludeTimestamp.value
    )
}


/**
 * Jetpack Compose provides three overloads of `AnimatedVisibility`.
 * Two of them have `ColumnScope` or `RowScope` as receivers, and one has neither.
 *
 * When adding `AnimatedVisibility` to a `Box` that's nested inside a `Column` or `Row`,
 * this can lead to ambiguity due to receiver resolution.
 *
 * This method is used to eliminate that ambiguity by explicitly selecting the correct overload.
 */
@Composable
fun FreeAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        label = label,
        content = content
    )
}