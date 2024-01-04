package com.yuroyami.syncplay.compose.popups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.yuroyami.syncplay.compose.ComposeUtils.FancyText2
import com.yuroyami.syncplay.compose.ComposeUtils.RoomPopup
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_SP_MEDIA_DIRS
import com.yuroyami.syncplay.settings.stringSetFlow
import com.yuroyami.syncplay.lyricist.rememberStrings
import com.yuroyami.syncplay.ui.Paletting
import org.jetbrains.compose.resources.Font
import syncplaymobile.generated.resources.Res

var wentForFilePick = false

object PopupMediaDirs {

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun MediaDirsPopup(visibilityState: MutableState<Boolean>) {
        val localz = rememberStrings()

        return RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.9f,
            heightPercent = 0.85f,
            strokeWidth = 0.5f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {
            //val context = LocalContext.current

            /*
            val dirResult = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
                onResult = { uri ->
                    if (this@MediaDirsPopup is WatchActivity) {
                        this@MediaDirsPopup.apply {
                            wentForFilePick = false
                        }
                    }

                    if (uri == null) return@rememberLauncherForActivityResult

                    lifecycleScope.launch(Dispatchers.IO) {
                        /** We need to use the takePersistableUriPermission in order
                         * to obtain permanent access to Uri across all activities.*/
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        applicationContext.contentResolver.takePersistableUriPermission(uri, takeFlags)


                        val folders = DATASTORE_GLOBAL_SETTINGS.obtainStringSet(PREF_SP_MEDIA_DIRS, emptySet()).toMutableSet()
                        if (!folders.contains(uri.toString())) {
                            folders.add(uri.toString())
                        } else {
                            toasty("Folder is already added.")
                        }

                        DATASTORE_GLOBAL_SETTINGS.writeStringSet(PREF_SP_MEDIA_DIRS, folders)
                    }
                }
            )

             */

            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp)
            ) {

                /* The title */
                FancyText2(
                    string = localz.strings.mediaDirectories,
                    solid = Color.Black,
                    size = 18f,
                    font = Font(Res.font.directive4_regular)
                )

                /* Title's subtext */
                Text(
                    text = localz.strings.mediaDirectoriesBrief,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 14.sp,
                    fontSize = 10.sp,
                    fontFamily = FontFamily(Font(Res.font.directive4_regular)),
                    textAlign = TextAlign.Center,
                )

                /* The card that holds the media directories */
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f)
                        .align(Alignment.CenterHorizontally).padding(6.dp),
                    shape = RoundedCornerShape(size = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray)
                ) {
                    val dirs = stringSetFlow(PREF_SP_MEDIA_DIRS, emptySet()).collectAsState(initial = emptySet())

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(66.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(dirs.value.toList()) { _, item ->

                            Box {
                                val itemMenuState = remember { mutableStateOf(false) }

                                Column(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = rememberRipple(color = Paletting.OLD_SP_PINK)
                                        ) { itemMenuState.value = true }, horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder, "",
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp)
                                    )

                                    val text = item //FIXME: .toUri().lastPathSegment?.substringAfterLast("/")

                                    Text(
                                        text = if (text?.contains("primary:") == true) text.substringAfter("primary:") else text ?: "",
                                        fontFamily = FontFamily(Font(Res.font.inter_regular)),
                                        fontSize = 8.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 10.sp,
                                        maxLines = 5,
                                        color = Color(35, 35, 35),
                                        modifier = Modifier.width(62.dp)
                                    )
                                }

                                DropdownMenu(
                                    modifier = Modifier.background(color = Color.DarkGray),
                                    expanded = itemMenuState.value,
                                    properties = PopupProperties(
                                        dismissOnBackPress = true,
                                        focusable = true,
                                        dismissOnClickOutside = true
                                    ),
                                    onDismissRequest = { itemMenuState.value = false }) {

                                    //Item action: Path
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                color = Color.LightGray,
                                                text = localz.strings.mediaDirectoriesShowFullPath,
                                                fontSize = 12.sp
                                            )
                                        },
                                        leadingIcon = { Icon(imageVector = Icons.Filled.Link, "", tint = Color.LightGray) },
                                        onClick = {
                                            itemMenuState.value = false

                                            /* FIXME
                                            val pathDialog = AlertDialog.Builder(context)
                                            val dialogClickListener = DialogInterface.OnClickListener { dialog, _ ->
                                                dialog.dismiss()
                                            }

                                            pathDialog.setMessage(item.toUri().path?.replace("/tree/primary:", "Storage//").toString())
                                                .setPositiveButton(getString(R.string.okay), dialogClickListener)
                                                .show()

                                             */
                                        }
                                    )

                                    //Item action: Delete
                                    DropdownMenuItem(
                                        text = { Text(color = Color.LightGray, text = localz.strings.mediaDirectoriesDelete, fontSize = 12.sp) },
                                        leadingIcon = { Icon(imageVector = Icons.Filled.Delete, "", tint = Color.LightGray) },
                                        onClick = {
                                            itemMenuState.value = false
                                            /* FIXME
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                val directories = DATASTORE_GLOBAL_SETTINGS.obtainStringSet(PREF_SP_MEDIA_DIRS, emptySet()).toMutableSet()
                                                if (directories.contains(item)) {
                                                    directories.remove(item)
                                                }

                                                DATASTORE_GLOBAL_SETTINGS.writeStringSet(PREF_SP_MEDIA_DIRS, directories)
                                            }

                                             */
                                        }
                                    )


                                }
                            }
                        }
                    }
                }

                /* The three buttons */
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceAround,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {

                    /* Clear All button */
                    Button(
                        border = BorderStroke(width = 1.dp, color = Color.Black),
                        modifier = Modifier.wrapContentWidth(),
                        onClick = {
                            /* FIXME
                            val clearDialog = AlertDialog.Builder(context)
                            val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
                                dialog.dismiss()
                                if (which == DialogInterface.BUTTON_POSITIVE) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        DATASTORE_GLOBAL_SETTINGS.writeStringSet(PREF_SP_MEDIA_DIRS, emptySet())
                                    }
                                }
                            }

                            clearDialog.setMessage(getString(R.string.setting_resetdefault_dialog))
                                .setPositiveButton(getString(R.string.yes), dialogClickListener)
                                .setNegativeButton(getString(R.string.no), dialogClickListener)
                                .show()

                             */
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.ClearAll, "")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(localz.strings.mediaDirectoriesClearAll, fontSize = 14.sp)
                    }

                    /* Add folder button */
                    Button(
                        border = BorderStroke(width = 1.dp, color = Color.Black),
                        modifier = Modifier.wrapContentWidth(),
                        onClick = {
                            /*
                            FIXME if (this@MediaDirsPopup is WatchActivity) {
                                this@MediaDirsPopup.apply {
                                    wentForFilePick = true
                                }
                            }
                            dirResult.launch(null)

                             */
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.CreateNewFolder, "")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(localz.strings.mediaDirectoriesAddFolder, fontSize = 14.sp)
                    }

                    /* Save button */
                    Button(
                        border = BorderStroke(width = 1.dp, color = Color.Black),
                        modifier = Modifier.wrapContentWidth(),
                        onClick = {
                            visibilityState.value = false
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Done, "")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(localz.strings.mediaDirectoriesSave, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}