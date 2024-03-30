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
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.yuroyami.syncplay.compose.ComposeUtils.FancyText2
import com.yuroyami.syncplay.compose.ComposeUtils.RoomPopup
import com.yuroyami.syncplay.compose.getRegularFont
import com.yuroyami.syncplay.filepicking.DirectoryPicker
import com.yuroyami.syncplay.lyricist.rememberStrings
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_SP_MEDIA_DIRS
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueFlow
import com.yuroyami.syncplay.settings.writeValue
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.utils.PlaylistUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res

object PopupMediaDirs {

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun MediaDirsPopup(visibilityState: MutableState<Boolean>) {
        val localz = rememberStrings()
        val scope = rememberCoroutineScope { Dispatchers.IO }

        var cleardialog by remember { mutableStateOf(false) }

        RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.9f,
            heightPercent = 0.85f,
            strokeWidth = 0.5f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {

            var directoryPicker by remember { mutableStateOf(false) }
            DirectoryPicker(
                show = directoryPicker,
                title = "Select directory to save playlist to as a file"
            ) { directoryUri ->
                directoryPicker = false
                if (directoryUri == null) return@DirectoryPicker

                scope.launch {
                    PlaylistUtils.saveFolderPathAsMediaDirectory(directoryUri)
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp)
            ) {

                /* The title */
                FancyText2(
                    modifier = Modifier.fillMaxWidth(),
                    string = localz.strings.mediaDirectories,
                    solid = Color.Black,
                    size = 18f,
                    font = Font(Res.font.Directive4_Regular)
                )

                /* Title's subtext */
                Text(
                    text = localz.strings.mediaDirectoriesBrief,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 14.sp,
                    fontSize = 10.sp,
                    fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                    textAlign = TextAlign.Center,
                )

                /* The card that holds the media directories */
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f)
                        .align(Alignment.CenterHorizontally).padding(6.dp),
                    shape = RoundedCornerShape(size = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray)
                ) {
                    val dirs = valueFlow(PREF_SP_MEDIA_DIRS, emptySet<String>()).collectAsState(initial = emptySet())

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

                                    Text(
                                        text = item.substringAfterLast("/"),
                                        fontFamily = FontFamily(getRegularFont()),
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


                                    //Item action: Delete
                                    DropdownMenuItem(
                                        text = { Text(color = Color.LightGray, text = localz.strings.mediaDirectoriesDelete, fontSize = 12.sp) },
                                        leadingIcon = { Icon(imageVector = Icons.Filled.Delete, "", tint = Color.LightGray) },
                                        onClick = {
                                            itemMenuState.value = false

                                            scope.launch {
                                                val paths = valueBlockingly(PREF_SP_MEDIA_DIRS, emptySet<String>()).toMutableSet()

                                                if (paths.contains(item)) {
                                                    paths.remove(item)
                                                    writeValue(PREF_SP_MEDIA_DIRS, paths)
                                                }
                                            }
                                        }
                                    )

                                    Text(
                                        text = "Path: $item",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                        fontSize = 8.sp,
                                        lineHeight = 9.sp,
                                        color = Color.LightGray,
                                        overflow = TextOverflow.Visible,
                                        style = TextStyle(
                                            lineBreak = LineBreak.Simple
                                        )
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
                            cleardialog = true
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
                            directoryPicker = true
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


        if (cleardialog) {
            AlertDialog(
                onDismissRequest = { cleardialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        cleardialog = false
                        scope.launch {
                            writeValue(PREF_SP_MEDIA_DIRS, emptySet<String>())
                        }
                    }) { Text(localz.strings.yes) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        cleardialog = false
                    }) { Text(localz.strings.no) }
                },
                text = { Text(localz.strings.settingResetdefaultDialog) }
            )
        }
    }
}