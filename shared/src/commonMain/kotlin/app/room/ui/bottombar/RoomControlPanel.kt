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
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.MutableState
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
import app.utils.timestampFromMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_aspect_ratio
import syncplaymobile.shared.generated.resources.room_control_panel
import syncplaymobile.shared.generated.resources.room_gestures_panel_title
import syncplaymobile.shared.generated.resources.room_no_recent_seek
import syncplaymobile.shared.generated.resources.room_seek_to
import syncplaymobile.shared.generated.resources.room_seek_undone
import syncplaymobile.shared.generated.resources.room_tracks
import syncplaymobile.shared.generated.resources.room_undo_seek
import syncplaymobile.shared.generated.resources.room_undo_seek_always
import syncplaymobile.shared.generated.resources.room_undo_seek_cancel
import syncplaymobile.shared.generated.resources.room_undo_seek_confirm
import syncplaymobile.shared.generated.resources.room_undo_seek_message
import syncplaymobile.shared.generated.resources.room_undo_seek_title

/*
 * The control panel: a row of glyph buttons. The audio and subtitle panel lives in the side dock
 * (CardTracks); the subtitle search and the chapter list live in their own files.
 */

/** The entry glyph in the transport bar. */
@Composable
fun RoomControlPanelButton(modifier: Modifier, popupStateAddMedia: MutableState<Boolean>) {
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalRoomUiState.current
    val hasVideo by viewmodel.hasVideo.collectAsState()

    if (hasVideo) {
        GlyphButton(
            icon = Icons.Filled.VideoSettings,
            name = stringResource(Res.string.room_control_panel),
            size = Space.glyphLarge,
            modifier = modifier,
            onClick = {
                popupStateAddMedia.value = false
                cardController.controlPanel.value = !cardController.controlPanel.value
            },
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
        viewmodel.dispatchOSD { getString(Res.string.room_seek_undone) }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewmodel.player.canChangeAspectRatio) {
            GlyphButton(Icons.Filled.AspectRatio, name = stringResource(Res.string.room_aspect_ratio), size = Space.glyphLarge) {
                scope.launch(Dispatchers.IO) {
                    val label = viewmodel.player.switchAspectRatio()
                    viewmodel.dispatchOSD { label }
                }
            }
        }

        GlyphButton(Icons.Filled.BrowseGallery, name = stringResource(Res.string.room_seek_to), size = Space.glyphLarge) {
            cardController.toggleSeekTo()
        }

        /* Only the local user's seeks are undoable (see RoomCallback.onSomeoneSeeked). The key
         * carries the position an undo would return to, so there is no guessing before the tap. */
        val last = viewmodel.seeks.lastOrNull()
        UndoSeekKey(target = last?.first) {
            when {
                last == null -> viewmodel.dispatchOSD { getString(Res.string.room_no_recent_seek) }
                undoNoConfirm -> undo(last)
                else -> pendingUndoSeek = last
            }
        }

        /* Gesture switches live here, not in settings, so they can be flipped mid-playback. */
        GlyphButton(Icons.Filled.TouchApp, name = stringResource(Res.string.room_gestures_panel_title), size = Space.glyphLarge) {
            Feedback.tick()
            cardController.toggleGestures()
        }

        GlyphButton(Icons.Filled.Subtitles, name = stringResource(Res.string.room_tracks), size = Space.glyphLarge) {
            Feedback.tick()
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
    val name = stringResource(Res.string.room_undo_seek)
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(Space.touchMin)
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
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun UndoSeekModal(seek: Pair<Long, Long>?, onDismiss: () -> Unit, onUndo: (always: Boolean) -> Unit) {
    Modal(
        open = seek != null,
        onDismiss = onDismiss,
        title = stringResource(Res.string.room_undo_seek_title),
        size = ModalSize.Ask,
        actions = {
            SecondaryAction(stringResource(Res.string.room_undo_seek_always), onClick = { onUndo(true) })
            SecondaryAction(stringResource(Res.string.room_undo_seek_cancel), onClick = onDismiss)
            AccentAction(stringResource(Res.string.room_undo_seek_confirm), onClick = { onUndo(false) })
        },
    ) {
        if (seek != null) {
            Text(
                // second is where we are now, first is where the seek started.
                text = stringResource(Res.string.room_undo_seek_message, timestampFromMillis(seek.second), timestampFromMillis(seek.first)),
                style = Type.note,
                color = palette.inkDim,
            )
        }
    }
}
