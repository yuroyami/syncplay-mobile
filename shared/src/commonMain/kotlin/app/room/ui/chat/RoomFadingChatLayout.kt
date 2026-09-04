package app.room.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import app.room.roomTopInsets
import app.theme.Motion
import app.theme.Space
import app.theme.palette
import kotlinx.coroutines.delay

/**
 * With the HUD hidden, the last few unseen lines from other people show over the video in the
 * same two shapes as the list, with no panel behind them: this is where the outline preference
 * earns its keep. The count is the fading count preference, the hold is the fading duration.
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
    // Picture in picture drops to the floor, never below it.
    val style = MessageStyle(if (isInPiPMode) 11 else fontSize, outlineThickness.toFloat().takeIf { it > 0f }, shadowOn, showTime = false)

    val messages by viewmodel.session.messageSequence.collectAsState()
    var shown by remember { mutableStateOf<List<Message>>(emptyList()) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        val recent = messages.filter { !it.isMainUser && !it.seen }.takeLast(maxCount)
        if (recent.isEmpty()) return@LaunchedEffect
        shown = recent
        visible = true
        delay(holdSeconds * 1000L)
        visible = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .windowInsetsPadding(roomTopInsets())
            .padding(start = Space.gutter, top = Space.rowCompact + Space.gap),
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn(Motion.quick()), exit = fadeOut(Motion.move())) {
            Column {
                shown.forEachIndexed { index, message ->
                    MessageRow(message, shown.getOrNull(index - 1), chatPalette, style)
                }
            }
        }
    }
}
