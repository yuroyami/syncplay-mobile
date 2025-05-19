package com.yuroyami.syncplay.screens.room

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.outlined.NetworkWifi
import androidx.compose.material.icons.outlined.NetworkWifi1Bar
import androidx.compose.material.icons.outlined.NetworkWifi2Bar
import androidx.compose.material.icons.outlined.NetworkWifi3Bar
import androidx.compose.material.icons.outlined.SignalWifi4Bar
import androidx.compose.material.icons.outlined.SignalWifiConnectedNoInternet4
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.components.ComposeUtils
import com.yuroyami.syncplay.components.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.settingBooleanState
import com.yuroyami.syncplay.settings.settingIntState
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_button_desc_add
import syncplaymobile.shared.generated.resources.syncplay_logo_gradient

object RoomComposables {

    @Composable
    fun ComposedMessagePalette(): MessagePalette {
        val colorTimestamp = DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP.settingIntState()
        val colorSelftag = DataStoreKeys.PREF_INROOM_COLOR_SELFTAG.settingIntState()
        val colorFriendtag = DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG.settingIntState()
        val colorSystem = DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG.settingIntState()
        val colorUserchat = DataStoreKeys.PREF_INROOM_COLOR_USERMSG.settingIntState()
        val colorError = DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG.settingIntState()
        val msgIncludeTimestamp = DataStoreKeys.PREF_INROOM_MSG_ACTIVATE_STAMP.settingBooleanState()

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

    /** The Syncplay artwork that is displayed in the video frame when no video is loaded */
    @Composable
    fun RoomArtwork(pipModeObserver: Boolean) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = Paletting.backgroundGradient()
                    )
                ),
        ) {
            Column(
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Image(
                    painter = painterResource(Res.drawable.syncplay_logo_gradient), contentDescription = "",
                    modifier = Modifier
                        .height(if (pipModeObserver) 40.dp else 84.dp)
                        .aspectRatio(1f)
                       // .radiantOverlay(offset = Offset(x = 50f, y = 80f))
                )

                Spacer(modifier = Modifier.width(14.dp))

                Box(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(
                        modifier = Modifier.wrapContentWidth(),
                        text = "Syncplay",
                        style = TextStyle(
                            color = Paletting.SP_PALE,
                            drawStyle = Stroke(
                                miter = 10f,
                                width = 2f,
                                join = StrokeJoin.Round
                            ),
                            shadow = Shadow(
                                color = Paletting.SP_INTENSE_PINK,
                                offset = Offset(0f, 10f),
                                blurRadius = 5f
                            ),
                            fontFamily = FontFamily(Font(Res.font.Directive4_Regular))
                        ),
                        fontSize = if (pipModeObserver) 8.sp else 26.sp,
                    )
                    Text(
                        modifier = Modifier.wrapContentWidth(),
                        text = "Syncplay",
                        style = TextStyle(
                            brush = Brush.linearGradient(
                                colors = Paletting.SP_GRADIENT
                            ),
                            fontFamily = FontFamily(Font(Res.font.Directive4_Regular))
                        ),
                        fontSize = if (pipModeObserver) 8.sp else 26.sp,
                    )
                }
            }
        }
    }

    @Composable
    fun FadingMessageLayout(hudVisibility: Boolean, pipModeObserver: Boolean) {
        /** The layout for the fading messages & OSD messages (when HUD is hidden, or when screen is locked) */
        val fadingTimeout = DataStoreKeys.PREF_INROOM_MSG_FADING_DURATION.settingIntState()
        val palette = LocalChatPalette.current

        if (!hudVisibility) {
            var visibility by remember { mutableStateOf(false) }

            LaunchedEffect(viewmodel!!.p.session.messageSequence.size) {
                if (viewmodel!!.p.session.messageSequence.isNotEmpty()) {
                    val lastMsg = viewmodel!!.p.session.messageSequence.last()

                    if (!lastMsg.isMainUser && !lastMsg.seen) {
                        visibility = true
                        delay(fadingTimeout.value.toLong() * 1000L)
                        visibility = false
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                AnimatedVisibility(
                    enter = fadeIn(animationSpec = keyframes { durationMillis = 100 }),
                    exit = fadeOut(animationSpec = keyframes { durationMillis = 500 }),
                    visible = visibility,
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .focusable(false),
                        overflow = TextOverflow.Ellipsis,
                        text = viewmodel!!.p.session.messageSequence.last().factorize(palette),
                        lineHeight = if (pipModeObserver) 9.sp else 15.sp,
                        fontSize = if (pipModeObserver) 8.sp else 13.sp
                    )
                }
            }
        }
    }

    @Composable
    fun RoomTab(icon: ImageVector, visibilityState: Boolean, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Paletting.SP_ORANGE)
                ) {
                    onClick.invoke()

                },
            shape = RoundedCornerShape(6.dp),
            border = if (visibilityState) {
                null
            } else {
                BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) }))
            },
            colors = CardDefaults.cardColors(containerColor = if (visibilityState) Color.Transparent else MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f)),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (visibilityState) {
                            Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })
                        } else {
                            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
            ) {
                if (visibilityState) {
                    Icon(
                        tint = Color.DarkGray,
                        imageVector = icon,
                        contentDescription = "",
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center)
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = "",
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center)
                            .gradientOverlay()
                    )
                }

            }
        }
    }

    @Composable
    fun AddVideoButton(modifier: Modifier, onClick: () -> Unit) {
        if (viewmodel?.hasVideoG?.value == true) {
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

    /** There are 3 overloads for AnimatedVisibility in Jetpack Compose, two of them use ColumnScope
     * or RowScope as receivers, so we cannot use the one that uses neither when we are adding it to
     * a box that is contained in another column or row, we use this method to remove this ambiguity.
     */
    @Composable
    fun FreeAnimatedVisibility(
        visible: Boolean,
        modifier: Modifier = Modifier,
        enter: EnterTransition = fadeIn() + expandIn(),
        exit: ExitTransition = shrinkOut() + fadeOut(),
        label: String = "AnimatedVisibility",
        content: @Composable() AnimatedVisibilityScope.() -> Unit
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
        object NoInternet : PingLevel(Icons.Outlined.SignalWifiConnectedNoInternet4, Color.Gray)
        object Excellent : PingLevel(Icons.Outlined.SignalWifi4Bar, Color.Green)
        object Good : PingLevel(Icons.Outlined.NetworkWifi, Color.Yellow)
        object Fair : PingLevel(Icons.Outlined.NetworkWifi3Bar, Color(255, 176, 66))
        object Poor : PingLevel(Icons.Outlined.NetworkWifi2Bar, Color(181, 80, 25))
        object Terrible : PingLevel(Icons.Outlined.NetworkWifi1Bar, Color.Red)
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