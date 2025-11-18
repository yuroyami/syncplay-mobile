package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.managers.preferences.Preferences.GESTURES
import com.yuroyami.syncplay.managers.preferences.set
import com.yuroyami.syncplay.managers.preferences.watchPref
import com.yuroyami.syncplay.ui.components.FlexibleIcon
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.jostFont
import com.yuroyami.syncplay.ui.screens.adam.LocalCardController
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.screens.theme.Theming
import com.yuroyami.syncplay.utils.ccExs
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
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
import syncplaymobile.shared.generated.resources.room_chapters_skip
import syncplaymobile.shared.generated.resources.room_gestures_disabled
import syncplaymobile.shared.generated.resources.room_gestures_enabled
import syncplaymobile.shared.generated.resources.room_no_recent_seek
import syncplaymobile.shared.generated.resources.room_seek_undone
import syncplaymobile.shared.generated.resources.room_sub_track_disable

@Composable
fun RoomControlPanelButton(modifier: Modifier, popupStateAddMedia: MutableState<Boolean>) {
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalCardController.current

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

@Composable
fun RoomControlPanelCard(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalCardController.current

    val composeScope = rememberCoroutineScope()

    val subtitlePicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = ccExs)) { file ->
        file?.path?.let {
            scope.launch(Dispatchers.IO) {
                viewmodel.player.loadExternalSub(it)
            }
        }
    }

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
                viewmodel.osdManager.dispatchOSD { newAspectRatio }
            }
        }

        /* Seek Gesture (DoNotTouch for disabling it) */
        val gesturesEnabled by GESTURES.watchPref()

        FlexibleIcon(
            icon = when (gesturesEnabled) {
                true -> Icons.Filled.TouchApp
                false -> Icons.Filled.DoNotTouch
            }, size = iconSize, shadowColors = listOf(Color.Black)
        ) {
            composeScope.launch(Dispatchers.IO) {
                GESTURES.set(!gesturesEnabled)
                viewmodel.osdManager.dispatchOSD {
                    getString(if (gesturesEnabled) Res.string.room_gestures_enabled else Res.string.room_gestures_disabled)
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
            viewmodel.uiManager.popupSeekToPosition.value = true
        }

        /* Undo Last Seek */
        FlexibleIcon(
            icon = Icons.Filled.History,
            size = iconSize,
            shadowColors = listOf(Color.Black)
        ) {
            if (viewmodel.seeks.isEmpty()) {
                viewmodel.osdManager.dispatchOSD { getString(Res.string.room_no_recent_seek) }
                return@FlexibleIcon
            }

            cardController.controlPanel.value = false

            val lastSeek = viewmodel.seeks.lastOrNull() ?: return@FlexibleIcon
            scope.launch(Dispatchers.Main.immediate) {
                viewmodel.player.seekTo(lastSeek.first)
            }
            viewmodel.actionManager.sendSeek(lastSeek.first)
            viewmodel.seeks.remove(lastSeek)
            viewmodel.osdManager.dispatchOSD { getString(Res.string.room_seek_undone) }
        }

        /* Subtitle Tracks */
        val ccTracksPopup = remember { mutableStateOf(false) }
        ControlPanelDropdownButton(
            icon = Icons.Filled.Subtitles,
            onClick = {
                composeScope.launch {
                    viewmodel.player.analyzeTracks(viewmodel.media ?: return@launch)
                    ccTracksPopup.value = true
                }
            },
            popupVisibility = ccTracksPopup,
            popupTitle = stringResource(Res.string.room_button_desc_subtitle_tracks),
            popupItems = buildList {
                add(
                    ControlPanelDropdownAction(
                        text = stringResource(Res.string.room_button_desc_subtitle_tracks_import_from_file),
                        icon = Icons.AutoMirrored.Filled.NoteAdd,
                        action = {
                            subtitlePicker.launch()
                        }
                    )
                )
                add(
                    ControlPanelDropdownAction(
                        text = stringResource(Res.string.room_sub_track_disable),
                        icon = Icons.AutoMirrored.Filled.NoteAdd,
                        action = {
                            viewmodel.viewModelScope.launch {
                                viewmodel.player.selectTrack(null, BasePlayer.TRACKTYPE.SUBTITLE)
                            }
                        }
                    )
                )

                for (track in (viewmodel.media?.subtitleTracks) ?: listOf()) {
                    add(
                        ControlPanelDropdownAction(
                            text = track.name,
                            isChecked = track.selected.value,
                            action = {
                                viewmodel.viewModelScope.launch {
                                    viewmodel.player.selectTrack(track, BasePlayer.TRACKTYPE.SUBTITLE)
                                }
                            }
                        )
                    )
                }
            }
        )

        /* Audio Tracks */
        val audioTracksPopup = remember { mutableStateOf(false) }
        ControlPanelDropdownButton(
            icon = Icons.Filled.SpeakerGroup,
            onClick = {
                composeScope.launch {
                    viewmodel.player.analyzeTracks(viewmodel.media ?: return@launch)
                    audioTracksPopup.value = true
                }
            },
            popupVisibility = audioTracksPopup,
            popupTitle = stringResource(Res.string.room_button_desc_audio_tracks),
            popupItems = buildList {
                for (track in (viewmodel.media?.audioTracks) ?: listOf()) {
                    add(
                        ControlPanelDropdownAction(
                            text = track.name,
                            isChecked = track.selected.value,
                            action = {
                                viewmodel.viewModelScope.launch {
                                    viewmodel.player.selectTrack(track, BasePlayer.TRACKTYPE.AUDIO)
                                }
                            }
                        )
                    )
                }
            }
        )

        /* Chapters */
        if (viewmodel.player.supportsChapters) {
            val chaptersPopup = remember { mutableStateOf(false) }
            ControlPanelDropdownButton(
                icon = Icons.Filled.Theaters,
                onClick = {
                    composeScope.launch {
                        viewmodel.player.analyzeChapters(viewmodel.media ?: return@launch)
                        chaptersPopup.value = true
                    }
                },
                popupVisibility = chaptersPopup,
                popupTitle = stringResource(Res.string.room_chapters),
                popupItems = buildList {
                    add(
                        ControlPanelDropdownAction(
                            text = stringResource(Res.string.room_chapters_skip),
                            action = {
                                viewmodel.viewModelScope.launch {
                                    viewmodel.player.skipChapter()
                                }
                            }
                        )
                    )

                    for (chapter in (viewmodel.media?.chapters) ?: listOf()) {
                        add(
                            ControlPanelDropdownAction(
                                text = chapter.name,
                                action = {
                                    viewmodel.viewModelScope.launch {
                                        viewmodel.player.jumpToChapter(chapter)
                                    }
                                }
                            )
                        )
                    }
                }
            )
        }
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
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.8f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
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