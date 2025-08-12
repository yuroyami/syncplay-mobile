package com.yuroyami.syncplay.ui.screens.room.slidingcards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.yuroyami.syncplay.ui.popups.PopupMediaDirs.MediaDirsPopup
import com.yuroyami.syncplay.ui.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.ui.theme.Paletting
import com.yuroyami.syncplay.ui.utils.FancyIcon2
import com.yuroyami.syncplay.ui.utils.FancyText2
import com.yuroyami.syncplay.ui.utils.SyncplayPopup
import com.yuroyami.syncplay.ui.utils.getRegularFont
import com.yuroyami.syncplay.ui.utils.gradientOverlay
import com.yuroyami.syncplay.utils.CommonUtils
import com.yuroyami.syncplay.utils.CommonUtils.vidExs
import com.yuroyami.syncplay.utils.addFolderToPlaylist
import com.yuroyami.syncplay.utils.loadPlaylistLocally
import com.yuroyami.syncplay.utils.savePlaylistLocally
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.delete
import syncplaymobile.shared.generated.resources.done
import syncplaymobile.shared.generated.resources.play
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_add_file
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_add_folder
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_add_url
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_playlist_export
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_playlist_import
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_playlist_import_n_shuffle
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_set_media_directories
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_shuffle
import syncplaymobile.shared.generated.resources.room_shared_playlist_button_shuffle_rest
import kotlin.time.Clock

object CardSharedPlaylist {

