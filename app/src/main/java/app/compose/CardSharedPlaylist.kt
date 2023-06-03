package app.compose

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import app.R
import app.activities.WatchActivity
import app.compose.PopupMediaDirs.MediaDirsPopup
import app.ui.Paletting
import app.utils.ComposeUtils
import app.utils.ComposeUtils.gradientOverlay
import app.utils.MiscUtils.getFileName
import app.utils.MiscUtils.toasty
import app.utils.SharedPlaylistUtils.addFilesToPlaylist
import app.utils.SharedPlaylistUtils.addFolderToPlaylist
import app.utils.SharedPlaylistUtils.addURLs
import app.utils.SharedPlaylistUtils.clearPlaylist
import app.utils.SharedPlaylistUtils.deleteItemFromPlaylist
import app.utils.SharedPlaylistUtils.loadSHP
import app.utils.SharedPlaylistUtils.saveSHP
import app.utils.SharedPlaylistUtils.sendPlaylistSelection
import app.utils.SharedPlaylistUtils.shuffle

object CardSharedPlaylist {

    @Composable
    fun WatchActivity.SharedPlaylistCard() {
        /* ActivityResultLaunchers for various shared playlist actions */
        val spAddFile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
            onResult = { uris ->
                wentForFilePick = false

                if (uris.isEmpty()) return@rememberLauncherForActivityResult

                for (uri in uris) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                addFilesToPlaylist(uris)
            }
        )

