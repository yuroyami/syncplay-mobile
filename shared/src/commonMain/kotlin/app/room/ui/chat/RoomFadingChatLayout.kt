package app.room.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.LocalChatPalette
import app.LocalRoomViewmodel
import app.preferences.Preferences.MSG_FADING_DURATION
import app.preferences.Preferences.MSG_FONTSIZE
import app.preferences.Preferences.MSG_MAXCOUNT
import app.preferences.Preferences.MSG_OUTLINE_THICKNESS
import app.preferences.Preferences.MSG_SHADOW_ACTIVATE
import app.preferences.watchPref
import app.room.models.Message
import app.room.models.fadingMessages
import kotlin.time.Duration.Companion.seconds
import app.theme.Motion
import app.theme.Space
import app.theme.Type
import app.theme.palette
import kotlinx.coroutines.delay

/**
 * With the HUD hidden, the last few unseen lines from other people show over the video in the
 * same two shapes as the list, with no panel behind them: this is where the outline preference
 * earns its keep. The count is the fading count preference, the hold is the fading duration.
 *
 * It draws where the room puts it, under the notices on the centre line, and only caps its own
 * width to a notice's, so a line wraps at the same edge a notice would.
 */
@Composable
fun FadingMessageLayout() {
    val viewmodel = LocalRoomViewmodel.current
    val isInPiPMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    val isHUDVisible by viewmodel.uiState.visibleHUD.collectAsState()
    if (isHUDVisible) return

    val chatPalette = LocalChatPalette.current.resolve(palette)
    val holdSeconds by MSG_FADING_DURATION.watchPref()
    val maxCount by MSG_MAXCOUNT.watchPref()
    val outlineThickness by MSG_OUTLINE_THICKNESS.watchPref()
    val shadowOn by MSG_SHADOW_ACTIVATE.watchPref()
    val fontSize by MSG_FONTSIZE.watchPref()
    // PiP caps large text at the default, while respecting a smaller size chosen by the user.
    // Anywhere else these lines are read from the middle of the picture, so they never go
    // below a notice's size: the log's size is for a dense list, not for a glance over video.
    val noticeSize = Type.note.fontSize.value.toInt()
    val size = if (isInPiPMode) minOf(fontSize, MSG_FONTSIZE.default) else maxOf(fontSize, noticeSize)
    val style = MessageStyle(size, outlineThickness.toFloat().takeIf { it > 0f }, shadowOn, showTime = false)

    val messages by viewmodel.session.messageSequence.collectAsState()
    var shown by remember { mutableStateOf<List<Message>>(emptyList()) }
    var visible by remember { mutableStateOf(false) }
    val muted = viewmodel.uiState.mutedUsers.toSet()
    LaunchedEffect(messages, holdSeconds, maxCount, muted) {
        val hold = holdSeconds.coerceAtLeast(0).seconds
        while (true) {
            val recent = fadingMessages(messages, hold, maxCount, muted)
            visible = recent.isNotEmpty()
            if (!visible) break
            shown = recent
            val nextExpiry = recent.minOf { (hold - it.receivedAt.elapsedNow()).inWholeMilliseconds }
            delay(nextExpiry.coerceAtLeast(1) + 1)
        }
    }

    Column(modifier = Modifier.widthIn(max = Space.noticeWidth).fillMaxWidth()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(Motion.quick()), exit = fadeOut(Motion.move())) {
            Column {
                shown.forEachIndexed { index, message ->
                    MessageRow(message, shown.getOrNull(index - 1), chatPalette, style, imageAlpha = if (visible) 1f else 0f)
                }
            }
        }
    }
}
