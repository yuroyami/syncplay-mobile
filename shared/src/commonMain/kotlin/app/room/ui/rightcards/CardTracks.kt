package app.room.ui.rightcards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.player.PlayerImpl
import app.player.models.Track
import app.room.ui.bottombar.SubtitleSearchModal
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.CheckGlyph
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.GroupHeading
import app.uicomponents.controls.Icon
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.Rule
import app.uicomponents.controls.Text
import app.uicomponents.controls.VerticalRule
import app.uicomponents.frames.PanelFrame
import app.utils.ccExs
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.action_close
import syncplaymobile.shared.generated.resources.room_audio_track_selected
import syncplaymobile.shared.generated.resources.room_button_desc_audio_tracks
import syncplaymobile.shared.generated.resources.room_button_desc_subtitle_tracks
import syncplaymobile.shared.generated.resources.room_button_desc_subtitle_tracks_import_from_file
import syncplaymobile.shared.generated.resources.room_sub_search_download_from_web
import syncplaymobile.shared.generated.resources.room_sub_track_disable
import syncplaymobile.shared.generated.resources.room_subtitle_track_selected
import syncplaymobile.shared.generated.resources.room_tracks_none
import syncplaymobile.shared.generated.resources.room_tracks_title

/**
 * Audio and subtitles side by side in the side dock: audio on the left, subtitles on the right
 * with the off row, the file import and the web search under them. Picking a track keeps the
 * panel open and re-reads the list, so the check mark follows the engine's real choice.
 */
object CardTracks {

    @Composable
    fun TracksPanel(shape: Shape) {
        val viewmodel = LocalRoomViewmodel.current
        val ui = LocalRoomUiState.current
        val scope = rememberCoroutineScope()
        val media = viewmodel.media
        val audio = media?.tracks?.filter { it.type == PlayerImpl.TrackType.AUDIO } ?: emptyList()
        val subtitles = media?.tracks?.filter { it.type == PlayerImpl.TrackType.SUBTITLE } ?: emptyList()
        var showSearch by remember { mutableStateOf(false) }

        // A side panel, not a modal: the picker can launch straight away (FileKit #575 is a modal race).
        val subtitlePicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = ccExs)) { file ->
            file?.let { scope.launch(Dispatchers.IO) { viewmodel.player.loadExternalSub(it) } }
        }

        fun choose(track: Track?, type: PlayerImpl.TrackType, notice: suspend () -> String) {
            Feedback.tick()
            viewmodel.viewModelScope.launch {
                viewmodel.player.selectTrack(track, type)
                viewmodel.media?.let { viewmodel.player.analyzeTracks(it) }
            }
            viewmodel.dispatchOSD(notice)
        }

        PanelFrame(
            title = stringResource(Res.string.room_tracks_title),
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            scrollable = false,
            actions = { GlyphButton(CloseGlyph, name = stringResource(Res.string.action_close)) { ui.toggleTracks(false) } },
        ) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    GroupHeading(stringResource(Res.string.room_button_desc_audio_tracks))
                    if (audio.isEmpty()) NoneLine()
                    audio.forEachIndexed { i, track ->
                        TrackRow(index = i + 1, track = track) {
                            choose(track, PlayerImpl.TrackType.AUDIO) { getString(Res.string.room_audio_track_selected, track.name) }
                        }
                    }
                }
                VerticalRule(Modifier.fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    GroupHeading(stringResource(Res.string.room_button_desc_subtitle_tracks))
                    ActionRow(Icons.Filled.ClosedCaptionDisabled, stringResource(Res.string.room_sub_track_disable), selected = subtitles.none { it.selected }) {
                        choose(null, PlayerImpl.TrackType.SUBTITLE) { getString(Res.string.room_sub_track_disable) }
                    }
                    subtitles.forEachIndexed { i, track ->
                        TrackRow(index = i + 1, track = track) {
                            choose(track, PlayerImpl.TrackType.SUBTITLE) { getString(Res.string.room_subtitle_track_selected, track.name) }
                        }
                    }
                    Rule()
                    ActionRow(Icons.AutoMirrored.Filled.NoteAdd, stringResource(Res.string.room_button_desc_subtitle_tracks_import_from_file)) {
                        Feedback.tick(); subtitlePicker.launch()
                    }
                    ActionRow(Icons.Filled.Search, stringResource(Res.string.room_sub_search_download_from_web)) {
                        Feedback.tick(); showSearch = true
                    }
                }
            }
        }

        SubtitleSearchModal(open = showSearch, onDismiss = { showSearch = false })
    }

    @Composable
    private fun NoneLine() {
        Text(
            text = stringResource(Res.string.room_tracks_none),
            style = Type.note,
            color = palette.inkDim,
            modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gapTight),
        )
    }

    @Composable
    private fun TrackRow(index: Int, track: Track, onClick: () -> Unit) {
        val p = palette
        ListRow(onClick = onClick, selected = track.selected) {
            RowValue("$index", width = 24.dp)
            RowGap(Space.gapTight)
            RowLabel(track.name)
            if (track.selected) {
                RowGap()
                Icon(CheckGlyph, contentDescription = null, tint = p.accent, modifier = Modifier.size(Space.glyph))
            }
        }
    }

    @Composable
    private fun ActionRow(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
        val p = palette
        ListRow(onClick = onClick, selected = selected) {
            Icon(icon, contentDescription = null, tint = if (selected) p.accent else p.inkDim, modifier = Modifier.size(Space.glyph))
            RowGap()
            RowLabel(label)
        }
    }
}
