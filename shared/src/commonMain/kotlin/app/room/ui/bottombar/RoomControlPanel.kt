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
import androidx.compose.runtime.LaunchedEffect
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
import app.preferences.Preferences.DOUBLETAP_SEEK
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.ScrubTrack
import app.utils.platformCallback
import syncplaymobile.shared.generated.resources.room_brightness
import syncplaymobile.shared.generated.resources.room_volume
import kotlin.math.roundToInt
import app.preferences.Preferences.SWIPE_GESTURES
import app.preferences.Preferences.UNDO_SEEK_NO_CONFIRM
import app.preferences.set
import app.preferences.settings.SettingRow
import app.preferences.watchPref
import app.theme.Space
import app.uicomponents.controls.AccentAction
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.utils.ccExs
import app.utils.timestampFromMillis
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
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
 * The control panel: a row of glyph buttons, each opening one modal. The audio and subtitle
 * list, the subtitle search and the chapter list live in their own files.
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

    val subtitlePicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = ccExs)) { file ->
        file?.let { scope.launch(Dispatchers.IO) { viewmodel.player.loadExternalSub(it) } }
    }

    var showTracks by remember { mutableStateOf(false) }
    var showSubtitleSearch by remember { mutableStateOf(false) }
    var showGestures by remember { mutableStateOf(false) }

    // iOS FileKit picker race (FileKit #575): launching a picker while a modal is closing fires
    // the native delegate twice ("Already resumed"). Dismiss first, launch once it has settled.
    var launchSubtitlePickerAfterDismiss by remember { mutableStateOf(false) }
    LaunchedEffect(showTracks, launchSubtitlePickerAfterDismiss) {
        if (!showTracks && launchSubtitlePickerAfterDismiss) {
            launchSubtitlePickerAfterDismiss = false
            subtitlePicker.launch()
        }
    }

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
        GlyphButton(Icons.Filled.AspectRatio, name = stringResource(Res.string.room_aspect_ratio), size = Space.glyphLarge) {
            scope.launch(Dispatchers.IO) {
                val label = viewmodel.player.switchAspectRatio()
                viewmodel.dispatchOSD { label }
            }
        }

        GlyphButton(Icons.Filled.BrowseGallery, name = stringResource(Res.string.room_seek_to), size = Space.glyphLarge) {
            cardController.controlPanel.value = false
            viewmodel.uiState.popupSeekToPosition.value = true
        }

        /* Only the local user's seeks are undoable (see RoomCallback.onSomeoneSeeked). */
        GlyphButton(Icons.Filled.History, name = stringResource(Res.string.room_undo_seek), size = Space.glyphLarge) {
            val last = viewmodel.seeks.lastOrNull()
            when {
                last == null -> viewmodel.dispatchOSD { getString(Res.string.room_no_recent_seek) }
                undoNoConfirm -> undo(last)
                else -> pendingUndoSeek = last
            }
        }

        /* Gesture switches live here, not in settings, so they can be flipped mid-playback. */
        GlyphButton(Icons.Filled.TouchApp, name = stringResource(Res.string.room_gestures_panel_title), size = Space.glyphLarge) {
            Feedback.tick()
            showGestures = true
        }

        GlyphButton(Icons.Filled.Subtitles, name = stringResource(Res.string.room_tracks), size = Space.glyphLarge) {
            Feedback.tick()
            viewmodel.viewModelScope.launch {
                // The list never opens without media; the engine needs one to list tracks.
                viewmodel.player.analyzeTracks(viewmodel.media ?: return@launch)
                showTracks = true
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

    Modal(
        open = showGestures,
        onDismiss = { showGestures = false },
        title = stringResource(Res.string.room_gestures_panel_title),
        size = ModalSize.Panel,
        inset = false,
    ) {
        DOUBLETAP_SEEK.SettingRow()
        SWIPE_GESTURES.SettingRow()
        // The swipes' equivalents, for anyone who cannot swipe: two tracks that set the same values.
        val maxVolume = viewmodel.player.getMaxVolume().coerceAtLeast(1)
        var volume by remember { mutableIntStateOf(viewmodel.player.getCurrentVolume()) }
        var brightness by remember { mutableFloatStateOf(platformCallback.getCurrentBrightness()) }
        LevelRow(stringResource(Res.string.room_volume), volume.toFloat() / maxVolume, "${volume * 100 / maxVolume}%") { f ->
            volume = (f * maxVolume).roundToInt()
            viewmodel.player.changeCurrentVolume(volume)
        }
        LevelRow(stringResource(Res.string.room_brightness), brightness, "${(brightness * 100).roundToInt()}%") { f ->
            brightness = f
            platformCallback.changeCurrentBrightness(f)
        }
    }

    TracksModal(
        open = showTracks,
        onDismiss = { showTracks = false },
        onImportSubtitle = {
            launchSubtitlePickerAfterDismiss = true
            showTracks = false
        },
        onSearchOnline = {
            showTracks = false
            showSubtitleSearch = true
        },
    )

    SubtitleSearchModal(open = showSubtitleSearch, onDismiss = { showSubtitleSearch = false })
}

@Composable
private fun LevelRow(label: String, value: Float, shown: String, onValue: (Float) -> Unit) {
    val p = palette
    Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.gapTight)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.label, color = p.ink, modifier = Modifier.weight(1f))
            Text(shown, style = Type.value, color = p.inkDim)
        }
        ScrubTrack(value = value.coerceIn(0f, 1f), onValueChange = onValue, describe = { "${(it * 100).roundToInt()} percent" }, name = label)
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
