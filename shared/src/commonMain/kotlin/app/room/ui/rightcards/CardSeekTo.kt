package app.room.ui.rightcards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.i18n.Localization
import app.i18n.strings
import app.preferences.Preferences.CUSTOM_SEEK_FRONT
import app.preferences.Preferences.CUSTOM_SEEK_AMOUNT
import app.preferences.set
import androidx.compose.runtime.rememberCoroutineScope
import app.uicomponents.controls.Rocker
import app.uicomponents.controls.Rule
import app.preferences.value
import app.preferences.watchPref
import app.room.RoomViewmodel
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.Field
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Text
import app.uicomponents.frames.PanelFrame
import app.utils.timestampFromMillis
import kotlinx.coroutines.launch

/**
 * Seek to a position, as a side panel over the video instead of a dialog. One timecode field:
 * digits shift in from the right like a microwave clock, so 1 2 3 4 reads 00:12:34. The field
 * sits at the top of the panel, above where the keyboard lands.
 */
object CardSeekTo {

    @Composable
    fun SeekToPanel(shape: Shape) {
        val viewmodel = LocalRoomViewmodel.current
        val ui = LocalRoomUiState.current
        val focusManager = LocalFocusManager.current
        var digits by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        val showShortcut by CUSTOM_SEEK_FRONT.watchPref()
        val customSkipAmount by CUSTOM_SEEK_AMOUNT.watchPref()
        val customSkipLabel = strings.roomCustomSkipButton(timestampFromMillis(customSkipAmount * 1000L))

        fun close() {
            focusManager.clearFocus(true)
            ui.toggleSeekTo(false)
        }
        fun commit() {
            val padded = digits.padStart(6, '0')
            val hh = padded.substring(0, 2).toLong()
            val mm = padded.substring(2, 4).toLong().coerceAtMost(59)
            val ss = padded.substring(4, 6).toLong().coerceAtMost(59)
            val result = ss * 1000 + mm * 60_000 + hh * 3_600_000
            close()
            // The one seek path: announce first so a rewind does not yank us back.
            viewmodel.dispatcher.seek(result)
            viewmodel.dispatchOSD { Localization.strings.roomSeekTopositionSuccess(timestampFromMillis(result)) }
        }

        PanelFrame(
            title = strings.roomSeekTopositionTitle,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            centerTitle = true,
            actions = { GlyphButton(CloseGlyph, name = strings.actionClose, onClick = ::close) },
        ) {
            SeekControls(
                value = format(digits), onValue = { digits = it.filter(Char::isDigit).takeLast(6) },
                canSeek = digits.isNotEmpty(), onSeek = ::commit,
                skipLabel = customSkipLabel, onSkip = { close(); viewmodel.customSkip() },
                showShortcut = showShortcut,
                onShowShortcut = { scope.launch { CUSTOM_SEEK_FRONT.set(it) } },
            )
        }
    }

    /** Empty stays empty (the placeholder shows); otherwise hh:mm:ss from the right-aligned digits. */
    private fun format(digits: String): String {
        if (digits.isEmpty()) return ""
        val padded = digits.padStart(6, '0')
        return padded.substring(0, 2) + ":" + padded.substring(2, 4) + ":" + padded.substring(4, 6)
    }
}

@Composable
internal fun SeekControls(
    value: String, onValue: (String) -> Unit, canSeek: Boolean, onSeek: () -> Unit,
    skipLabel: String, onSkip: () -> Unit, showShortcut: Boolean, onShowShortcut: (Boolean) -> Unit,
) {
    Column(Modifier.padding(Space.gap)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.gap)) {
            Field(value = value, onValueChange = onValue, modifier = Modifier.weight(1f),
                placeholder = "00:00:00", keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
                onImeAction = { if (canSeek) onSeek() }, showClear = false,
                textStyle = Type.label.copy(textAlign = TextAlign.Center), name = strings.roomSeekTopositionTitle)
            AccentAction(strings.roomSeekGo, enabled = canSeek, onClick = onSeek)
        }
        Text(strings.roomSeekTopositionHint, style = Type.note, color = palette.inkDim,
            modifier = Modifier.padding(vertical = Space.gapTight))
        Rule()
        Row(Modifier.fillMaxWidth().padding(top = Space.gap), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.gap)) {
            SecondaryAction(skipLabel, modifier = Modifier.weight(1f), onClick = onSkip)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strings.roomSeekShowButton, style = Type.note, color = palette.inkDim)
                Rocker(showShortcut, onShowShortcut, name = strings.roomSeekShowButton)
            }
        }
    }
}

fun RoomViewmodel.customSkip() {
    player.playerScopeMain.launch {
        val currentMs = player.currentPositionMs()
        val newPos = currentMs + CUSTOM_SEEK_AMOUNT.value() * 1000L
        dispatcher.seek(newPos, fromMs = currentMs)
        // The same notice as seek-to, so it gets the same timecode shape, not a bare count.
        dispatchOSD { Localization.strings.roomSeekTopositionSuccess(timestampFromMillis(newPos)) }
    }
}
