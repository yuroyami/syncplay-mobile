package app.ui.compose

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import app.R
import app.ui.Paletting
import app.ui.activities.WatchActivity
import app.ui.compose.MediaDirsPopup.MediaDirsPopup
import app.utils.ComposeUtils
import app.utils.ComposeUtils.syncplayGradient
import app.utils.MiscUtils.getFileName
import app.utils.SharedPlaylistUtils.addFilesToPlaylist
import app.utils.SharedPlaylistUtils.addFolderToPlaylist
import app.utils.SharedPlaylistUtils.clearPlaylist
import app.utils.SharedPlaylistUtils.deleteItemFromPlaylist
import app.utils.SharedPlaylistUtils.loadSHP
import app.utils.SharedPlaylistUtils.saveSHP
import app.utils.SharedPlaylistUtils.sendPlaylistSelection
import app.utils.SharedPlaylistUtils.shuffle
import app.utils.UIUtils.toasty

object SharedPlaylistCard {

    @Composable
    fun WatchActivity.SharedPlaylistCard() {
        /* ActivityResultLaunchers for various shared playlist actions */
        val spAddFile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
            onResult = { uris ->
                if (uris.isEmpty()) return@rememberLauncherForActivityResult;

                for (uri in uris) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                addFilesToPlaylist(uris)
            }
        )

        val spAddFolder = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { treeUri ->
                if (treeUri == null) return@rememberLauncherForActivityResult;

                contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFolderToPlaylist(treeUri)
            }
        )

        val spLoadFileNoShuffle = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
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
                if (uri == null) return@rememberLauncherForActivityResult

                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                saveSHP(uri)
            }
        )

        val mediaDirsPopupState = remember { mutableStateOf(false) }

        /* Now to the actual content in the card */
        Card(
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
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
                            spAddFile.launch(arrayOf("video/*"))
                        }
                    )

                    /* Button to add link to Shared Playlist */
                    ComposeUtils.FancyIcon2(
                        icon = Icons.Filled.AddLink, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
                        onClick = {
                            //TODO
                        }
                    )

                    /* Button to add folder to Shared Playlist */
                    ComposeUtils.FancyIcon2(
                        icon = Icons.Filled.CreateNewFolder, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
                        onClick = {
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
                                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_shuffle), "", tint = Color.LightGray) },
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
                                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_shuffle), "", tint = Color.LightGray) },
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
                                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_shared_playlist_add), "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
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
                                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_url), "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    //TODO
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
                                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_add_folder), "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
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
                                leadingIcon = { Icon(painter = painterResource(R.drawable.file_import), "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
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
                                leadingIcon = { Icon(painter = painterResource(R.drawable.file_import_shf), "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
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
                                leadingIcon = { Icon(painter = painterResource(R.drawable.save), "", tint = Color.LightGray) },
                                onClick = {
                                    sharedplaylistOverflowState.value = false
                                    if (p.session.sharedPlaylist.isEmpty()) {
                                        toasty("Shared Playlist is empty. Nothing to save.")
                                        return@DropdownMenuItem
                                    }

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
                                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_clear_all), "", tint = Color.LightGray) },
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
                                    leadingIcon = { Icon(imageVector = Icons.Default.PlayCircle, "") },
                                    onClick = {
                                        sendPlaylistSelection(index)
                                        itempopup.value = false
                                    }
                                )

                                //Item Action: Delete
                                DropdownMenuItem(
                                    text = { Text(color = Color.LightGray, text = stringResource(R.string.delete)) },
                                    leadingIcon = { Icon(imageVector = Icons.Default.Delete, "") },
                                    onClick = {
                                        deleteItemFromPlaylist(index)
                                        itempopup.value = false
                                    }
                                )
                            }

                        }

                        if (index < p.session.sharedPlaylist.lastIndex)
                            Divider(modifier = Modifier
                                .syncplayGradient()
                                .alpha(0.7f), color = Color.Black, thickness = (0.5).dp)
                    }
                }

            }
        }

        MediaDirsPopup(mediaDirsPopupState)

    }
}