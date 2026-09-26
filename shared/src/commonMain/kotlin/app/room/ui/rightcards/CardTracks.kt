package app.room.ui.rightcards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import app.player.models.VisualizerControls
import app.player.models.channelBadge
import app.player.models.codecBadge
import app.player.models.trackLanguage
import app.preferences.Preferences.AUDIO_VISUALIZATION
import app.preferences.Preferences.SHOW_SETTING_DESCRIPTIONS
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
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.Rule
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Segmented
import app.uicomponents.controls.Stepper
import app.uicomponents.controls.Tag
import app.uicomponents.controls.Text
import app.uicomponents.frames.PanelSurface
import app.uicomponents.frames.ScrollbarHost
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
        val visualizer by viewmodel.player.visualizer.collectAsState()
        val subtitlePicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = ccExs)) { file ->
            file?.let {
                scope.launch(ioDispatcher) {
                    viewmodel.player.loadExternalSub(it)
                    viewmodel.media?.let { current -> viewmodel.player.analyzeTracks(current) }
                }
            }
        }
        fun choose(track: Track?, type: TrackType) {
            val current = media
            if (selecting || current == null) return
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
        PanelSurface(Modifier.fillMaxSize(), shape) {
            TrackControls(
                tracks = media?.tracks?.toList().orEmpty(),
                supportsVideo = viewmodel.player.supportsVideoTrackSelection,
                supportsVisualization = viewmodel.player.supportsAudioVisualization,
                visualization = visualization,
                visualizer = visualizer,
                onVisualization = { on ->
                    scope.launch { AUDIO_VISUALIZATION.set(on) }
                    // The visualizer draws in place of the picture, so turning it on turns the
                    // video off. Turning it off leaves the video off. A video track from the list
                    // brings the video back.
                    if (on && media?.tracks?.any { it.type == TrackType.VIDEO && it.selected } == true) choose(null, TrackType.VIDEO)
                },
                enabled = !selecting,
                onChoose = ::choose,
                onImport = { subtitlePicker.launch() },
                onSearch = { showSearch = true },
                // The panel leaves the composition when it closes, so the chosen tab lives in
                // RoomUiStateManager.
                initialType = ui.tracksTab.value,
                onTypeChange = { ui.tracksTab.value = it },
                onClose = { ui.toggleTracks(false) },
            )
        }
        SubtitleSearchModal(open = showSearch, onDismiss = { showSearch = false })
    }
}

/**
 * The track controls: tabs for the track types, and one scrolling list that gets the whole width
 * of the dock (the side area of the screen that holds the panels). So the track types never
 * compete for narrow columns.
 */
@Composable
internal fun TrackControls(
    tracks: List<Track>, supportsVideo: Boolean, supportsVisualization: Boolean,
    visualization: Boolean, onVisualization: (Boolean) -> Unit,
    onChoose: (Track?, TrackType) -> Unit, onImport: () -> Unit, onSearch: () -> Unit, onClose: () -> Unit,
    enabled: Boolean = true, initialType: TrackType = TrackType.AUDIO, visualizer: VisualizerControls? = null,
    onTypeChange: (TrackType) -> Unit = {},
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
        // No title row: the tabs name the panel, and the close key sits at the end, as in the
        // other panels.
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.gapTight), verticalAlignment = Alignment.CenterVertically) {
            Segmented(labels, types.indexOf(selectedType), { active = types[it]; onTypeChange(active) },
                Modifier.weight(1f).padding(vertical = Space.gapTight), autoSize = true)
            GlyphButton(CloseGlyph, name = strings.actionClose, onClick = onClose)
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
        ScrollbarHost(listState, Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                // The visualizer rows scroll with the list. A panel on a phone is short, and as a fixed
                // header, these rows would push the pattern stepper and the tracks out of the panel.
                if (selectedType == TrackType.VIDEO && supportsVisualization) item {
                    Column { VisualizerRows(tracks, visualization, onVisualization, visualizer) }
                }
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
}

/**
 * The visualizer rows on the video tab. The switch shows what is on screen, not the stored value.
 * While a video track is selected, the visualizer draws nothing, whatever the stored value says.
 * Turning the switch on turns the video off. The director and pattern rows show only while the
 * visualizer draws.
 */
@Composable
internal fun VisualizerRows(tracks: List<Track>, visualization: Boolean, onVisualization: (Boolean) -> Unit, visualizer: VisualizerControls?) {
    val drawing = visualization && tracks.none { it.type == TrackType.VIDEO && it.selected }
    SwitchRow(strings.uisettingKiteAudioVizTitle, strings.roomVisualizerSummary, drawing, onVisualization)
    if (drawing && visualizer != null) {
        SwitchRow(strings.roomVisualizerDirector, strings.roomVisualizerDirectorSummary, visualizer.directed) { visualizer.directed = it }
        // The stepper sits beside its label, as in a choice row of the settings screen. The label
        // keeps one line at its own width, and the stepper takes the rest. The minimum width of
        // the stepper gives way to the row, because the panel can be as narrow as 320dp.
        ListRow(horizontalPadding = Space.gap) {
            Text(strings.roomVisualizerPattern, style = Type.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = Space.valueCol))
            RowGap()
            Stepper(visualizer.drawings, visualizer.showing, visualizer::show, Modifier.weight(1f),
                wrap = true, autoSize = true, name = strings.roomVisualizerPattern)
        }
    }
}

/**
 * A switch row, drawn like one in the settings screen. A tap on the row toggles the switch, and
 * the value reads On or Off. The note shows under the row after a long press, or while the "Show
 * setting descriptions" setting is on.
 */
@Composable
private fun SwitchRow(title: String, note: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val showDescriptions by SHOW_SETTING_DESCRIPTIONS.watchPref()
    var explain by remember { mutableStateOf(false) }
    ListRow(onClick = { onChange(!on) }, onLongClick = { explain = !explain }, horizontalPadding = Space.gap) {
        RowLabel(title)
        RowGap()
        RowValue(if (on) strings.settingsValueOn else strings.settingsValueOff, accent = on, width = 36.dp)
        RowGap()
        // The row is the only toggleable node. A second node on the rocker would read as two
        // switches.
        Rocker(on = on, onChange = onChange, modifier = Modifier.clearAndSetSemantics { })
    }
    if (showDescriptions || explain) {
        Text(note, style = Type.note, color = palette.inkDim,
            modifier = Modifier.fillMaxWidth().offset(y = -Space.gapTight).padding(start = Space.gap, end = Space.gap, bottom = Space.gapTight))
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
