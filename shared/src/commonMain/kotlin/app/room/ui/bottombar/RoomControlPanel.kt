package app.room.ui.bottombar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import app.LocalRoomUiState
import app.LocalRoomViewmodel
import app.player.PlayerImpl
import app.subtitles.SubtitleSearch
import syncplaymobile.shared.generated.resources.room_sub_search_download_from_web
import syncplaymobile.shared.generated.resources.room_sub_search_downloads
import syncplaymobile.shared.generated.resources.room_sub_search_hint
import syncplaymobile.shared.generated.resources.room_sub_search_no_results
import syncplaymobile.shared.generated.resources.room_sub_search_title
import app.theme.Theming.flexibleGradient
import app.uicomponents.FlexibleIcon
import app.uicomponents.FlexibleText
import app.uicomponents.jostFont
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
import syncplaymobile.shared.generated.resources.room_button_desc_audio_tracks
import syncplaymobile.shared.generated.resources.room_button_desc_subtitle_tracks
import syncplaymobile.shared.generated.resources.room_button_desc_subtitle_tracks_import_from_file
import syncplaymobile.shared.generated.resources.room_chapters
import syncplaymobile.shared.generated.resources.room_chapters_jump
import syncplaymobile.shared.generated.resources.room_chapters_skip
import syncplaymobile.shared.generated.resources.room_no_recent_seek
import syncplaymobile.shared.generated.resources.room_screenshot_saved
import syncplaymobile.shared.generated.resources.room_screenshot_unsupported
import syncplaymobile.shared.generated.resources.room_seek_undone
import syncplaymobile.shared.generated.resources.room_sub_track_disable
import syncplaymobile.shared.generated.resources.room_tracks_title

