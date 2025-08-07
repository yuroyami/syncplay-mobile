package com.yuroyami.syncplay.screens.room.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.screens.adam.LocalChatPalette
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.valueAsState
import kotlinx.coroutines.delay


@Composable
fun FadingMessageLayout() {
    val viewmodel = LocalViewmodel.current

    val isInPiPMode by viewmodel.hasEnteredPipMode.collectAsState()
    val isHUDVisible by viewmodel.visibleHUD.collectAsState()

    /** The layout for the fading messages & OSD messages (when HUD is hidden, or when screen is locked) */
    val fadingTimeout = DataStoreKeys.PREF_INROOM_MSG_FADING_DURATION.valueAsState(3)
    val palette = LocalChatPalette.current

    if (!isHUDVisible) {
        var visibility by remember { mutableStateOf(false) }
        val msgs = remember { viewmodel.p.session.messageSequence }
        LaunchedEffect(msgs.size) {
            if (viewmodel.p.session.messageSequence.isNotEmpty()) {
                val lastMsg = viewmodel.p.session.messageSequence.last()

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
                    text = viewmodel.p.session.messageSequence.last().factorize(palette),
                    lineHeight = if (isInPiPMode) 9.sp else 15.sp,
                    fontSize = if (isInPiPMode) 8.sp else 13.sp
                )
            }
        }
    }
}