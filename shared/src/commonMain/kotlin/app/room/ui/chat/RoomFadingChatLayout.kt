package app.room.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalChatPalette
import app.LocalRoomViewmodel
import app.preferences.Preferences.MSG_FADING_DURATION
import app.preferences.watchPref
import app.uicomponents.AnimatedImage
import kotlinx.coroutines.delay


/** Briefly shows the latest unseen non-self chat message when the HUD is hidden, then fades it out. */
@Composable
fun FadingMessageLayout() {
    val viewmodel = LocalRoomViewmodel.current

    val isInPiPMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    val isHUDVisible by viewmodel.uiState.visibleHUD.collectAsState()

    val fadingTimeout = MSG_FADING_DURATION.watchPref()
    val palette = LocalChatPalette.current

    if (!isHUDVisible) {
        var visibility by remember { mutableStateOf(false) }
        val msgs by viewmodel.session.messageSequence.collectAsState()
        LaunchedEffect(msgs.size) {
            if (msgs.isNotEmpty()) {
                val lastMsg = msgs.last()

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
                val lastMsg = msgs.last()
                if (lastMsg.isImageUrl) {
                    /* GIF/image message: show the sender tag + the rendered image, never the raw
                     * URL (mirrors how ChatBox renders these inline). */
                    Row(
                        modifier = Modifier.fillMaxWidth(0.8f).focusable(false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            overflow = TextOverflow.Ellipsis,
                            text = lastMsg.factorizeSenderTag(palette),
                            lineHeight = if (isInPiPMode) 9.sp else 15.sp,
                            fontSize = if (isInPiPMode) 8.sp else 13.sp
                        )
                        /* Alpha as a parameter (not Modifier.alpha): the iOS UIImageView interop
                         * layer ignores Compose alpha modifiers. */
                        AnimatedImage(
                            url = lastMsg.content,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 1f,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(if (isInPiPMode) 40.dp else 64.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                } else {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .focusable(false),
                        overflow = TextOverflow.Ellipsis,
                        text = lastMsg.factorize(palette),
                        lineHeight = if (isInPiPMode) 9.sp else 15.sp,
                        fontSize = if (isInPiPMode) 8.sp else 13.sp
                    )
                }
            }
        }
    }
}