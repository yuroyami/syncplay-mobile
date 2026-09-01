package app.room.ui.bottombar

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewModelScope
import app.LocalRoomViewmodel
import app.player.PlayerImpl
import app.player.models.Track
import app.theme.Space
import app.theme.palette
import app.uicomponents.controls.CheckGlyph
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.GroupHeading
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.Rule
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_audio_track_selected
import syncplaymobile.shared.generated.resources.room_button_desc_audio_tracks
import syncplaymobile.shared.generated.resources.room_button_desc_subtitle_tracks
import syncplaymobile.shared.generated.resources.room_button_desc_subtitle_tracks_import_from_file
import syncplaymobile.shared.generated.resources.room_sub_search_download_from_web
import syncplaymobile.shared.generated.resources.room_sub_track_disable
import syncplaymobile.shared.generated.resources.room_subtitle_track_selected
import syncplaymobile.shared.generated.resources.room_tracks

/**
 * Audio and subtitle selection as two hairline lists in one modal. Selecting closes the modal
 * before the engine call runs, as before. Import and search are handed back to the caller so
 * the file picker can launch after this modal has dismissed.
 */
@Composable
fun TracksModal(open: Boolean, onDismiss: () -> Unit, onImportSubtitle: () -> Unit, onSearchOnline: () -> Unit) {
    if (!open) return
    val viewmodel = LocalRoomViewmodel.current
    val audio = viewmodel.media?.tracks?.filter { it.type == PlayerImpl.TrackType.AUDIO } ?: emptyList()
    val subtitles = viewmodel.media?.tracks?.filter { it.type == PlayerImpl.TrackType.SUBTITLE } ?: emptyList()

    fun choose(track: Track?, type: PlayerImpl.TrackType, notice: suspend () -> String) {
        Feedback.tick()
        onDismiss()
        viewmodel.viewModelScope.launch { viewmodel.player.selectTrack(track, type) }
        viewmodel.dispatchOSD(notice)
    }

    Modal(open = true, onDismiss = onDismiss, title = stringResource(Res.string.room_tracks), size = ModalSize.Panel, inset = false) {
        GroupHeading(stringResource(Res.string.room_button_desc_audio_tracks))
        audio.forEachIndexed { i, track ->
            TrackRow(index = i + 1, track = track) {
                choose(track, PlayerImpl.TrackType.AUDIO) { getString(Res.string.room_audio_track_selected, track.name) }
            }
        }

        Rule()
        GroupHeading(stringResource(Res.string.room_button_desc_subtitle_tracks))
        ActionRow(Icons.AutoMirrored.Filled.NoteAdd, stringResource(Res.string.room_button_desc_subtitle_tracks_import_from_file)) {
            Feedback.tick(); onImportSubtitle()
        }
        ActionRow(Icons.Filled.Search, stringResource(Res.string.room_sub_search_download_from_web)) {
            Feedback.tick(); onSearchOnline()
        }
        ActionRow(Icons.Filled.ClosedCaptionDisabled, stringResource(Res.string.room_sub_track_disable)) {
            choose(null, PlayerImpl.TrackType.SUBTITLE) { getString(Res.string.room_sub_track_disable) }
        }
        subtitles.forEachIndexed { i, track ->
            TrackRow(index = i + 1, track = track) {
                choose(track, PlayerImpl.TrackType.SUBTITLE) { getString(Res.string.room_subtitle_track_selected, track.name) }
            }
        }
    }
}

@Composable
private fun TrackRow(index: Int, track: Track, onClick: () -> Unit) {
    val p = palette
    ListRow(onClick = onClick, selected = track.selected) {
        RowValue("$index", width = 24.dpSafe)
        RowGap(Space.gapTight)
        RowLabel(track.name)
        if (track.selected) {
            RowGap()
            Icon(CheckGlyph, contentDescription = null, tint = p.accent, modifier = Modifier.size(Space.glyph))
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListRow(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = palette.inkDim, modifier = Modifier.size(Space.glyph))
        RowGap()
        RowLabel(label)
    }
}

private val Int.dpSafe get() = androidx.compose.ui.unit.Dp(this.toFloat())
