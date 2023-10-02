package com.yuroyami.syncplay.compose

object CardSharedPlaylist {

//    @Composable
//    fun WatchActivity.SharedPlaylistCard() {
//        /* ActivityResultLaunchers for various shared playlist actions */
//        val spAddFile = rememberLauncherForActivityResult(
//            contract = ActivityResultContracts.OpenMultipleDocuments(),
//            onResult = { uris ->
//                wentForFilePick = false
//
//                if (uris.isEmpty()) return@rememberLauncherForActivityResult
//
//                for (uri in uris) {
//                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                }
//
//                addFilesToPlaylist(uris)
//            }
//        )
//
//        val spAddFolder = rememberLauncherForActivityResult(
//            contract = ActivityResultContracts.OpenDocumentTree(),
//            onResult = { treeUri ->
//                wentForFilePick = false
//
//                if (treeUri == null) return@rememberLauncherForActivityResult
//
//                contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                addFolderToPlaylist(treeUri)
//            }
//        )
//
//        val spLoadFileNoShuffle = rememberLauncherForActivityResult(
//            contract = ActivityResultContracts.OpenDocument(),
//            onResult = { uri ->
//                wentForFilePick = false
//
//                if (uri == null) return@rememberLauncherForActivityResult
//
//                val filename = getFileName(uri).toString()
//                val extension = filename.substring(filename.length - 4)
//                if (extension == ".txt") {
//                    loadSHP(uri, false)
//                } else {
//                    toasty("Error: Not a valid plain text file (.txt)")
//                }
//            }
//        )
//
//        val spLoadFileWithShuffle = rememberLauncherForActivityResult(
//            contract = ActivityResultContracts.OpenDocument(),
//            onResult = { uri ->
//                wentForFilePick = false
//
//                if (uri == null) return@rememberLauncherForActivityResult
//
//                val filename = getFileName(uri).toString()
//                val extension = filename.substring(filename.length - 4)
//                if (extension == ".txt") {
//                    loadSHP(uri, true)
//                } else {
//                    toasty("Error: Not a valid plain text file (.txt)")
//                }
//            }
//        )
//
//        val spSaveToFile = rememberLauncherForActivityResult(
//            contract = ActivityResultContracts.OpenDocumentTree(),
//            onResult = { uri ->
//                wentForFilePick = false
//
//                if (uri == null) return@rememberLauncherForActivityResult
//
//                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
//                contentResolver.takePersistableUriPermission(uri, takeFlags)
//                saveSHP(uri)
//            }
//        )
//
//        val mediaDirsPopupState = remember { mutableStateOf(false) }
//        val addUrlsPopupState = remember { mutableStateOf(false) }
//
//        /* Now to the actual content in the card */
//        Card(
//            shape = RoundedCornerShape(6.dp),
//            border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
//            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
//            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
//        ) {
//            ConstraintLayout(modifier = Modifier.fillMaxSize()) {
//                val (sptitle, spcadre, spbottombuttons) = createRefs()
//
//                /* Card title */
//                ComposeUtils.FancyText2(
//                    modifier = Modifier.constrainAs(sptitle) {
//                        top.linkTo(parent.top, 4.dp)
//                        end.linkTo(parent.end)
//                        start.linkTo(parent.start)
//                        width = Dimension.wrapContent
//                    },
//                    string = "Shared Playlist",
//                    solid = Color.Transparent,
//                    size = 16f,
//                    font = Font(R.font.directive4bold)
//                )
//
//                /* Bottom buttons (add file, add folder, add url, and overflow button) */
//                Row(horizontalArrangement = Arrangement.SpaceEvenly,
//                    modifier = Modifier.constrainAs(spbottombuttons) {
//                        bottom.linkTo(parent.bottom, 4.dp)
//                        end.linkTo(parent.end)
//                        start.linkTo(parent.start)
//                        width = Dimension.percent(0.8f)
//                    }) {
//
//                    /* Button to add file to Shared Playlist */
//                    ComposeUtils.FancyIcon2(
//                        icon = Icons.Filled.NoteAdd, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
//                        onClick = {
//                            wentForFilePick = true
//                            spAddFile.launch(arrayOf("video/*"))
//                        }
//                    )
//
//                    /* Button to add link to Shared Playlist */
//                    ComposeUtils.FancyIcon2(
//                        icon = Icons.Filled.AddLink, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
//                        onClick = {
//                            addUrlsPopupState.value = true
//                        }
//                    )
//
//                    /* Button to add folder to Shared Playlist */
//                    ComposeUtils.FancyIcon2(
//                        icon = Icons.Filled.CreateNewFolder, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
//                        onClick = {
//                            wentForFilePick = true
//                            spAddFolder.launch(null)
//                        }
//                    )
//
//                    /* Overflow menu to show all available actions */
//                    Box {
//                        val sharedplaylistOverflowState = remember { mutableStateOf(false) }
//
//                        ComposeUtils.FancyIcon2(
//                            icon = Icons.Filled.MoreVert, size = Paletting.ROOM_ICON_SIZE, shadowColor = Color.Black,
//                            onClick = {
//                                sharedplaylistOverflowState.value = !sharedplaylistOverflowState.value
//                            }
//                        )
//
//                        DropdownMenu(
//                            modifier = Modifier.background(color = Color.DarkGray),
//                            expanded = sharedplaylistOverflowState.value,
//                            properties = PopupProperties(
//                                dismissOnBackPress = true,
//                                focusable = true,
//                                dismissOnClickOutside = true
//                            ),
//                            onDismissRequest = { sharedplaylistOverflowState.value = !sharedplaylistOverflowState.value }) {
//
//                            ComposeUtils.FancyText2(
//                                modifier = Modifier.align(Alignment.CenterHorizontally),
//                                string = "Shared Playlist Actions",
//                                solid = Color.Black,
//                                size = 13f,
//                                font = Font(R.font.directive4bold)
//                            )
//
//                            val txtsize = 10f
//
//                            //Shared Playlist Action: Shuffle All
//                            DropdownMenuItem(
//                                text = {
//                                    Text(
//                                        fontSize = txtsize.sp,
//                                        color = Color.LightGray,
//                                        text = stringResource(R.string.room_shared_playlist_button_shuffle)
//                                    )
//                                },
//                                leadingIcon = { Icon(imageVector = Icons.Filled.Shuffle, "", tint = Color.LightGray) },
//                                onClick = {
//                                    sharedplaylistOverflowState.value = false
//                                    shuffle(false)
//                                }
//                            )
//
//                            //Shared Playlist Action: Shuffle Rest of files
//                            DropdownMenuItem(
//                                text = {
//                                    Text(
//                                        fontSize = txtsize.sp,
//                                        color = Color.LightGray,
//                                        text = stringResource(R.string.room_shared_playlist_button_shuffle_rest)
//                                    )
//                                },
//                                leadingIcon = { Icon(imageVector = Icons.Filled.Shuffle, "", tint = Color.LightGray) },
//                                onClick = {
//                                    sharedplaylistOverflowState.value = false
//                                    shuffle(true)
//                                }
//                            )
//
//                            Divider(thickness = (0.5).dp, color = Color.LightGray)
//
//                            //Shared Playlist Action: Add file(s)
//                            DropdownMenuItem(
//                                text = {
//                                    Text(
//                                        fontSize = txtsize.sp,
//                                        color = Color.LightGray,
//                                        text = stringResource(R.string.room_shared_playlist_button_add_file)
//                                    )
//                                },
//                                leadingIcon = { Icon(imageVector = Icons.Filled.NoteAdd, "", tint = Color.LightGray) },
//                                onClick = {
//                                    sharedplaylistOverflowState.value = false
//                                    wentForFilePick = true
//                                    spAddFile.launch(arrayOf("video/*"))
//                                }
//                            )
//
//                            //Shared Playlist Action: Add URL(s)
//                            DropdownMenuItem(
//                                text = {
//                                    Text(
//                                        fontSize = txtsize.sp,
//                                        color = Color.LightGray,
//                                        text = stringResource(R.string.room_shared_playlist_button_add_url)
//                                    )
//                                },
//                                leadingIcon = { Icon(imageVector = Icons.Filled.AddLink, "", tint = Color.LightGray) },
//                                onClick = {
//                                    sharedplaylistOverflowState.value = false
//                                    addUrlsPopupState.value = true
//                                }
//                            )
//
//                            //Shared Playlist Action: Add folder
//                            DropdownMenuItem(
//                                text = {
//                                    Text(
//                                        fontSize = txtsize.sp,
//                                        color = Color.LightGray,
//                                        text = stringResource(R.string.room_shared_playlist_button_add_folder)
//                                    )
//                                },
//                                leadingIcon = { Icon(imageVector = Icons.Filled.CreateNewFolder, "", tint = Color.LightGray) },
//                                onClick = {
//                                    sharedplaylistOverflowState.value = false
//                                    wentForFilePick = true
//                                    spAddFolder.launch(null)
//                                }
//                            )
//
//                            Divider(thickness = (0.5).dp, color = Color.LightGray)
//
//                            //Shared Playlist Action: Import playlist file (txt)
//                            DropdownMenuItem(
//                                text = {
//                                    Text(
//                                        fontSize = txtsize.sp,
//                                        color = Color.LightGray,
//                                        text = stringResource(R.string.room_shared_playlist_button_playlist_import)
//                                    )
//                                },
//                                leadingIcon = { Icon(imageVector = Icons.Filled.Download, "", tint = Color.LightGray) },
//                                onClick = {
//                                    sharedplaylistOverflowState.value = false
//                                    wentForFilePick = true
//                                    spLoadFileNoShuffle.launch(arrayOf("text/plain"))
//                                }
//                            )
//
//                            //Shared Playlist Action: Import playlist file (txt) with shuffling
//                            DropdownMenuItem(
//                                text = {
//                                    Text(
//                                        fontSize = txtsize.sp,
//                                        color = Color.LightGray,
//                                        text = stringResource(R.string.room_shared_playlist_button_playlist_import_n_shuffle)
//                                    )
//                                },
//                                leadingIcon = { Icon(imageVector = Icons.Filled.Download, "", tint = Color.LightGray) },
//                                onClick = {
//                                    sharedplaylistOverflowState.value = false
//                                    wentForFilePick = true
//                                    spLoadFileWithShuffle.launch(arrayOf("text/plain"))
//                                }
//                            )
//
//                            //Shared Playlist Action: Export playlist to file
//                            DropdownMenuItem(
//                                text = {
//                                    Text(
//                                        fontSize = txtsize.sp,
//                                        color = Color.LightGray,
//                                        text = stringResource(R.string.room_shared_playlist_button_playlist_export)
//                                    )
//                                },
//                                leadingIcon = { Icon(imageVector = Icons.Filled.Save, "", tint = Color.LightGray) },
//                                onClick = {
//                                    sharedplaylistOverflowState.value = false
//                                    if (p.session.sharedPlaylist.isEmpty()) {
//                                        toasty("Shared Playlist is empty. Nothing to save.")
//                                        return@DropdownMenuItem
//                                    }
//
//                                    wentForFilePick = true
//
//                                    spSaveToFile.launch(null)
//                                }
//                            )
//
//                            Divider(thickness = (0.5).dp, color = Color.LightGray)
//
//                            //Shared Playlist Action: Set Media Directories
//                            DropdownMenuItem(
//                                text = {
//                                    Text(
//                                        fontSize = txtsize.sp,
//                                        color = Color.LightGray,
//                                        text = stringResource(R.string.room_shared_playlist_button_set_media_directories)
//                                    )
//                                },
//                                leadingIcon = { Icon(imageVector = Icons.Filled.Folder, "", tint = Color.LightGray) },
//                                onClick = {
//                                    sharedplaylistOverflowState.value = false
//                                    mediaDirsPopupState.value = true
//                                }
//                            )
//
//                            Divider(thickness = (0.5).dp, color = Color.LightGray)
//
//                            //Shared Playlist Action: Clear playlist
//                            DropdownMenuItem(
//                                text = { Text(fontSize = txtsize.sp, color = Color.LightGray, text = "Clear the playlist") },
//                                leadingIcon = { Icon(imageVector = Icons.Filled.ClearAll, "", tint = Color.LightGray) },
//                                onClick = {
//                                    sharedplaylistOverflowState.value = false
//                                    clearPlaylist()
//                                }
//                            )
//                        }
//                    }
//
//
//                }
//
//                /* The actual shared playlist */
//                LazyColumn(
//                    modifier = Modifier
//                        .background(color = Color(60, 60, 60))
//                        .constrainAs(spcadre) {
//                            top.linkTo(sptitle.bottom, 4.dp)
//                            bottom.linkTo(spbottombuttons.top, 6.dp)
//                            end.linkTo(parent.end, 18.dp)
//                            start.linkTo(parent.start, 12.dp)
//                            width = Dimension.fillToConstraints
//                            height = Dimension.fillToConstraints
//                        }
//                ) {
//                    itemsIndexed(p.session.sharedPlaylist) { index, item ->
//
//                        val itempopup = remember { mutableStateOf(false) }
//
//
//                        Box {
//                            Row(modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(horizontal = 5.dp)
//                                .clickable(
//                                    interactionSource = remember { MutableInteractionSource() },
//                                    indication = rememberRipple(color = Paletting.SP_ORANGE)
//                                ) { itempopup.value = true }
//                            ) {
//                                if (index == p.session.sharedPlaylistIndex) {
//                                    Icon(
//                                        imageVector = Icons.Outlined.PlayArrow, "",
//                                        tint = Color.Green,
//                                        modifier = Modifier
//                                            .size(24.dp)
//                                            .padding(2.dp)
//                                    )
//                                }
//
//                                Text(
//                                    modifier = Modifier
//                                        .fillMaxWidth()
//                                        .padding(vertical = 6.dp),
//                                    text = item, maxLines = 1, fontSize = 11.sp, color = Color.LightGray
//                                )
//                            }
//
//                            DropdownMenu(
//                                modifier = Modifier.background(color = Color.DarkGray),
//                                expanded = itempopup.value,
//                                properties = PopupProperties(
//                                    dismissOnBackPress = true,
//                                    focusable = true,
//                                    dismissOnClickOutside = true
//                                ),
//                                onDismissRequest = { itempopup.value = false }) {
//
//                                ComposeUtils.FancyText2(
//                                    modifier = Modifier.align(Alignment.CenterHorizontally),
//                                    string = "Item Actions",
//                                    solid = Color.Black,
//                                    size = 12f,
//                                    font = Font(R.font.directive4bold)
//                                )
//
//                                //Item Action: Play
//                                DropdownMenuItem(
//                                    text = { Text(color = Color.LightGray, text = stringResource(R.string.play)) },
//                                    leadingIcon = { Icon(imageVector = Icons.Default.PlayCircle, "", tint = Color.LightGray) },
//                                    onClick = {
//                                        sendPlaylistSelection(index)
//                                        itempopup.value = false
//                                    }
//                                )
//
//                                //Item Action: Delete
//                                DropdownMenuItem(
//                                    text = { Text(color = Color.LightGray, text = stringResource(R.string.delete)) },
//                                    leadingIcon = { Icon(imageVector = Icons.Default.Delete, "", tint = Color.LightGray) },
//                                    onClick = {
//                                        deleteItemFromPlaylist(index)
//                                        itempopup.value = false
//                                    }
//                                )
//                            }
//
//                        }
//
//                        if (index < p.session.sharedPlaylist.lastIndex)
//                            Divider(
//                                modifier = Modifier
//                                    .gradientOverlay()
//                                    .alpha(0.7f), color = Color.Black, thickness = (0.5).dp
//                            )
//                    }
//                }
//
//            }
//        }
//
//        MediaDirsPopup(mediaDirsPopupState)
//        AddSPUrlsPopup(addUrlsPopupState)
//    }
//
//    @Composable
//    fun WatchActivity.AddSPUrlsPopup(visibilityState: MutableState<Boolean>) {
//        return ComposeUtils.RoomPopup(
//            dialogOpen = visibilityState.value,
//            widthPercent = 0.9f,
//            heightPercent = 0.85f,
//            strokeWidth = 0.5f,
//            cardBackgroundColor = Color.DarkGray,
//            onDismiss = { visibilityState.value = false }
//        ) {
//            val clipboardManager: ClipboardManager = LocalClipboardManager.current
//
//            ConstraintLayout(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(6.dp)
//            ) {
//
//                val (title, soustitre, urlbox, buttons) = createRefs()
//
//                /* The title */
//                ComposeUtils.FancyText2(
//                    modifier = Modifier.constrainAs(title) {
//                        top.linkTo(parent.top, 12.dp)
//                        end.linkTo(parent.end)
//                        start.linkTo(parent.start)
//                    },
//                    string = "Add URLs to Shared Playlist",
//                    solid = Color.Black,
//                    size = 18f,
//                    font = Font(R.font.directive4bold)
//                )
//
//                /* Title's subtext */
//                Text(
//                    text = "Each line wil be added as an entry to the shared playlist.\nSyncplay Android supports only direct links for now.",
//                    color = MaterialTheme.colorScheme.primary,
//                    fontSize = 10.sp,
//                    fontFamily = FontFamily(Font(R.font.inter)),
//                    textAlign = TextAlign.Center,
//                    lineHeight = 14.sp,
//                    modifier = Modifier.constrainAs(soustitre) {
//                        top.linkTo(title.bottom, 6.dp)
//                        end.linkTo(parent.end, 12.dp)
//                        start.linkTo(parent.start, 12.dp)
//                        width = Dimension.percent(0.6f)
//                    })
//
//                /* The URLs input box */
//                val urls = remember { mutableStateOf("") }
//                TextField(
//                    modifier = Modifier.constrainAs(urlbox) {
//                        top.linkTo(soustitre.bottom, 8.dp)
//                        absoluteLeft.linkTo(parent.absoluteLeft)
//                        absoluteRight.linkTo(parent.absoluteRight)
//                        bottom.linkTo(buttons.top, 12.dp)
//                        width = Dimension.percent(0.9f)
//                        height = Dimension.wrapContent
//                    },
//                    shape = RoundedCornerShape(16.dp),
//                    value = urls.value,
//                    colors = TextFieldDefaults.colors(
//                        focusedContainerColor = Color.DarkGray,
//                        unfocusedContainerColor = Color.DarkGray,
//                        disabledContainerColor = Color.DarkGray,
//                        focusedIndicatorColor = Color.Transparent,
//                        unfocusedIndicatorColor = Color.Transparent,
//                        disabledIndicatorColor = Color.Transparent,
//                    ),
//                    trailingIcon = {
//                        IconButton(onClick = {
//                            urls.value = clipboardManager.getText().toString()
//                        }) {
//                            Icon(imageVector = Icons.Filled.ContentPaste, "", tint = MaterialTheme.colorScheme.primary)
//                        }
//                    },
//                    leadingIcon = {
//                        Icon(imageVector = Icons.Filled.Link, "", tint = MaterialTheme.colorScheme.primary)
//                    },
//                    onValueChange = { urls.value = it },
//                    textStyle = TextStyle(
//                        brush = Brush.linearGradient(
//                            colors = Paletting.SP_GRADIENT
//                        ),
//                        fontFamily = FontFamily(Font(R.font.inter)),
//                        fontSize = 16.sp,
//                    ),
//                    label = {
//                        Text("URLs", color = Color.Gray)
//                    }
//                )
//
//                /* Ok button */
//                Button(
//                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
//                    border = BorderStroke(width = 1.dp, color = Color.Black),
//                    modifier = Modifier.constrainAs(buttons) {
//                        bottom.linkTo(parent.bottom, 4.dp)
//                        end.linkTo(parent.end, 12.dp)
//                        start.linkTo(parent.start, 12.dp)
//                        width = Dimension.wrapContent
//                    },
//                    onClick = {
//                        visibilityState.value = false
//
//                        addURLs(urls.value.split("\n"))
//                    },
//                ) {
//                    Icon(imageVector = Icons.Filled.Done, "")
//                    Spacer(modifier = Modifier.width(8.dp))
//                    Text(stringResource(R.string.done), fontSize = 14.sp)
//                }
//            }
//        }
//    }
//

}