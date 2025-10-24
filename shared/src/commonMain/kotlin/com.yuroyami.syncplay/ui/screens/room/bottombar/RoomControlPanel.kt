package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.MISC_GESTURES
import com.yuroyami.syncplay.managers.datastore.valueFlow
import com.yuroyami.syncplay.managers.datastore.writeValue
import com.yuroyami.syncplay.managers.player.BasePlayer
import com.yuroyami.syncplay.ui.screens.adam.LocalCardController
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.utils.FancyIcon2
import com.yuroyami.syncplay.ui.utils.FancyText2
import com.yuroyami.syncplay.ui.utils.syncplayFont
import com.yuroyami.syncplay.utils.ccExs
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Composable
fun RoomControlPanelButton(modifier: Modifier, popupStateAddMedia: MutableState<Boolean>) {
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalCardController.current

    val hasVideo by viewmodel.hasVideo.collectAsState()

    if (hasVideo) {
        FancyIcon2(
            modifier = modifier,
            icon = Icons.Filled.VideoSettings,
            size = 47,
            shadowColor = Color.Black,
            onClick = {
                popupStateAddMedia.value = false
                cardController.controlPanel.value = !cardController.controlPanel.value
            }
        )
    }
}

@Composable
fun RoomControlPanelCard(modifier: Modifier, height: Dp) {
    val scope = rememberCoroutineScope()
    val viewmodel = LocalRoomViewmodel.current
    val cardController = LocalCardController.current

    val composeScope = rememberCoroutineScope()

    val subtitlePicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = ccExs)) { file ->
        file?.path?.let {
            scope.launch(Dispatchers.IO) {
                viewmodel.player?.loadExternalSub(it)
            }
        }
    }

    val iconSize = (height.value - 2).toInt()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        /* Aspect Ratio */
        FancyIcon2(
            icon = Icons.Filled.AspectRatio,
            size = iconSize,
            shadowColor = Color.Black
        ) {
            composeScope.launch(Dispatchers.IO) {
                val newAspectRatio = viewmodel.player?.switchAspectRatio()
                if (newAspectRatio != null) {
                    viewmodel.osdManager.dispatchOSD { newAspectRatio }
                }
            }
        }

        /* Seek Gesture (DoNotTouch for disabling it) */
        val gesturesEnabled by valueFlow(MISC_GESTURES, true).collectAsState(initial = true)

        FancyIcon2(
            icon = when (gesturesEnabled) {
                true -> Icons.Filled.TouchApp
                false -> Icons.Filled.DoNotTouch
            }, size = iconSize, shadowColor = Color.Black
        ) {
            composeScope.launch(Dispatchers.IO) {
                writeValue(MISC_GESTURES, !gesturesEnabled)
                viewmodel.osdManager.dispatchOSD { if (gesturesEnabled) "Gestures enabled" else "Gestures disabled" } //TODO Localize
            }
        }

        /* Seek To */
        FancyIcon2(
            icon = Icons.Filled.BrowseGallery,
            size = iconSize,
            shadowColor = Color.Black
        ) {
            cardController.controlPanel.value = false
            //TODO seektopopupstate.value = true
        }

        /* Undo Last Seek */
        FancyIcon2(
            icon = Icons.Filled.History,
            size = iconSize,
            shadowColor = Color.Black
        ) {
            if (viewmodel.seeks.isEmpty()) {
                viewmodel.osdManager.dispatchOSD { "There is no recent seek in the room." }
                return@FancyIcon2
            }

            cardController.controlPanel.value = false

            val lastSeek = viewmodel.seeks.lastOrNull() ?: return@FancyIcon2
            scope.launch(Dispatchers.Main.immediate) {
                viewmodel.player?.seekTo(lastSeek.first)
            }
            viewmodel.actionManager.sendSeek(lastSeek.first)
            viewmodel.seeks.remove(lastSeek)
            viewmodel.osdManager.dispatchOSD { "Seek undone." }
        }

        /* Subtitle Tracks */
        Box {
            val tracksPopup = remember { mutableStateOf(false) }

            FancyIcon2(
                icon = Icons.Filled.Subtitles,
                size = iconSize,
                shadowColor = Color.Black
            ) {
                composeScope.launch {
                    viewmodel.player?.analyzeTracks(viewmodel.media ?: return@launch)
                    tracksPopup.value = true
                }
            }

            DropdownMenu(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(
                    0.5f
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map {
                        it.copy(alpha = 0.5f)
                    })
                ),
                shape = RoundedCornerShape(8.dp),
                expanded = tracksPopup.value,
                properties = PopupProperties(
                    dismissOnBackPress = true,
                    focusable = true,
                    dismissOnClickOutside = true
                ),
                onDismissRequest = {
                    tracksPopup.value = !tracksPopup.value
                }) {

                FancyText2(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    string = "Subtitle Track",
                    solid = Color.Black,
                    size = 14f,
                    font = syncplayFont
                )

                DropdownMenuItem(text = {
                    Row(verticalAlignment = CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                            "",
                            tint = Color.LightGray
                        )

                        Spacer(Modifier.width(2.dp))

                        Text(
                            "Import from file",
                            color = Color.LightGray
                        )
                    }
                }, onClick = {
                    tracksPopup.value = false
                    cardController.controlPanel.value = false

                    subtitlePicker.launch()
                })


                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.SubtitlesOff,
                                "",
                                tint = Color.LightGray
                            )

                            Spacer(Modifier.width(2.dp))

                            Text(
                                "Disable subtitles.",
                                color = Color.LightGray
                            )

                        }
                    },
                    onClick = {
                        scope.launch(Dispatchers.Main.immediate) {
                            viewmodel.player?.selectTrack(null, BasePlayer.TRACKTYPE.SUBTITLE)
                            tracksPopup.value = false
                            cardController.controlPanel.value = false
                        }
                    }
                )

                for (track in (viewmodel.media?.subtitleTracks)
                    ?: listOf()) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = CenterVertically) {
                                Checkbox(
                                    checked = track.selected.value,
                                    onCheckedChange = {
                                        scope.launch(Dispatchers.Main.immediate) {
                                            viewmodel.player?.selectTrack(track, BasePlayer.TRACKTYPE.SUBTITLE)
                                            tracksPopup.value = false
                                        }
                                    })

                                Text(
                                    color = Color.LightGray,
                                    text = track.name
                                )
                            }
                        },
                        onClick = {
                            scope.launch(Dispatchers.Main.immediate) {
                                viewmodel.player?.selectTrack(track, BasePlayer.TRACKTYPE.SUBTITLE)
                                tracksPopup.value = false
                                cardController.controlPanel.value = false
                            }
                        }
                    )
                }
            }
        }

        /* Audio Tracks */
        Box {
            val tracksPopup = remember { mutableStateOf(false) }

            FancyIcon2(
                icon = Icons.Filled.SpeakerGroup,
                size = iconSize,
                shadowColor = Color.Black
            ) {
                viewmodel.viewModelScope.launch {
                    viewmodel.player?.analyzeTracks(
                        viewmodel.media ?: return@launch
                    )
                    tracksPopup.value = true
                }
            }

            DropdownMenu(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(
                    0.5f
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map {
                        it.copy(alpha = 0.5f)
                    })
                ),
                shape = RoundedCornerShape(8.dp),
                expanded = tracksPopup.value,
                properties = PopupProperties(
                    dismissOnBackPress = true,
                    focusable = true,
                    dismissOnClickOutside = true
                ),
                onDismissRequest = {
                    tracksPopup.value = !tracksPopup.value
                }) {

                FancyText2(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    string = "Audio Track",
                    solid = Color.Black,
                    size = 14f,
                    font = syncplayFont
                )

                for (track in (viewmodel.media?.audioTracks
                    ?: listOf())) {
                    DropdownMenuItem(text = {
                        Row(verticalAlignment = CenterVertically) {
                            Checkbox(
                                checked = track.selected.value,
                                onCheckedChange = {
                                    scope.launch(Dispatchers.Main.immediate) {
                                        viewmodel.player?.selectTrack(track, BasePlayer.TRACKTYPE.AUDIO)
                                        tracksPopup.value = false
                                    }
                                })

                            Text(
                                color = Color.LightGray,
                                text = track.name
                            )
                        }
                    }, onClick = {
                        scope.launch(Dispatchers.Main.immediate) {
                            viewmodel.player?.selectTrack(track, BasePlayer.TRACKTYPE.AUDIO)
                            tracksPopup.value = false
                        }
                    })
                }
            }
        }


        /* Chapters */
        if (viewmodel.player?.supportsChapters == true) {
            Box {
                var chaptersPopup by remember { mutableStateOf(false) }

                FancyIcon2(
                    icon = Icons.Filled.Theaters,
                    size = iconSize,
                    shadowColor = Color.Black
                ) {
                    viewmodel.viewModelScope.launch {
                        viewmodel.player?.analyzeChapters(
                            viewmodel.media ?: return@launch
                        )
                        chaptersPopup = !chaptersPopup
                    }
                }

                DropdownMenu(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(
                        0.5f
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map {
                            it.copy(alpha = 0.5f)
                        })
                    ),
                    shape = RoundedCornerShape(8.dp),
                    expanded = chaptersPopup,
                    properties = PopupProperties(
                        dismissOnBackPress = true,
                        focusable = true,
                        dismissOnClickOutside = true
                    ),
                    onDismissRequest = { chaptersPopup = false }) {

                    FancyText2(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        string = "Chapters",
                        solid = Color.Black,
                        size = 14f,
                        font = syncplayFont
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = CenterVertically) {
                                Text(
                                    color = Color.LightGray,
                                    text = "Skip chapter"
                                )
                            }
                        },
                        onClick = {
                            scope.launch(Dispatchers.Main.immediate) {
                                viewmodel.player?.skipChapter()
                                chaptersPopup = false
                            }
                        }
                    )

                    for (chapter in (viewmodel.media?.chapters
                        ?: listOf())) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = CenterVertically) {
                                    Text(
                                        color = Color.LightGray,
                                        text = chapter.name
                                    )
                                }
                            }, onClick = {
                                scope.launch(Dispatchers.Main.immediate) {
                                    viewmodel.player?.jumpToChapter(chapter)
                                    chaptersPopup = false
                                }

                            }
                        )
                    }
                }
            }
        }
    }
}