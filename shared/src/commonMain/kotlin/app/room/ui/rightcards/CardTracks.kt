package app.room.ui.rightcards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Block
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.i18n.Localization
import app.i18n.strings
import app.player.PlayerImpl.TrackType
import app.player.models.Track
import app.player.models.TrackTrait
import app.player.models.channelBadge
import app.player.models.codecBadge
import app.player.models.trackLanguage
import app.preferences.Preferences.AUDIO_VISUALIZATION
import app.preferences.set
import app.preferences.watchPref
import app.room.ui.bottombar.SubtitleSearchModal
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.CheckGlyph
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.Icon
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.Rocker
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.Rule
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Segmented
import app.uicomponents.controls.Tag
import app.uicomponents.controls.Text
import app.uicomponents.frames.PanelFrame
import app.utils.ccExs
import app.utils.ioDispatcher
import app.utils.localizedLanguageName
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch

object CardTracks {
    @Composable
    fun TracksPanel(shape: Shape) {
        val viewmodel = LocalRoomViewmodel.current
        val ui = LocalRoomUiState.current
        val scope = rememberCoroutineScope()
        val media by viewmodel.playerManager.media.collectAsState()
        var showSearch by remember { mutableStateOf(false) }
        var selecting by remember { mutableStateOf(false) }
        val visualization by AUDIO_VISUALIZATION.watchPref()
        val subtitlePicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = ccExs)) { file ->
            file?.let {
                scope.launch(ioDispatcher) {
                    viewmodel.player.loadExternalSub(it)
                    viewmodel.media?.let { current -> viewmodel.player.analyzeTracks(current) }
                }
            }
        }
        PanelFrame(
            title = strings.roomTracksTitle, modifier = Modifier.fillMaxSize(), shape = shape,
            scrollable = false, centerTitle = true,
            actions = { GlyphButton(CloseGlyph, name = strings.actionClose) { ui.toggleTracks(false) } },
        ) {
            TrackControls(
                tracks = media?.tracks?.toList().orEmpty(),
                supportsVideo = viewmodel.player.supportsVideoTrackSelection,
                supportsVisualization = viewmodel.player.supportsAudioVisualization,
                visualization = visualization,
                onVisualization = { scope.launch { AUDIO_VISUALIZATION.set(it) } },
                enabled = !selecting,
                onChoose = { track, type ->
                    val current = media
                    if (!selecting && current != null) {
                        selecting = true
                        viewmodel.viewModelScope.launch {
                            try {
                                if (viewmodel.media !== current) return@launch
                                viewmodel.player.selectTrack(track, type)
                                if (viewmodel.media === current) {
                                    viewmodel.player.analyzeTracks(current)
                                    if (track != null && current.tracks.any { it.type == type && it.index == track.index && it.selected }) {
                                        when (type) {
                                            TrackType.AUDIO -> viewmodel.dispatchOSD { Localization.strings.roomAudioTrackSelected(track.name) }
                                            TrackType.SUBTITLE -> viewmodel.dispatchOSD { Localization.strings.roomSubtitleTrackSelected(track.name) }
                                            TrackType.VIDEO -> Unit
                                        }
                                    }
                                }
                            } finally { selecting = false }
                        }
                    }
                },
                onImport = { subtitlePicker.launch() },
                onSearch = { showSearch = true },
            )
        }
        SubtitleSearchModal(open = showSearch, onDismiss = { showSearch = false })
    }
}