@Composable
fun RoomControlPanelButton(modifier: Modifier, popupStateAddMedia: MutableState<Boolean>) {
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalRoomUiState.current

    val hasVideo by viewmodel.hasVideo.collectAsState()

    if (hasVideo) {
        FlexibleIcon(
            modifier = modifier,
            icon = Icons.Filled.VideoSettings,
            size = 47,
            shadowColors = listOf(Color.Black),
            onClick = {
                popupStateAddMedia.value = false
                cardController.controlPanel.value = !cardController.controlPanel.value
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomControlPanelCard(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalRoomUiState.current
    val hapticFeedback = LocalHapticFeedback.current

    fun haptic() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
    }

    val composeScope = rememberCoroutineScope()

    val subtitlePicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = ccExs)) { file ->
        file?.let {
            scope.launch(Dispatchers.IO) {
                viewmodel.player.loadExternalSub(it)
            }
        }
    }

    /* Track sheet state */
    var showTrackSheet by remember { mutableStateOf(false) }
    var showSubtitleSearch by remember { mutableStateOf(false) }

    val iconSize = 44
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        /* Aspect Ratio */
        FlexibleIcon(
            icon = Icons.Filled.AspectRatio,
            size = iconSize,
            shadowColors = listOf(Color.Black)
        ) {
            composeScope.launch(Dispatchers.IO) {
                val newAspectRatio = viewmodel.player.switchAspectRatio()
                viewmodel.dispatchOSD { newAspectRatio }
            }
        }

        /* Screenshot - hidden until properly implemented */
        if (false) {
            FlexibleIcon(
                icon = Icons.Filled.CameraAlt,
                size = iconSize, shadowColors = listOf(Color.Black)
            ) {
                composeScope.launch(Dispatchers.IO) {
                    val success = viewmodel.player.takeScreenshot()
                    viewmodel.dispatchOSD {
                        getString(if (success) Res.string.room_screenshot_saved else Res.string.room_screenshot_unsupported)
                    }
                }
            }
        }

        /* Seek To */
        FlexibleIcon(
            icon = Icons.Filled.BrowseGallery,
            size = iconSize,
            shadowColors = listOf(Color.Black)
        ) {
            cardController.controlPanel.value = false
            viewmodel.uiState.popupSeekToPosition.value = true
        }

        /* Undo Last Seek */
        FlexibleIcon(
            icon = Icons.Filled.History,
            size = iconSize,
            shadowColors = listOf(Color.Black)
        ) {
            if (viewmodel.seeks.isEmpty()) {
                viewmodel.dispatchOSD { getString(Res.string.room_no_recent_seek) }
                return@FlexibleIcon
            }

            cardController.controlPanel.value = false

            val lastSeek = viewmodel.seeks.lastOrNull() ?: return@FlexibleIcon
            scope.launch(Dispatchers.Main.immediate) {
                viewmodel.player.seekTo(lastSeek.first)
            }
            viewmodel.dispatcher.sendSeek(lastSeek.first)
            viewmodel.seeks.remove(lastSeek)
            viewmodel.dispatchOSD { getString(Res.string.room_seek_undone) }
        }

        /* Unified Audio & Subtitles button */
        FlexibleIcon(
            icon = Icons.Filled.Subtitles,
            size = iconSize,
            shadowColors = listOf(Color.Black)
        ) {
            haptic()
            viewmodel.viewModelScope.launch {
                viewmodel.player.analyzeTracks(viewmodel.media ?: return@launch)
                showTrackSheet = true
            }
        }

        /* Chapters */
        if (viewmodel.player.supportsChapters && viewmodel.media?.chapters?.isNotEmpty() == true) {
            val chaptersPopup = remember { mutableStateOf(false) }
            val skipStr = stringResource(Res.string.room_chapters_skip)
            val items by remember(chaptersPopup.value) {
                mutableStateOf(
                    buildList {
                        add(
                            ControlPanelDropdownAction(
                                text = skipStr,
                                icon = Icons.AutoMirrored.Filled.KeyboardTab,
                                action = {
                                    haptic()

                                    viewmodel.viewModelScope.launch {
                                        viewmodel.player.skipChapter()
                                        viewmodel.dispatchOSD {
                                            getString(Res.string.room_chapters_skip)
                                        }
                                    }
                                }
                            )
                        )

                        for (chapter in (viewmodel.media?.chapters) ?: listOf()) {
                            add(
                                ControlPanelDropdownAction(
                                    text = "${chapter.name} [${timestampFromMillis(chapter.timeOffsetMillis)}]",
                                    icon = Icons.Filled.Theaters,
                                    action = {
                                        haptic()

                                        viewmodel.viewModelScope.launch {
                                            viewmodel.player.jumpToChapter(chapter)
                                            viewmodel.dispatchOSD {
                                                getString(Res.string.room_chapters_jump, chapter.name)
                                            }
                                        }
                                    }
                                )
                            )
                        }
                    }
                )
            }
            ControlPanelDropdownButton(
                icon = Icons.Filled.Theaters,
                onClick = {
                    haptic()

                    viewmodel.viewModelScope.launch {
                        viewmodel.player.analyzeChapters(viewmodel.media ?: return@launch)
                        chaptersPopup.value = true
                    }
                },
                popupVisibility = chaptersPopup,
                popupTitle = stringResource(Res.string.room_chapters),
                popupItems = items
            )
        }
    }

    /* ===== Audio & Subtitles Bottom Sheet ===== */
    if (showTrackSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val audioTracks = viewmodel.media?.tracks?.filter { it.type == PlayerImpl.TrackType.AUDIO } ?: emptyList()
        val subtitleTracks = viewmodel.media?.tracks?.filter { it.type == PlayerImpl.TrackType.SUBTITLE } ?: emptyList()

        ModalBottomSheet(
            onDismissRequest = { showTrackSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            /* Title */
            Text(
                text = stringResource(Res.string.room_tracks_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)
            )

            /* Split view: Audio (left) | Subtitles (right) */
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(320.dp)
            ) {
                /* Audio column */
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(Res.string.room_button_desc_audio_tracks),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(audioTracks) { i, track ->
                            TrackRow(
                                label = "${i + 1}. ${track.name}",
                                selected = track.selected,
                                onClick = {
                                    haptic()
                                    showTrackSheet = false
                                    viewmodel.viewModelScope.launch {
                                        viewmodel.player.selectTrack(track, PlayerImpl.TrackType.AUDIO)
                                    }
                                    viewmodel.dispatchOSD { "Audio: ${track.name}" }
                                }
                            )
                        }
                    }
                }

                /* Divider */
                androidx.compose.material3.VerticalDivider(modifier = Modifier.padding(vertical = 8.dp))

                /* Subtitle column */
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Subtitles, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(Res.string.room_button_desc_subtitle_tracks),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        /* Import from file */
                        item {
                            TrackRow(
                                label = stringResource(Res.string.room_button_desc_subtitle_tracks_import_from_file),
                                icon = Icons.AutoMirrored.Filled.NoteAdd,
                                onClick = {
                                    haptic()
                                    showTrackSheet = false
                                    subtitlePicker.launch()
                                }
                            )
                        }
                        /* Search online */
                        item {
                            TrackRow(
                                label = stringResource(Res.string.room_sub_search_download_from_web),
                                icon = Icons.Filled.Search,
                                onClick = {
                                    haptic()
                                    showTrackSheet = false
                                    showSubtitleSearch = true
                                }
                            )
                        }
                        /* Disable subtitles */
                        item {
                            TrackRow(
                                label = stringResource(Res.string.room_sub_track_disable),
                                icon = Icons.Filled.ClosedCaptionDisabled,
                                onClick = {
                                    haptic()
                                    showTrackSheet = false
                                    viewmodel.viewModelScope.launch {
                                        viewmodel.player.selectTrack(null, PlayerImpl.TrackType.SUBTITLE)
                                    }
                                    viewmodel.dispatchOSD {
                                        getString(Res.string.room_sub_track_disable)
                                    }
                                }
                            )
                        }
                        /* Available subtitle tracks */
                        itemsIndexed(subtitleTracks) { i, track ->
                            TrackRow(
                                label = "${i + 1}. ${track.name}",
                                selected = track.selected,
                                onClick = {
                                    haptic()
                                    showTrackSheet = false
                                    viewmodel.viewModelScope.launch {
                                        viewmodel.player.selectTrack(track, PlayerImpl.TrackType.SUBTITLE)
                                    }
                                    viewmodel.dispatchOSD { "Subtitle: ${track.name}" }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    /* ===== Subtitle Search Bottom Sheet ===== */
    if (showSubtitleSearch) {
        val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val initialQuery = remember {
            viewmodel.media?.fileName?.let { SubtitleSearch.cleanMediaName(it) } ?: ""
        }
        var searchQuery by remember { mutableStateOf(initialQuery) }
        var searchResults by remember { mutableStateOf<List<app.subtitles.SubtitleResult>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }
        var isDownloading by remember { mutableStateOf<Int?>(null) }

        fun doSearch() {
            if (searchQuery.isBlank()) return
            scope.launch(Dispatchers.IO) {
                isSearching = true
                searchResults = SubtitleSearch.search(searchQuery)
                isSearching = false
            }
        }

        /* Auto-search on open */
        LaunchedEffect(Unit) {
            if (initialQuery.isNotBlank()) {
                isSearching = true
                searchResults = SubtitleSearch.search(initialQuery)
                isSearching = false
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showSubtitleSearch = false },
            sheetState = searchSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(400.dp)
            ) {
                Text(
                    text = stringResource(Res.string.room_sub_search_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.room_sub_search_hint), fontSize = 12.sp) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Search, null,
                            modifier = Modifier.clickable { doSearch() }
                        )
                    },
                    keyboardActions = KeyboardActions(onDone = { doSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                when {
                    isSearching -> {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    searchResults.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.room_sub_search_no_results),
                                color = Color.Gray, fontSize = 12.sp
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            itemsIndexed(searchResults) { _, result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = isDownloading == null) {
                                            isDownloading = result.fileId
                                            scope.launch(Dispatchers.IO) {
                                                val path = SubtitleSearch.download(result.fileId)
                                                if (path != null) {
                                                    val filename = path.substringAfterLast('/')
                                                    viewmodel.player.loadSubtitleFromPath(path, filename)
                                                }
                                                isDownloading = null
                                                showSubtitleSearch = false
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = CenterVertically
                                ) {
                                    if (isDownloading == result.fileId) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Download, null,
                                            modifier = Modifier.padding(end = 8.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.releaseInfo.ifBlank { result.filename },
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row {
                                            Text(
                                                text = "${result.language.uppercase()} · ${result.downloadCount} ${stringResource(Res.string.room_sub_search_downloads)}",
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                            if (result.hearingImpaired) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Filled.HearingDisabled, null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TrackRow(
    label: String,
    selected: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.padding(end = 8.dp))
        } else if (selected) {
            Icon(
                Icons.Filled.Check, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
        } else {
            Spacer(Modifier.width(32.dp))
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

data class ControlPanelDropdownAction(
    val text: String,
    val icon: ImageVector? = null,
    val isChecked: Boolean? = null,
    val action: () -> Unit
)

@Composable
fun ControlPanelDropdownButton(
    icon: ImageVector,
    onClick: () -> Unit = {},
    popupTitle: String,
    popupVisibility: MutableState<Boolean>,
    popupItems: List<ControlPanelDropdownAction>
) {
    Box {
        FlexibleIcon(
            icon = icon,
            size = 44,
            shadowColors = listOf(Color.Black),
            onClick = {
                onClick()
                popupVisibility.value = true
            }
        )

        DropdownMenu(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(width = Dp.Hairline, brush = Brush.linearGradient(colors = flexibleGradient)),
            shape = RoundedCornerShape(8.dp),
            expanded = popupVisibility.value,
            properties = PopupProperties(
                dismissOnBackPress = true, focusable = true, dismissOnClickOutside = true
            ),
            onDismissRequest = { popupVisibility.value = false }
        ) {
            FlexibleText(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = popupTitle,
                strokeColors = listOf(Color.Black),
                size = 13f,
                font = jostFont
            )

            popupItems.forEach { item ->
                DropdownMenuItem(
                    leadingIcon = {
                        if (item.icon != null) {
                            Icon(item.icon, null)
                        }
                        if (item.isChecked != null) {
                            Checkbox(
                                checked = item.isChecked,
                                onCheckedChange = {
                                    item.action()
                                    popupVisibility.value = false
                                }
                            )
                        }
                    },
                    text = {
                        Row(verticalAlignment = CenterVertically) {
                            Text(
                                color = Color.LightGray,
                                text = item.text
                            )
                        }
                    }, onClick = {
                        item.action()
                        popupVisibility.value = false
                    }
                )
            }
        }
    }
}
