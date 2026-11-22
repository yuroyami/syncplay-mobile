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
import app.preferences.Preferences.MSG_BG_OPACITY
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
import kotlinx.coroutines.delay

/** How many lines the picture-in-picture window shows at most. The window is small. */
internal const val PIP_LINES = 2

/** The least opacity of the plate behind a line in picture-in-picture, of 255. */
private const val PIP_PLATE = 150

/**
 * Shows the last few unseen chat lines from other users over the video while the HUD (the
 * controls over the video) is hidden. The lines keep the two shapes of the chat list, in the style
 * of chat over video ([overVideoStyle]). The fading count preference sets how many lines show, and
 * the fading duration sets how long each one stays.
 *
 * In picture-in-picture, the window has no HUD, so the lines show whatever the HUD state says. The
 * window is small, so it shows [PIP_LINES] lines at most, in small text, and each line always has
 * a plate behind it.
 *
 * [app.room.RoomScreenUI] places this layout under the notices on the center line, or at the
 * bottom of the picture-in-picture window. The layout only caps its own width to the width of a
 * notice, so a line wraps at the same edge as a notice.
 */
@Composable
fun FadingMessageLayout() {
    val viewmodel = LocalRoomViewmodel.current
    val isInPiPMode by viewmodel.uiState.hasEnteredPipMode.collectAsState()
    val isHUDVisible by viewmodel.uiState.visibleHUD.collectAsState()
    if (isHUDVisible && !isInPiPMode) return

    val chatPalette = LocalChatPalette.current
    val holdSeconds by MSG_FADING_DURATION.watchPref()
    val maxCountPreference by MSG_MAXCOUNT.watchPref()
    val outlineThickness by MSG_OUTLINE_THICKNESS.watchPref()
    val shadowOn by MSG_SHADOW_ACTIVATE.watchPref()
    val fontSize by MSG_FONTSIZE.watchPref()
    val bgOpacity by MSG_BG_OPACITY.watchPref()
    val hasVideo by viewmodel.hasVideo.collectAsState()
    // In picture-in-picture, large text is capped at the default size, and a smaller size that the
    // user chose is kept. Elsewhere these lines are read from the middle of the picture, so they
    // never go below the size of a notice: the chat list size is for a dense list, not for a
    // glance over video.
    val noticeSize = Type.note.fontSize.value.toInt()
    val style = if (isInPiPMode) {
        overVideoStyle(minOf(fontSize, MSG_FONTSIZE.default), outlineThickness, shadowOn, maxOf(bgOpacity, PIP_PLATE), showTime = false)
    } else {
        overVideoStyle(maxOf(fontSize, noticeSize), outlineThickness, shadowOn, if (hasVideo) bgOpacity else 0, showTime = false)
    }
    val maxCount = if (isInPiPMode) minOf(maxCountPreference, PIP_LINES) else maxCountPreference

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