/** One scrolling list gets the whole dock width; track types never compete for narrow columns. */
@Composable
internal fun TrackControls(
    tracks: List<Track>, supportsVideo: Boolean, supportsVisualization: Boolean,
    visualization: Boolean, onVisualization: (Boolean) -> Unit,
    onChoose: (Track?, TrackType) -> Unit, onImport: () -> Unit, onSearch: () -> Unit,
    enabled: Boolean = true, initialType: TrackType = TrackType.AUDIO,
) {
    val types = listOf(TrackType.AUDIO, TrackType.SUBTITLE) +
        if (supportsVideo || supportsVisualization) listOf(TrackType.VIDEO) else emptyList()
    var active by remember { mutableStateOf(initialType) }
    val selectedType = active.takeIf { it in types } ?: TrackType.AUDIO
    val labels = types.map { when (it) {
        TrackType.AUDIO -> strings.roomTrackTabAudio
        TrackType.SUBTITLE -> strings.roomTrackTabSubtitles
        TrackType.VIDEO -> strings.roomTrackTabVideo
    } }
    Column(Modifier.fillMaxSize()) {
        Segmented(labels, types.indexOf(selectedType), { active = types[it] },
            Modifier.fillMaxWidth().padding(Space.gapTight), autoSize = true)
        if (selectedType == TrackType.VIDEO && supportsVisualization) {
            // The visualizer only draws with the picture off, so it stays locked while a video track plays.
            val vizAvailable = tracks.none { it.type == TrackType.VIDEO && it.selected }
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gap, vertical = Space.gapTight),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(strings.uisettingKiteAudioVizTitle, style = Type.value,
                        color = if (vizAvailable) Color.Unspecified else palette.disabled)
                    Text(strings.roomVisualizationWhenVideoOff, style = Type.note,
                        color = if (vizAvailable) palette.inkDim else palette.disabled)
                }
                Rocker(visualization, onVisualization, enabled = vizAvailable, name = strings.uisettingKiteAudioVizTitle)
            }
        }
        if (selectedType == TrackType.SUBTITLE) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gapTight), horizontalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                SecondaryAction(strings.roomTrackImport, modifier = Modifier.weight(1f), onClick = onImport)
                SecondaryAction(strings.roomTrackSearch, modifier = Modifier.weight(1f), onClick = onSearch)
            }
        }
        Rule()
        val shown = tracks.filter { it.type == selectedType }
        val listState = key(selectedType) { rememberLazyListState() }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            if (selectedType == TrackType.SUBTITLE || selectedType == TrackType.VIDEO && supportsVideo) item {
                ListRow(onClick = { onChoose(null, selectedType) }, enabled = enabled,
                    selected = shown.none { it.selected }, minHeight = Space.row, horizontalPadding = Space.gap) {
                    Icon(Icons.Filled.Block, null, modifier = Modifier.size(20.dp))
                    RowGap(Space.gap)
                    Text(if (selectedType == TrackType.VIDEO) strings.roomVideoOff else strings.roomSubTrackDisable,
                        style = Type.value, modifier = Modifier.weight(1f))
                    if (shown.none { it.selected }) Icon(CheckGlyph, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                }
            }
            if (shown.isEmpty()) item {
                Text(strings.roomTracksNone, style = Type.note, color = palette.inkDim,
                    modifier = Modifier.padding(Space.gap))
            }
            itemsIndexed(shown) { _, track ->
                TrackRow(track, enabled) { onChoose(track, selectedType) }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, enabled: Boolean, onClick: () -> Unit) {
    val language = remember(track.language) { trackLanguage(track.language) }
    val languageName = if (language.code == "und") strings.roomTrackUnknownLanguage else
        localizedLanguageName(language.code, Localization.lyricist.state.value.languageTag)
            ?: language.fallbackName ?: language.code.uppercase()
    val channels = channelBadge(track.channelCount, track.channelLayout)
    val codec = codecBadge(track.codec)
    val traitLabel = when (track.trait) {
        TrackTrait.ACCESSIBILITY -> strings.roomTrackTraitAccessibility
        TrackTrait.FORCED -> strings.roomTrackTraitForced
        null -> null
    }
    val title = track.name.takeIf { it.isNotBlank() && it != track.language } ?: languageName
    ListRow(onClick = onClick, enabled = enabled, selected = track.selected,
        minHeight = 48.dp, horizontalPadding = Space.gap,
        modifier = Modifier.semantics { contentDescription = listOfNotNull(title, languageName.takeUnless { track.type == TrackType.VIDEO }, channels, track.videoDescription, traitLabel).joinToString(", ") }) {
        if (track.type == TrackType.VIDEO) Icon(Icons.Filled.Videocam, null, modifier = Modifier.size(24.dp))
        else Text(language.flag, style = Type.label, maxLines = 1, modifier = Modifier.width(28.dp))
        RowGap(Space.gapTight)
        Column(Modifier.weight(1f).padding(vertical = 5.dp)) {
            Text(title, style = Type.value, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.gapTight), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (track.type != TrackType.VIDEO && title != languageName) Text(languageName, style = Type.note, color = palette.inkDim)
                track.videoDescription?.let { Text(it, style = Type.note, color = palette.inkDim) }
                if (channels != null && track.type == TrackType.AUDIO) Tag(channels)
                if (codec != null && track.type != TrackType.SUBTITLE) Tag(codec)
                if (traitLabel != null) Tag(if (track.trait == TrackTrait.FORCED) traitLabel else
                    if (track.type == TrackType.AUDIO) "AD" else "SDH")
            }
        }
        if (track.selected) {
            RowGap(Space.gapTight)
            Icon(CheckGlyph, null, tint = palette.accent, modifier = Modifier.size(16.dp))
        }
    }
}