        val spAddFolder = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { treeUri ->
                wentForFilePick = false

                if (treeUri == null) return@rememberLauncherForActivityResult

                contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFolderToPlaylist(treeUri)
            }
        )

        val spLoadFileNoShuffle = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                wentForFilePick = false

                if (uri == null) return@rememberLauncherForActivityResult

                val filename = getFileName(uri).toString()
                val extension = filename.substring(filename.length - 4)
                if (extension == ".txt") {
                    loadSHP(uri, false)
                } else {
                    toasty("Error: Not a valid plain text file (.txt)")
                }
            }
        )

        val spLoadFileWithShuffle = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                wentForFilePick = false

                if (uri == null) return@rememberLauncherForActivityResult

                val filename = getFileName(uri).toString()
                val extension = filename.substring(filename.length - 4)
                if (extension == ".txt") {
                    loadSHP(uri, true)
                } else {
                    toasty("Error: Not a valid plain text file (.txt)")
                }
            }
        )

        val spSaveToFile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri ->
                wentForFilePick = false

                if (uri == null) return@rememberLauncherForActivityResult

                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                saveSHP(uri)
            }
        )

        val mediaDirsPopupState = remember { mutableStateOf(false) }
        val addUrlsPopupState = remember { mutableStateOf(false) }

        /* Now to the actual content in the card */
        Card(
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            ConstraintLayout(modifier = Modifier.fillMaxSize()) {
                val (sptitle, spcadre, spbottombuttons) = createRefs()

                /* Card title */
                ComposeUtils.FancyText2(
                    modifier = Modifier.constrainAs(sptitle) {
                        top.linkTo(parent.top, 4.dp)
                        end.linkTo(parent.end)
                        start.linkTo(parent.start)
                        width = Dimension.wrapContent
                    },
                    string = "Shared Playlist",
                    solid = Color.Transparent,
                    size = 16f,
                    font = Font(R.font.directive4bold)
                )

                /* Bottom buttons (add file, add folder, add url, and overflow button) */
                Row(horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.constrainAs(spbottombuttons) {
                        bottom.linkTo(parent.bottom, 4.dp)
                        end.linkTo(parent.end)
                        start.linkTo(parent.start)
                        width = Dimension.percent(0.8f)
                    }) {

                    /* Button to add file to Shared Playlist */
                    ComposeUtils.FancyIcon2(
                        icon = Icons.Filled.NoteAdd, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
                        onClick = {
                            wentForFilePick = true
                            spAddFile.launch(arrayOf("video/*"))
                        }
                    )

                    /* Button to add link to Shared Playlist */
                    ComposeUtils.FancyIcon2(
                        icon = Icons.Filled.AddLink, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
                        onClick = {
                            addUrlsPopupState.value = true
                        }
                    )

                    /* Button to add folder to Shared Playlist */
                    ComposeUtils.FancyIcon2(
                        icon = Icons.Filled.CreateNewFolder, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
                        onClick = {
                            wentForFilePick = true
                            spAddFolder.launch(null)
                        }
                    )

                    /* Overflow menu to show all available actions */
                    Box {
                        val sharedplaylistOverflowState = remember { mutableStateOf(false) }

                        ComposeUtils.FancyIcon2(
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

                            ComposeUtils.FancyText2(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                string = "Shared Playlist Actions",
                                solid = Color.Black,
                                size = 13f,
                                font = Font(R.font.directive4bold)
                            )

                            val txtsize = 10f

                            //Shared Playlist Action: Shuffle All
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(R.string.room_shared_playlist_button_shuffle)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Shuffle, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    shuffle(false)
                                }
                            )

                            //Shared Playlist Action: Shuffle Rest of files
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(R.string.room_shared_playlist_button_shuffle_rest)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Shuffle, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    shuffle(true)
                                }
                            )

                            Divider(thickness = (0.5).dp, color = Color.LightGray)

                            //Shared Playlist Action: Add file(s)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(R.string.room_shared_playlist_button_add_file)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.NoteAdd, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    wentForFilePick = true
                                    spAddFile.launch(arrayOf("video/*"))
                                }
                            )

                            //Shared Playlist Action: Add URL(s)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(R.string.room_shared_playlist_button_add_url)
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
                                        text = stringResource(R.string.room_shared_playlist_button_add_folder)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.CreateNewFolder, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    wentForFilePick = true
                                    spAddFolder.launch(null)
                                }
                            )

                            Divider(thickness = (0.5).dp, color = Color.LightGray)

                            //Shared Playlist Action: Import playlist file (txt)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(R.string.room_shared_playlist_button_playlist_import)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Download, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    wentForFilePick = true
                                    spLoadFileNoShuffle.launch(arrayOf("text/plain"))
                                }
                            )

                            //Shared Playlist Action: Import playlist file (txt) with shuffling
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(R.string.room_shared_playlist_button_playlist_import_n_shuffle)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Download, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    wentForFilePick = true
                                    spLoadFileWithShuffle.launch(arrayOf("text/plain"))
                                }
                            )

                            //Shared Playlist Action: Export playlist to file
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(R.string.room_shared_playlist_button_playlist_export)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Save, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    if (p.session.sharedPlaylist.isEmpty()) {
                                        toasty("Shared Playlist is empty. Nothing to save.")
                                        return@DropdownMenuItem
                                    }

                                    wentForFilePick = true

                                    spSaveToFile.launch(null)
                                }
                            )

                            Divider(thickness = (0.5).dp, color = Color.LightGray)

                            //Shared Playlist Action: Set Media Directories
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        fontSize = txtsize.sp,
                                        color = Color.LightGray,
                                        text = stringResource(R.string.room_shared_playlist_button_set_media_directories)
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Filled.Folder, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    mediaDirsPopupState.value = true
                                }
                            )

                            Divider(thickness = (0.5).dp, color = Color.LightGray)

                            //Shared Playlist Action: Clear playlist
                            DropdownMenuItem(
                                text = { Text(fontSize = txtsize.sp, color = Color.LightGray, text = "Clear the playlist") },
                                leadingIcon = { Icon(imageVector = Icons.Filled.ClearAll, "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    clearPlaylist()
                                }
                            )
                        }
                    }


                }

                /* The actual shared playlist */
                LazyColumn(
                    modifier = Modifier
                        .background(color = Color(60, 60, 60))
                        .constrainAs(spcadre) {
                            top.linkTo(sptitle.bottom, 4.dp)
                            bottom.linkTo(spbottombuttons.top, 6.dp)
                            end.linkTo(parent.end, 18.dp)
                            start.linkTo(parent.start, 12.dp)
                            width = Dimension.fillToConstraints
                            height = Dimension.fillToConstraints
                        }
                ) {
                    itemsIndexed(p.session.sharedPlaylist) { index, item ->

                        val itempopup = remember { mutableStateOf(false) }


                        Box {
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 5.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(color = Paletting.SP_ORANGE)
                                ) { itempopup.value = true }
                            ) {
                                if (index == p.session.sharedPlaylistIndex) {
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
                                        .padding(vertical = 6.dp),
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

                                ComposeUtils.FancyText2(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    string = "Item Actions",
                                    solid = Color.Black,
                                    size = 12f,
                                    font = Font(R.font.directive4bold)
                                )

                                //Item Action: Play
                                DropdownMenuItem(
                                    text = { Text(color = Color.LightGray, text = stringResource(R.string.play)) },
                                    leadingIcon = { Icon(imageVector = Icons.Default.PlayCircle, "", tint = Color.LightGray) },
                                    onClick = {
                                        sendPlaylistSelection(index)
                                        itempopup.value = false
                                    }
                                )

                                //Item Action: Delete
                                DropdownMenuItem(
                                    text = { Text(color = Color.LightGray, text = stringResource(R.string.delete)) },
                                    leadingIcon = { Icon(imageVector = Icons.Default.Delete, "", tint = Color.LightGray) },
                                    onClick = {
                                        deleteItemFromPlaylist(index)
                                        itempopup.value = false
                                    }
                                )
                            }

                        }

                        if (index < p.session.sharedPlaylist.lastIndex)
                            Divider(
                                modifier = Modifier
                                    .gradientOverlay()
                                    .alpha(0.7f), color = Color.Black, thickness = (0.5).dp
                            )
                    }
                }

            }
        }

        MediaDirsPopup(mediaDirsPopupState)
        AddSPUrlsPopup(addUrlsPopupState)
    }

    @Composable
    fun WatchActivity.AddSPUrlsPopup(visibilityState: MutableState<Boolean>) {
        return ComposeUtils.RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.9f,
            heightPercent = 0.85f,
            strokeWidth = 0.5f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {
            val clipboardManager: ClipboardManager = LocalClipboardManager.current

            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {

                val (title, soustitre, urlbox, buttons) = createRefs()

                /* The title */
                ComposeUtils.FancyText2(
                    modifier = Modifier.constrainAs(title) {
                        top.linkTo(parent.top, 12.dp)
                        end.linkTo(parent.end)
                        start.linkTo(parent.start)
                    },
                    string = "Add URLs to Shared Playlist",
                    solid = Color.Black,
                    size = 18f,
                    font = Font(R.font.directive4bold)
                )

                /* Title's subtext */
                Text(
                    text = "Each line wil be added as an entry to the shared playlist.\nSyncplay Android supports only direct links for now.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily(Font(R.font.inter)),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    modifier = Modifier.constrainAs(soustitre) {
                        top.linkTo(title.bottom, 6.dp)
                        end.linkTo(parent.end, 12.dp)
                        start.linkTo(parent.start, 12.dp)
                        width = Dimension.percent(0.6f)
                    })

                /* The URLs input box */
                val urls = remember { mutableStateOf("") }
                TextField(
                    modifier = Modifier.constrainAs(urlbox) {
                        top.linkTo(soustitre.bottom, 8.dp)
                        absoluteLeft.linkTo(parent.absoluteLeft)
                        absoluteRight.linkTo(parent.absoluteRight)
                        bottom.linkTo(buttons.top, 12.dp)
                        width = Dimension.percent(0.9f)
                        height = Dimension.wrapContent
                    },
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
                        fontFamily = FontFamily(Font(R.font.inter)),
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
                    modifier = Modifier.constrainAs(buttons) {
                        bottom.linkTo(parent.bottom, 4.dp)
                        end.linkTo(parent.end, 12.dp)
                        start.linkTo(parent.start, 12.dp)
                        width = Dimension.wrapContent
                    },
                    onClick = {
                        visibilityState.value = false

                        addURLs(urls.value.split("\n"))
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Done, "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.done), fontSize = 14.sp)
                }
            }
        }
    }
}