package app.room.ui.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import syncplaymobile.shared.generated.resources.room_seek_hours
import syncplaymobile.shared.generated.resources.room_seek_minutes
import syncplaymobile.shared.generated.resources.room_seek_seconds
import syncplaymobile.shared.generated.resources.room_seek_toposition_success
import syncplaymobile.shared.generated.resources.room_seek_toposition_timeformat
import syncplaymobile.shared.generated.resources.room_seek_toposition_title

object PopupSeekToPosition {

    /** Three tabular cells, focus chained HH to MM to SS, minutes and seconds clamped at 59. */
    @Composable
    fun SeekToPositionPopup() {
        val viewmodel = LocalRoomViewmodel.current
        val visible by viewmodel.uiState.popupSeekToPosition.collectAsState()
        if (!visible) return
        val p = palette
        val focusManager = LocalFocusManager.current
        var hours by remember { mutableStateOf("") }
        var minutes by remember { mutableStateOf("") }
        var seconds by remember { mutableStateOf("") }
        val hhFocus = remember { FocusRequester() }
        val mmFocus = remember { FocusRequester() }
        val ssFocus = remember { FocusRequester() }
        val customSkipAmount by CUSTOM_SEEK_AMOUNT.watchPref()
        val customSkipLabel = stringResource(Res.string.room_custom_skip_button, timestampFromMillis(customSkipAmount * 1000L))

        fun close() { viewmodel.uiState.popupSeekToPosition.value = false }
        fun commit() {
            close()
            val ss = (seconds.toLongOrNull() ?: 0L).coerceAtMost(59)
            val mm = (minutes.toLongOrNull() ?: 0L).coerceAtMost(59)
            val hh = hours.toLongOrNull() ?: 0L
            val result = ss * 1000 + mm * 60_000 + hh * 3_600_000
            // The one seek path: announce first so a rewind does not yank us back.
            viewmodel.dispatcher.seek(result)
            viewmodel.dispatchOSD { getString(Res.string.room_seek_toposition_success, timestampFromMillis(result)) }
        }

        // Focus lands after the modal has entered, so the keyboard does not fight the animation.
        LaunchedEffect(Unit) {
            delay(Motion.moveMs.toLong() + 50)
            runCatching { hhFocus.requestFocus() }
        }

        Modal(
            open = true,
            onDismiss = ::close,
            title = stringResource(Res.string.room_seek_toposition_title),
            size = ModalSize.Panel,
            actions = {
                SecondaryAction(customSkipLabel, onClick = { close(); viewmodel.customSkip() })
                AccentAction(stringResource(Res.string.done), onClick = ::commit)
            },
        ) {
            Text(stringResource(Res.string.room_seek_toposition_timeformat), style = Type.note, color = p.inkDim)
            Spacer(Modifier.height(Space.gap))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                TimeCell(hours, { hours = it.filter(Char::isDigit).take(3) }, "HH", stringResource(Res.string.room_seek_hours), hhFocus, ImeAction.Next) { mmFocus.requestFocus() }
                Text(":", style = Type.display, color = p.inkDim)
                TimeCell(minutes, { minutes = it.filter(Char::isDigit).take(2) }, "MM", stringResource(Res.string.room_seek_minutes), mmFocus, ImeAction.Next) { ssFocus.requestFocus() }
                Text(":", style = Type.display, color = p.inkDim)
                TimeCell(seconds, { seconds = it.filter(Char::isDigit).take(2) }, "SS", stringResource(Res.string.room_seek_seconds), ssFocus, ImeAction.Done) {
                    focusManager.clearFocus(true)
                    commit()
                }
            }
        }
    }

    @Composable
    private fun TimeCell(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        name: String,
        focusRequester: FocusRequester,
        imeAction: ImeAction,
        onImeAction: () -> Unit,
    ) {
        Field(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(72.dp),
            placeholder = placeholder,
            keyboardType = KeyboardType.Number,
            imeAction = imeAction,
            onImeAction = onImeAction,
            focusRequester = focusRequester,
            showClear = false,
            textStyle = Type.display.copy(textAlign = TextAlign.Center),
            name = name,
        )
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
