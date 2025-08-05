package com.yuroyami.syncplay.screens.room.subcomponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.components.ComposeUtils
import com.yuroyami.syncplay.components.ComposeUtils.FancyIcon2
import com.yuroyami.syncplay.components.syncplayFont
import com.yuroyami.syncplay.player.BasePlayer.TRACKTYPE
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.settings.DataStoreKeys.MISC_GESTURES
import com.yuroyami.syncplay.settings.valueFlow
import com.yuroyami.syncplay.settings.writeValue
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.ui.Paletting.ROOM_ICON_SIZE
import com.yuroyami.syncplay.utils.CommonUtils
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Composable
fun RoomAdvancedControlButton() {
    val viewmodel = LocalViewmodel.current
    val hasVideo by viewmodel.hasVideo.collectAsState()

    if (hasVideo) {
        var controlPanelVisibility by remember { mutableStateOf(false) }

        FancyIcon2(
            modifier = Modifier.offset(y = (-2).dp),
            icon = Icons.Filled.VideoSettings,
            size = ROOM_ICON_SIZE + 6,
            shadowColor = Color.Black,
            onClick = {
                //TODO controlcardvisible = !controlcardvisible
                //TODO addmediacardvisible = false
            }
        )
    }
}

@Composable
fun RoomAdvancedControlCard() {
    val viewmodel = LocalViewmodel.current
    val composeScope = rememberCoroutineScope()

    val subtitlePicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = CommonUtils.ccExs)) { file ->
        file?.path?.let {
            viewmodel.player?.loadExternalSub(it)
        }
    }

    /** CONTROL CARD ------ PLAYER CONTROL CARD ----- PLAYER CONTROL CARD */
    Card(
        modifier = Modifier.zIndex(10f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map {
                it.copy(alpha = 0.5f)
            })
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(
                0.5f
            )
        ),
    ) {
        FlowColumn(
            modifier = Modifier.padding(8.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalArrangement = Arrangement.Center
        ) {
            /* Aspect Ratio */
            FancyIcon2(
                icon = Icons.Filled.AspectRatio,
                size = ROOM_ICON_SIZE,
                shadowColor = Color.Black
            ) {
                composeScope.launch(Dispatchers.IO) {
                    val newAspectRatio = viewmodel.player?.switchAspectRatio()
                    if (newAspectRatio != null) {
                        viewmodel.dispatchOSD { newAspectRatio }
                    }
                }
            }

            /* Seek Gesture (DoNotTouch for disabling it) */
            val gesturesEnabled by valueFlow(MISC_GESTURES, true).collectAsState(initial = true)

            FancyIcon2(
                icon = when (gesturesEnabled) {
                    true -> Icons.Filled.TouchApp
                    false -> Icons.Filled.DoNotTouch
                }, size = ROOM_ICON_SIZE, shadowColor = Color.Black
            ) {
                composeScope.launch(Dispatchers.IO) {
                    writeValue(MISC_GESTURES, !gesturesEnabled)
                    viewmodel.dispatchOSD { if (gesturesEnabled) "Gestures enabled" else "Gestures disabled" } //TODO Localize
                }
            }

            /* Seek To */
            FancyIcon2(
                icon = Icons.Filled.BrowseGallery,
                size = ROOM_ICON_SIZE,
                shadowColor = Color.Black
            ) {
                //TODO controlcardvisible = false
                //TODO seektopopupstate.value = true
            }

            /* Undo Last Seek */
            FancyIcon2(
                icon = Icons.Filled.History,
                size = ROOM_ICON_SIZE,
                shadowColor = Color.Black
            ) {
                if (viewmodel.seeks.isEmpty()) {
                    viewmodel.dispatchOSD { "There is no recent seek in the room." }
                    return@FancyIcon2
                }

                //TODO controlcardvisible = false

                val lastSeek = viewmodel.seeks.lastOrNull() ?: return@FancyIcon2
                viewmodel.player?.seekTo(lastSeek.first)
                viewmodel.sendSeek(lastSeek.first)
                viewmodel.seeks.remove(lastSeek)
                viewmodel.dispatchOSD { "Seek undone." }
            }

            /* Subtitle Tracks */
            Box {
                val tracksPopup = remember { mutableStateOf(false) }


                FancyIcon2(
                    icon = Icons.Filled.Subtitles,
                    size = ROOM_ICON_SIZE,
                    shadowColor = Color.Black
                ) {
                    composeScope.launch {
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
                        brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map {
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

                    ComposeUtils.FancyText2(
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
                        //TODO controlcardvisible = false

                        subtitlePicker.launch()
                    })


                    DropdownMenuItem(text = {
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
                    }, onClick = {
                        viewmodel.player?.selectTrack(
                            null, TRACKTYPE.SUBTITLE
                        )
                        tracksPopup.value = false
                        //TODO controlcardvisible = false

                    })

                    for (track in (viewmodel.media?.subtitleTracks)
                        ?: listOf()) {
                        DropdownMenuItem(text = {
                            Row(verticalAlignment = CenterVertically) {
                                Checkbox(
                                    checked = track.selected.value,
                                    onCheckedChange = {
                                        viewmodel.player?.selectTrack(
                                            track, TRACKTYPE.SUBTITLE
                                        )
                                        tracksPopup.value = false
                                    })

                                Text(
                                    color = Color.LightGray,
                                    text = track.name
                                )
                            }
                        }, onClick = {
                            viewmodel.player?.selectTrack(
                                track, TRACKTYPE.SUBTITLE
                            )

                            tracksPopup.value = false
                            //TODO controlcardvisible = false

                        })
                    }
                }
            }

            /* Audio Tracks */
            Box {
                val tracksPopup = remember { mutableStateOf(false) }

                FancyIcon2(
                    icon = Icons.Filled.SpeakerGroup,
                    size = ROOM_ICON_SIZE,
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
                        brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map {
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

                    ComposeUtils.FancyText2(
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
                                        viewmodel.player?.selectTrack(
                                            track, TRACKTYPE.AUDIO
                                        )
                                        tracksPopup.value = false
                                    })

                                Text(
                                    color = Color.LightGray,
                                    text = track.name
                                )
                            }
                        }, onClick = {
                            viewmodel.player?.selectTrack(
                                track, TRACKTYPE.AUDIO
                            )
                            tracksPopup.value = false
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
                        size = ROOM_ICON_SIZE,
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
                            brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map {
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

                        ComposeUtils.FancyText2(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            string = "Chapters",
                            solid = Color.Black,
                            size = 14f,
                            font = syncplayFont
                        )

                        DropdownMenuItem(text = {
                            Row(verticalAlignment = CenterVertically) {
                                Text(
                                    color = Color.LightGray,
                                    text = "Skip chapter"
                                )
                            }
                        }, onClick = {
                            viewmodel.player?.skipChapter()
                            chaptersPopup = false
                        })

                        for (chapter in (viewmodel.media?.chapters
                            ?: listOf())) {
                            DropdownMenuItem(text = {
                                Row(verticalAlignment = CenterVertically) {
                                    Text(
                                        color = Color.LightGray,
                                        text = chapter.name
                                    )
                                }
                            }, onClick = {
                                viewmodel.player?.jumpToChapter(chapter)
                                chaptersPopup = false
                            })
                        }
                    }
                }
            }
        }
    }
}