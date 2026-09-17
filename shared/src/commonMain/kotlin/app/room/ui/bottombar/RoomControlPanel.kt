package app.room.ui.bottombar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VideoSettings
import app.i18n.Localization
import app.i18n.strings
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import app.uicomponents.controls.pressFeedback
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.Icon
import app.theme.Radius
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import androidx.compose.foundation.layout.padding
import app.theme.Type
import app.theme.palette
import app.preferences.Preferences.UNDO_SEEK_NO_CONFIRM
import app.preferences.set
import app.preferences.watchPref
import app.theme.Space
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.ioDispatcher
import app.utils.timestampFromMillis
import kotlinx.coroutines.launch

/*
 * The control panel: a row of glyph buttons. The audio and subtitle panel lives in the side dock
 * (CardTracks); the subtitle search and the chapter list live in their own files.
 */

/** The entry glyph in the transport bar. */
@Composable
fun RoomControlPanelButton(modifier: Modifier) {
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalRoomUiState.current
    val hasVideo by viewmodel.hasVideo.collectAsState()

    if (hasVideo) {
        GlyphButton(
            icon = Icons.Filled.VideoSettings,
            name = strings.roomControlPanel,
            size = Space.glyphLarge,
            modifier = modifier,
            onClick = { cardController.toggleControlPanel() },
        )
    }
}

@Composable
fun RoomControlPanelCard(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalRoomUiState.current


    var pendingUndoSeek by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val undoNoConfirm by UNDO_SEEK_NO_CONFIRM.watchPref()

    fun undo(seek: Pair<Long, Long>) {
        cardController.controlPanel.value = false
        viewmodel.dispatcher.undoSeek(seek)
        viewmodel.dispatchOSD { Localization.strings.roomSeekUndone }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewmodel.player.canChangeAspectRatio) {
            GlyphButton(Icons.Filled.AspectRatio, name = strings.roomAspectRatio, size = Space.glyphLarge) {
                scope.launch(ioDispatcher) {
                    val label = viewmodel.player.switchAspectRatio()
                    if (label.isNotBlank()) viewmodel.dispatchOSD { label }
                }
            }
        }

        GlyphButton(Icons.Filled.BrowseGallery, name = strings.roomSeekTo, size = Space.glyphLarge) {
            cardController.toggleSeekTo()
        }

        /* Only the local user's seeks are undoable (see RoomCallback.onSomeoneSeeked). The key
         * carries the position an undo would return to, so there is no guessing before the tap. */
        val last = viewmodel.seeks.lastOrNull()
        UndoSeekKey(target = last?.first) {
            when {
                last == null -> viewmodel.dispatchWarning { Localization.strings.roomNoRecentSeek }
                undoNoConfirm -> undo(last)
                else -> pendingUndoSeek = last
            }
        }

        /* Gesture switches live here, not in settings, so they can be flipped mid-playback. */
        GlyphButton(Icons.Filled.TouchApp, name = strings.roomGesturesPanelTitle, size = Space.glyphLarge) {
            cardController.toggleGestures()
        }

        GlyphButton(Icons.Filled.Subtitles, name = strings.roomTracks, size = Space.glyphLarge) {
            if (cardController.tabCardTracks.value) {
                cardController.toggleTracks(false)
                return@GlyphButton
            }
            viewmodel.viewModelScope.launch {
                // The panel never opens without media; the engine needs one to list tracks.
                viewmodel.player.analyzeTracks(viewmodel.media ?: return@launch)
                cardController.toggleTracks(true)
            }
        }
    }

    UndoSeekModal(
        seek = pendingUndoSeek,
        onDismiss = { pendingUndoSeek = null },
        onUndo = { always ->
            val seek = pendingUndoSeek ?: return@UndoSeekModal
            pendingUndoSeek = null
            if (always) scope.launch { UNDO_SEEK_NO_CONFIRM.set(true) }
            undo(seek)
        },
    )
}

/** The undo glyph in a normal 48dp key, with the return timecode as a small badge under it. */
@Composable
private fun UndoSeekKey(target: Long?, onClick: () -> Unit) {
    val p = palette
    val name = strings.roomUndoSeek
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .height(Space.touchMin)
            .widthIn(min = Space.touchMin)
            .clip(Radius.controlShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { Feedback.tick(); onClick() }
            .hoverable(source)
            .semantics { contentDescription = name }
            .controlStates(source, Radius.controlShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            tint = p.ink,
            modifier = Modifier.size(Space.glyphLarge).offset(y = if (target != null) (-4).dp else 0.dp),
        )
        if (target != null) {
            Text(
                text = timestampFromMillis(target),
                style = Type.group,
                color = p.inkDim,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun UndoSeekModal(seek: Pair<Long, Long>?, onDismiss: () -> Unit, onUndo: (always: Boolean) -> Unit) {
    Modal(
        open = seek != null,
        onDismiss = onDismiss,
        title = strings.roomUndoSeekTitle,
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(strings.roomUndoSeekAlways, onClick = { onUndo(true) })
            SecondaryAction(strings.roomUndoSeekCancel, onClick = onDismiss)
            AccentAction(strings.roomUndoSeekConfirm, onClick = { onUndo(false) })
        },
    ) {
        if (seek != null) {
            Text(
                // second is where we are now, first is where the seek started.
                text = strings.roomUndoSeekMessage(timestampFromMillis(seek.second), timestampFromMillis(seek.first)),
                style = Type.note,
                color = palette.inkDim,
            )
        }
    }
}
