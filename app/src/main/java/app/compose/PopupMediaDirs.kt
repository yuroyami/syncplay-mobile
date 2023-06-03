package app.compose

import android.content.DialogInterface
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import app.R
import app.activities.WatchActivity
import app.datastore.DataStoreKeys.DATASTORE_GLOBAL_SETTINGS
import app.datastore.DataStoreKeys.PREF_SP_MEDIA_DIRS
import app.datastore.DataStoreUtils.ds
import app.datastore.DataStoreUtils.obtainStringSet
import app.datastore.DataStoreUtils.stringSetFlow
import app.datastore.DataStoreUtils.writeStringSet
import app.ui.Paletting
import app.utils.ComposeUtils.FancyText2
import app.utils.ComposeUtils.RoomPopup
import app.utils.MiscUtils.toasty
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.FlowRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PopupMediaDirs {

    @Composable
    fun ComponentActivity.MediaDirsPopup(visibilityState: MutableState<Boolean>) {
        return RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.9f,
            heightPercent = 0.85f,
            strokeWidth = 0.5f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {
            val context = LocalContext.current

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

            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {

                val (title, soustitre, mediadirs, buttons) = createRefs()

                /* The title */
                FancyText2(
                    modifier = Modifier.constrainAs(title) {
                        top.linkTo(parent.top, 12.dp)
                        end.linkTo(parent.end)
                        start.linkTo(parent.start)
                    },
                    string = stringResource(R.string.media_directories),
                    solid = Color.Black,
                    size = 18f,
                    font = Font(R.font.directive4bold)
                )

                /* Title's subtext */
                Text(
                    text = stringResource(R.string.media_directories_brief),
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 14.sp,
                    fontSize = 10.sp,
                    fontFamily = FontFamily(Font(R.font.inter)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.constrainAs(soustitre) {
                        top.linkTo(title.bottom, 6.dp)
                        end.linkTo(parent.end, 12.dp)
                        start.linkTo(parent.start, 12.dp)
                    })

                /* The card that holds the media directories */
                Card(
                    modifier = Modifier
                        .constrainAs(mediadirs) {
                            top.linkTo(soustitre.bottom, 8.dp)
                            absoluteLeft.linkTo(parent.absoluteLeft)
                            absoluteRight.linkTo(parent.absoluteRight)
                            bottom.linkTo(buttons.top, 12.dp)
                            width = Dimension.percent(0.9f)
                            height = Dimension.fillToConstraints
                        },
                    shape = RoundedCornerShape(size = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray)
                ) {
                    val dirs = DATASTORE_GLOBAL_SETTINGS.ds()
                        .stringSetFlow(PREF_SP_MEDIA_DIRS, emptySet()).collectAsState(initial = emptySet())

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

                                    val text = item.toUri().lastPathSegment?.substringAfterLast("/")

                                    Text(
                                        text = if (text?.contains("primary:") == true) text.substringAfter("primary:") else text ?: "",
                                        fontFamily = FontFamily(Font(R.font.cascadiacode)),
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
                                                text = stringResource(R.string.media_directories_show_full_path),
                                                fontSize = 12.sp
                                            )
                                        },
                                        leadingIcon = { Icon(imageVector = Icons.Filled.Link, "", tint = Color.LightGray) },
                                        onClick = {
                                            itemMenuState.value = false

                                            val pathDialog = AlertDialog.Builder(context)
                                            val dialogClickListener = DialogInterface.OnClickListener { dialog, _ ->
                                                dialog.dismiss()
                                            }

                                            pathDialog.setMessage(item.toUri().path?.replace("/tree/primary:", "Storage//").toString())
                                                .setPositiveButton(getString(R.string.okay), dialogClickListener)
                                                .show()
                                        }
                                    )

                                    //Item action: Delete
                                    DropdownMenuItem(
                                        text = { Text(color = Color.LightGray, text = stringResource(R.string.media_directories_delete), fontSize = 12.sp) },
                                        leadingIcon = { Icon(imageVector = Icons.Filled.Delete, "", tint = Color.LightGray) },
                                        onClick = {
                                            itemMenuState.value = false
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                val directories = DATASTORE_GLOBAL_SETTINGS.obtainStringSet(PREF_SP_MEDIA_DIRS, emptySet()).toMutableSet()
                                                if (directories.contains(item)) {
                                                    directories.remove(item)
                                                }

                                                DATASTORE_GLOBAL_SETTINGS.writeStringSet(PREF_SP_MEDIA_DIRS, directories)
                                            }
                                        }
                                    )


                                }
                            }
                        }
                    }
                }


                /* The three buttons */
                FlowRow(
                    mainAxisSpacing = 6.dp,
                    mainAxisAlignment = FlowMainAxisAlignment.Center,
                    modifier = Modifier.constrainAs(buttons) {
                        bottom.linkTo(parent.bottom, 4.dp)
                        end.linkTo(parent.end, 12.dp)
                        start.linkTo(parent.start, 12.dp)
                    }) {

                    /* Clear All button */
                    Button(
                        border = BorderStroke(width = 1.dp, color = Color.Black),
                        modifier = Modifier.wrapContentWidth(),
                        onClick = {
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
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.ClearAll, "")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.media_directories_clear_all), fontSize = 14.sp)
                    }

                    /* Add folder button */
                    Button(
                        border = BorderStroke(width = 1.dp, color = Color.Black),
                        modifier = Modifier.wrapContentWidth(),
                        onClick = {
                            if (this@MediaDirsPopup is WatchActivity) {
                                this@MediaDirsPopup.apply {
                                    wentForFilePick = true
                                }
                            }
                            dirResult.launch(null)
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.CreateNewFolder, "")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.media_directories_add_folder), fontSize = 14.sp)
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
                        Text(stringResource(R.string.media_directories_save), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}