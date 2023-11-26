package com.yuroyami.syncplay.watchroom

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
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.compose.ComposeUtils
import com.yuroyami.syncplay.compose.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.compose.ComposeUtils.radiantOverlay
import com.yuroyami.syncplay.compose.ComposeUtils.solidOverlay
import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.booleanFlow
import com.yuroyami.syncplay.datastore.ds
import com.yuroyami.syncplay.datastore.intFlow
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.ui.Paletting
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

object RoomComposables {

    @Composable
    fun ComposedMessagePalette(): MessagePalette {

        val colorTimestamp = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP, Paletting.MSG_TIMESTAMP.toArgb())
            .collectAsState(initial = Paletting.MSG_TIMESTAMP.toArgb())
        val colorSelftag =
            DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_SELFTAG, Paletting.MSG_SELF_TAG.toArgb()).collectAsState(initial = Paletting.MSG_SELF_TAG.toArgb())
        val colorFriendtag = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG, Paletting.MSG_FRIEND_TAG.toArgb())
            .collectAsState(initial = Paletting.MSG_FRIEND_TAG.toArgb())
        val colorSystem =
            DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG, Paletting.MSG_SYSTEM.toArgb()).collectAsState(initial = Paletting.MSG_SYSTEM.toArgb())
        val colorUserchat =
            DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_USERMSG, Paletting.MSG_CHAT.toArgb()).collectAsState(initial = Paletting.MSG_CHAT.toArgb())
        val colorError =
            DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG, Paletting.MSG_ERROR.toArgb()).collectAsState(initial = Paletting.MSG_ERROR.toArgb())
        val msgIncludeTimestamp = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().booleanFlow(DataStoreKeys.PREF_INROOM_MSG_ACTIVATE_STAMP, true).collectAsState(initial = true)

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
                    painter = painterResource("images/syncplay_logo_gradient.svg"), contentDescription = "",
                    modifier = Modifier
                        .height(if (pipModeObserver) 40.dp else 84.dp)
                        .aspectRatio(1f)
                        .radiantOverlay(offset = Offset(x = 50f, y = 80f))
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
                            fontFamily = FontFamily(Font("fonts/Directive4-Regular.otf"))
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
                            fontFamily = FontFamily(Font("fonts/Directive4-Regular.otf"))
                        ),
                        fontSize = if (pipModeObserver) 8.sp else 26.sp,
                    )
                }
            }
        }
    }

    @Composable
    fun fadingMessageLayout(hudVisibility: Boolean, pipModeObserver: Boolean, msgPalette: MessagePalette) {
        /** The layout for the fading messages & OSD messages (when HUD is hidden, or when screen is locked) */
        val fadingTimeout = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_MSG_FADING_DURATION, 2).collectAsState(initial = 3)

        if (!hudVisibility) {
            var visibility by remember { mutableStateOf(false) }

            LaunchedEffect(p.session.messageSequence.size) {
                if (p.session.messageSequence.isNotEmpty()) {
                    val lastMsg = p.session.messageSequence.last()

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
                        text = p.session.messageSequence.last().factorize(msgPalette),
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
                    indication = rememberRipple(color = Paletting.SP_ORANGE)
                ) {
                    onClick.invoke()

                },
            shape = RoundedCornerShape(6.dp),
            border = if (visibilityState) {
                null
            } else {
                BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT))
            },
            colors = CardDefaults.cardColors(containerColor = if (visibilityState) Color.Transparent else MaterialTheme.colorScheme.tertiaryContainer),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (visibilityState) {
                            Brush.linearGradient(colors = Paletting.SP_GRADIENT)
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
        if (hasVideoG.value) {
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
                border = BorderStroke(1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
                shadowElevation = 12.dp,
                tonalElevation = 4.dp,
                onClick = { onClick.invoke() },
                contentColor = Color.DarkGray
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            tint = Color.DarkGray, imageVector = Icons.Filled.AddToQueue, contentDescription = "",
                            modifier = Modifier.size(32.dp).gradientOverlay() //.align(Alignment.Center)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(modifier = Modifier.fillMaxWidth().gradientOverlay(),
                            text = "Add media", textAlign = TextAlign.Center, maxLines = 1,
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


    /** Ping */
    @Composable
    fun PingRadar(pingValue: Int?) {
        when (pingValue) {
            null -> {
                Image(
                    painter = painterResource("images/network_level_0@1x.png"), "",
                    modifier = Modifier.size(16.dp).solidOverlay(Color.Gray)
                )
            }

            in (0..90) -> {
                Image(
                    painter = painterResource("images/network_level_4@1x.png"), "",
                    modifier = Modifier.size(16.dp).solidOverlay(Color.Green)
                )
            }
            in (91..120) -> {
                Image(
                    painter = painterResource("images/network_level_3@1x.png"), "",
                    modifier = Modifier.size(16.dp).solidOverlay(Color.Yellow)
                )
            }
            in (121..160) -> {
                Image(
                    painter = painterResource("images/network_level_3@1x.png"), "",
                    modifier = Modifier.size(16.dp).solidOverlay(Color(255, 176, 66))
                )
            }
            in (161..200) -> {
                Image(
                    painter = painterResource("images/network_level_2@1x.png"), "",
                    modifier = Modifier.size(16.dp).solidOverlay(Color(181, 80, 25))
                )
            }
            else -> {
                Image(
                    painter = painterResource("images/network_level_1@1x.png"), "",
                    modifier = Modifier.size(16.dp).solidOverlay(Color.Red)
                )
            }
        }
    }
}