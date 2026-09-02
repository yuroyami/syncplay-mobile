package app.room.ui.bottombar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import app.LocalRoomViewmodel
import app.preferences.Preferences.CUSTOM_SEEK_AMOUNT
import app.preferences.value
import app.preferences.watchPref
import app.room.RoomViewmodel
import app.theme.Motion
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.Field
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Text
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.timestampFromMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.done
import syncplaymobile.shared.generated.resources.room_custom_skip_button
import syncplaymobile.shared.generated.resources.room_seek_toposition_hint
import syncplaymobile.shared.generated.resources.room_seek_toposition_success
import syncplaymobile.shared.generated.resources.room_seek_toposition_title

object PopupSeekToPosition {

    /**
     * One timecode field instead of three: digits shift in from the right like a microwave
     * clock, so 1 2 3 4 reads 00:12:34 and there is nothing to tab between. The frame has no
     * title, so the whole thing still fits above the keyboard in landscape.
     */
    @Composable
    fun SeekToPositionPopup() {
        val viewmodel = LocalRoomViewmodel.current
        val visible by viewmodel.uiState.popupSeekToPosition.collectAsState()
        if (!visible) return
        val p = palette
        val focusManager = LocalFocusManager.current
        var digits by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        val customSkipAmount by CUSTOM_SEEK_AMOUNT.watchPref()
        val customSkipLabel = stringResource(Res.string.room_custom_skip_button, timestampFromMillis(customSkipAmount * 1000L))

        fun close() { viewmodel.uiState.popupSeekToPosition.value = false }
        fun commit() {
            close()
            val padded = digits.padStart(6, '0')
            val hh = padded.substring(0, 2).toLong()
            val mm = padded.substring(2, 4).toLong().coerceAtMost(59)
            val ss = padded.substring(4, 6).toLong().coerceAtMost(59)
            val result = ss * 1000 + mm * 60_000 + hh * 3_600_000
            // The one seek path: announce first so a rewind does not yank us back.
            viewmodel.dispatcher.seek(result)
            viewmodel.dispatchOSD { getString(Res.string.room_seek_toposition_success, timestampFromMillis(result)) }
        }

        // Focus lands after the modal has entered, so the keyboard does not fight the animation.
        LaunchedEffect(Unit) {
            delay(Motion.moveMs.toLong() + 50)
            runCatching { focus.requestFocus() }
        }

        Modal(
            open = true,
            onDismiss = ::close,
            size = ModalSize.Ask,
            actions = {
                SecondaryAction(customSkipLabel, onClick = { close(); viewmodel.customSkip() })
                AccentAction(stringResource(Res.string.done), onClick = ::commit, enabled = digits.isNotEmpty())
            },
        ) {
            Field(
                value = format(digits),
                onValueChange = { digits = it.filter(Char::isDigit).takeLast(6) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "00:00:00",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                onImeAction = { focusManager.clearFocus(true); if (digits.isNotEmpty()) commit() },
                focusRequester = focus,
                showClear = false,
                textStyle = Type.display.copy(textAlign = TextAlign.Center),
                name = stringResource(Res.string.room_seek_toposition_title),
            )
            Spacer(Modifier.height(Space.gapTight))
            Text(stringResource(Res.string.room_seek_toposition_hint), style = Type.note, color = p.inkDim)
        }
    }

    /** Empty stays empty (the placeholder shows); otherwise hh:mm:ss from the right-aligned digits. */
    private fun format(digits: String): String {
        if (digits.isEmpty()) return ""
        val padded = digits.padStart(6, '0')
        return padded.substring(0, 2) + ":" + padded.substring(2, 4) + ":" + padded.substring(4, 6)
    }
}

fun RoomViewmodel.customSkip() {
    player.playerScopeMain.launch {
        val currentMs = player.currentPositionMs()
        val newPos = currentMs + CUSTOM_SEEK_AMOUNT.value() * 1000L
        dispatcher.seek(newPos, fromMs = currentMs)
        // The same notice as seek-to, so it gets the same timecode shape, not a bare count.
        dispatchOSD { getString(Res.string.room_seek_toposition_success, timestampFromMillis(newPos)) }
    }
}
