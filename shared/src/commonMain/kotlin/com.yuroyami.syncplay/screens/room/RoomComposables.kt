package com.yuroyami.syncplay.screens.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.outlined.NetworkWifi
import androidx.compose.material.icons.outlined.NetworkWifi1Bar
import androidx.compose.material.icons.outlined.NetworkWifi2Bar
import androidx.compose.material.icons.outlined.NetworkWifi3Bar
import androidx.compose.material.icons.outlined.SignalWifi4Bar
import androidx.compose.material.icons.outlined.SignalWifiConnectedNoInternet4
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.components.ComposeUtils
import com.yuroyami.syncplay.components.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueAsState
import com.yuroyami.syncplay.ui.Paletting
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_button_desc_add

object RoomComposables {

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





    @Composable
    fun AddVideoButton(modifier: Modifier, expanded: Boolean, onClick: () -> Unit) {
        if (!expanded) {
            ComposeUtils.FancyIcon2(
                modifier = modifier,
                icon = Icons.Filled.AddToQueue, size = Paletting.ROOM_ICON_SIZE + 6, shadowColor = Color.Black,
                onClick = {
                    onClick.invoke()
                })
        } else {
            Surface(
                modifier = modifier.width(150.dp).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
                onClick = { onClick.invoke() },
                contentColor = Color.DarkGray.copy(0.5f)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {

                        Icon(
                            tint = Color.DarkGray, imageVector = Icons.Filled.AddToQueue, contentDescription = "",
                            modifier = Modifier.size(32.dp).gradientOverlay() //.align(Alignment.Center)
                        )



                        Text(modifier = Modifier.gradientOverlay(),
                            text = stringResource(Res.string.room_button_desc_add), textAlign = TextAlign.Center, maxLines = 1,
                            fontSize = 14.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
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

    sealed class PingLevel(
        val icon: ImageVector,
        val tint: Color
    ) {
        data object NoInternet : PingLevel(Icons.Outlined.SignalWifiConnectedNoInternet4, Color.Gray)
        data object Excellent : PingLevel(Icons.Outlined.SignalWifi4Bar, Color.Green)
        data object Good : PingLevel(Icons.Outlined.NetworkWifi, Color.Yellow)
        data object Fair : PingLevel(Icons.Outlined.NetworkWifi3Bar, Color(255, 176, 66))
        data object Poor : PingLevel(Icons.Outlined.NetworkWifi2Bar, Color(181, 80, 25))
        data object Terrible : PingLevel(Icons.Outlined.NetworkWifi1Bar, Color.Red)
        companion object {
            fun from(ping: Int?): PingLevel = when (ping) {
                null -> NoInternet
                in 0..90 -> Excellent
                in 91..120 -> Good
                in 121..160 -> Fair
                in 161..200 -> Poor
                else -> Terrible
            }
        }
    }

    @Composable
    fun PingRadar(pingValue: Int?) {
        val pingLevel = PingLevel.from(pingValue)
        Icon(
            imageVector = pingLevel.icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = pingLevel.tint
        )
    }
}