    @Composable
    fun SharedPlaylistCard() {
        val viewmodel = LocalViewmodel.current
        val scope = rememberCoroutineScope { Dispatchers.IO }

        /* ActivityResultLaunchers for various shared playlist actions */
        val mediaFilePicker  = rememberFilePickerLauncher(
            type = FileKitType.File(extensions = vidExs),
            mode = FileKitMode.Multiple(),
            title = "Select one or multiple files to add to playlist"
        ) { files ->
            if (files?.isEmpty() == true || files == null) return@rememberFilePickerLauncher

            viewmodel.addFiles(files.map { it.path })
        }

        val mediaDirectoryPicker =rememberDirectoryPickerLauncher(
            title = "Select directory to add its files to playlist"
        ) { directoryUri ->
            if (directoryUri == null) return@rememberDirectoryPickerLauncher
            scope.launch {
                viewmodel.addFolderToPlaylist(directoryUri.path)
            }
        }

        var shouldShuffle by remember { mutableStateOf(false) }
        val playlistLoadPicker = rememberFilePickerLauncher(
            type = FileKitType.File(extensions = CommonUtils.playlistExs)
        ) { playlistFile ->
            if (playlistFile != null) {
                viewmodel.loadPlaylistLocally(playlistFile.path, alsoShuffle = shouldShuffle)
            }
            shouldShuffle = false
        }

        val playlistSaver = rememberFileSaverLauncher { directoryUri ->
            if (directoryUri == null) return@rememberFileSaverLauncher
            viewmodel.savePlaylistLocally(directoryUri.path)
        }

        val mediaDirsPopupState = remember { mutableStateOf(false) }
        val addUrlsPopupState = remember { mutableStateOf(false) }

        /* Now to the actual content in the card */
        Card(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f)),
        ) {
            Column(modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /* Card title */
                FancyText2(
                    string = "Shared Playlist",
                    solid = Color.Transparent,
                    size = 16f,
                    font = Font(Res.font.Directive4_Regular)
                )

                /* The actual shared playlist */
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight(0.75f)
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    itemsIndexed(viewmodel.p.session.sharedPlaylist) { index, item ->

                        val itempopup = remember { mutableStateOf(false) }

                        Box {
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 5.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = Paletting.SP_ORANGE)
                                ) { itempopup.value = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val spi by remember { viewmodel.p.session.spIndex }
                                if (index == spi) {
                                    Icon(
                                        imageVector = Icons.Outlined.PlayArrow, "",
                                        tint = Color.Green,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .padding(2.dp)
                                    )
                                }

                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    text = item, maxLines = 1, fontSize = 11.sp, color = Color.LightGray
                                )
                            }

                            DropdownMenu(
                                modifier = Modifier.background(color = Color.DarkGray),
                                expanded = itempopup.value,
                                properties = PopupProperties(
                                    dismissOnBackPress = true,
                                    focusable = true,
                                    dismissOnClickOutside = true
                                ),
                                onDismissRequest = { itempopup.value = false }) {

                                FancyText2(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    string = "Item Actions",
                                    solid = Color.Black,
                                    size = 12f,
                                    font = Font(Res.font.Directive4_Regular)
                                )

                                //Item Action: Play
                                DropdownMenuItem(
                                    text = { Text(color = Color.LightGray, text = stringResource(Res.string.play)) },
                                    leadingIcon = { Icon(imageVector = Icons.Default.PlayCircle, "", tint = Color.LightGray) },
                                    onClick = {
                                        viewmodel.sendPlaylistSelection(index)
                                        itempopup.value = false
                                    }
                                )

                                //Item Action: Delete
                                DropdownMenuItem(
                                    text = { Text(color = Color.LightGray, text = stringResource(Res.string.delete)) },
                                    leadingIcon = { Icon(imageVector = Icons.Default.Delete, "", tint = Color.LightGray) },
                                    onClick = {
                                        viewmodel.deleteItemFromPlaylist(index)
                                        itempopup.value = false
                                    }
                                )
                            }

                        }

                        if (index < viewmodel.p.session.sharedPlaylist.lastIndex)
                            HorizontalDivider(
                                modifier = Modifier
                                    .gradientOverlay()
                                    .alpha(0.7f), thickness = (0.5).dp,
                                color = Color.Black
                            )
                    }
                }

                /* Bottom buttons (add file, add folder, add url, and overflow button) */
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    /* Button to add file to Shared Playlist */
                    FancyIcon2(
                        icon = Icons.AutoMirrored.Filled.NoteAdd, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
                        onClick = {
                            mediaFilePicker.launch()
                        }
                    )

                    /* Button to add link to Shared Playlist */
                    FancyIcon2(
                        icon = Icons.Filled.AddLink, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
                        onClick = {
                            addUrlsPopupState.value = true
                        }
                    )

                    /* Button to add folder to Shared Playlist */
                    FancyIcon2(
                        icon = Icons.Filled.CreateNewFolder, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
                        onClick = {
                            mediaDirectoryPicker.launch()
                        }
                    )

                    /* Overflow menu to show all available actions */
                    Box {
                        val sharedplaylistOverflowState = remember { mutableStateOf(false) }

                        FancyIcon2(
                            icon = Icons.Filled.MoreVert, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
                            onClick = {
                                sharedplaylistOverflowState.value = !sharedplaylistOverflowState.value
                            }
                        )

                        DropdownMenu(
                            modifier = Modifier.background(color = Color.DarkGray),
                            expanded = sharedplaylistOverflowState.value,
                            properties = PopupProperties(
                                dismissOnBackPress = true,
                                focusable = true,
                                dismissOnClickOutside = true
                            ),
                            onDismissRequest = { sharedplaylistOverflowState.value = !sharedplaylistOverflowState.value }) {

                            FancyText2(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                string = "Shared Playlist Actions",
                                solid = Color.Black,
                                size = 13f,
                                font = Font(Res.font.Directive4_Regular)
                            )

                            val txtsize = 10f

                            //Shared Playlist Action: Shuffle All
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(Res.string.room_shared_playlist_button_shuffle)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Shuffle, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    scope.launch { viewmodel.shuffle(false) }
                                }
                            )

                            //Shared Playlist Action: Shuffle Rest of files
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(Res.string.room_shared_playlist_button_shuffle_rest)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Shuffle, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    scope.launch {
                                        viewmodel.shuffle(true)
                                    }
                                }
                            )

                            HorizontalDivider(thickness = (0.5).dp, color = Color.LightGray)

                            //Shared Playlist Action: Add file(s)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(Res.string.room_shared_playlist_button_add_file)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.AutoMirrored.Filled.NoteAdd, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    mediaFilePicker.launch()
                                }
                            )

                            //Shared Playlist Action: Add URL(s)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(Res.string.room_shared_playlist_button_add_url)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.AddLink, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    addUrlsPopupState.value = true
                                }
                            )

                            //Shared Playlist Action: Add folder
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(Res.string.room_shared_playlist_button_add_folder)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.CreateNewFolder, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    mediaDirectoryPicker.launch()
                                }
                            )

                            HorizontalDivider(thickness = (0.5).dp, color = Color.LightGray)

                            //Shared Playlist Action: Import playlist file (txt)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(Res.string.room_shared_playlist_button_playlist_import)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Download, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    playlistLoadPicker.launch()
                                }
                            )

                            //Shared Playlist Action: Import playlist file (txt) with shuffling
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(Res.string.room_shared_playlist_button_playlist_import_n_shuffle)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Download, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    playlistLoadPicker.launch()
                                    shouldShuffle = true
                                }
                            )

                            //Shared Playlist Action: Export playlist to file
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(Res.string.room_shared_playlist_button_playlist_export)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Save, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    if (viewmodel.p.session.sharedPlaylist.isEmpty()) {
                                        viewmodel.osdManager.dispatchOSD { "Shared Playlist is empty. Nothing to save." }
                                        return@DropdownMenuItem
                                    }

                                    playlistSaver.launch(
                                        suggestedName = "SharedPlaylist_${Clock.System.now()}",
                                        extension = "txt"
                                    )
                                }
                            )

                            HorizontalDivider(thickness = (0.5).dp, color = Color.LightGray)

                            //Shared Playlist Action: Set Media Directories
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(Res.string.room_shared_playlist_button_set_media_directories)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Folder, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    mediaDirsPopupState.value = true
                                }
                            )

                            HorizontalDivider(thickness = (0.5).dp, color = Color.LightGray)

                            //Shared Playlist Action: Clear playlist
                            DropdownMenuItem(
                                text = { Text(fontSize = txtsize.sp, color = Color.LightGray, text = "Clear the playlist") },
                                leadingIcon = { Icon(imageVector = Icons.Filled.ClearAll, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    viewmodel.clearPlaylist()
                                }
                            )
                        }
                    }


                }

            }
        }

        MediaDirsPopup(mediaDirsPopupState)
        AddSPUrlsPopup(addUrlsPopupState)
    }

    @Composable
    fun AddSPUrlsPopup(visibilityState: MutableState<Boolean>) {
        return SyncplayPopup(
            dialogOpen = visibilityState.value,
            strokeWidth = 0.5f,
            onDismiss = { visibilityState.value = false }
        ) {
            val viewmodel = LocalViewmodel.current

            val clipboardManager: ClipboardManager = LocalClipboardManager.current

            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /* The title */
                FancyText2(
                    string = "Add URLs to Shared Playlist",
                    solid = Color.Black,
                    size = 18f,
                    font = Font(Res.font.Directive4_Regular)
                )

                /* Title's subtext */
                Text(
                    text = "Each line wil be added as an entry to the shared playlist.\nSyncplay Android supports only direct links for now.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily(getRegularFont()),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )

                /* The URLs input box */
                val urls = remember { mutableStateOf("") }
                TextField(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(16.dp),
                    value = urls.value,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.DarkGray,
                        unfocusedContainerColor = Color.DarkGray,
                        disabledContainerColor = Color.DarkGray,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            urls.value = clipboardManager.getText().toString()
                        }) {
                            Icon(imageVector = Icons.Filled.ContentPaste, "", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Link, "", tint = MaterialTheme.colorScheme.primary)
                    },
                    onValueChange = { urls.value = it },
                    textStyle = TextStyle(
                        brush = Brush.linearGradient(
                            colors = Paletting.SP_GRADIENT
                        ),
                        fontFamily = FontFamily(getRegularFont()),
                        fontSize = 16.sp,
                    ),
                    label = {
                        Text("URLs", color = Color.Gray)
                    }
                )

                /* Ok button */
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(width = 1.dp, color = Color.Black),
                    onClick = {
                        visibilityState.value = false

                        viewmodel.addURLs(urls.value.split("\n"))
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Done, "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.done), fontSize = 14.sp)
                }
            }
        }
    }
}