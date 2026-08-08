package app.room.ui.chat

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
import app.LocalChatPalette
import app.LocalRoomViewmodel
import app.preferences.Preferences.MSG_FADING_DURATION
import app.preferences.watchPref
import app.room.models.Message
import kotlinx.coroutines.delay

internal data class FadingMessagePresentation(
    val message: Message? = null,
    val visible: Boolean = false,
)

internal fun nextFadingMessagePresentation(
    current: FadingMessagePresentation,
    latest: Message?,
): FadingMessagePresentation {
    return if (latest != null && !latest.isMainUser && !latest.seen) {
        FadingMessagePresentation(message = latest, visible = true)
    } else {
        current.copy(visible = false)
    }
}

/** Briefly shows the latest unseen non-self chat message when the HUD is hidden, then fades it out. */
@Composable
fun FadingMessageLayout() {
    val viewmodel = LocalRoomViewmodel.current

    val isInPiPMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    val isHUDVisible by viewmodel.uiState.visibleHUD.collectAsState()

    val fadingTimeout = MSG_FADING_DURATION.watchPref()
    val palette = LocalChatPalette.current

    if (!isHUDVisible) {
        var presentation by remember { mutableStateOf(FadingMessagePresentation()) }
        val msgs by viewmodel.session.messageSequence.collectAsState()
        LaunchedEffect(msgs) {
            presentation = nextFadingMessagePresentation(presentation, msgs.lastOrNull())
            val displayedMessage = presentation.message
            if (presentation.visible) {
                delay(fadingTimeout.value.toLong() * 1000L)
                if (presentation.message === displayedMessage) {
                    presentation = presentation.copy(visible = false)
                }
            }
            delay(FADE_OUT_MILLIS.toLong())
            if (presentation.message === displayedMessage && !presentation.visible) {
                presentation = FadingMessagePresentation()
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            presentation.message?.let { message ->
                AnimatedVisibility(
                    enter = fadeIn(animationSpec = keyframes { durationMillis = 100 }),
                    exit = fadeOut(animationSpec = keyframes { durationMillis = FADE_OUT_MILLIS }),
                    visible = presentation.visible,
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .focusable(false),
                        overflow = TextOverflow.Ellipsis,
                        text = message.factorize(palette),
                        lineHeight = if (isInPiPMode) 9.sp else 15.sp,
                        fontSize = if (isInPiPMode) 8.sp else 13.sp
                    )
                }
            }
        }
    }
}

private const val FADE_OUT_MILLIS = 500
