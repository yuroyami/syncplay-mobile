package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.theme.Theming.ROOM_ICON_SIZE
import com.yuroyami.syncplay.ui.utils.FlexibleIcon
import com.yuroyami.syncplay.ui.utils.FancyText2
import com.yuroyami.syncplay.ui.utils.SyncplayPopup
import com.yuroyami.syncplay.ui.utils.getRegularFont
import com.yuroyami.syncplay.ui.utils.gradientOverlay
import com.yuroyami.syncplay.ui.utils.syncplayFont
import com.yuroyami.syncplay.utils.getText
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.vidExs
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.done
import syncplaymobile.shared.generated.resources.room_addmedia_offline
import syncplaymobile.shared.generated.resources.room_addmedia_online
import syncplaymobile.shared.generated.resources.room_button_desc_add

@Composable
fun RoomMediaAddButton(popupStateAddMedia: MutableState<Boolean>) {
    var showPopup by remember { popupStateAddMedia }

    val viewmodel = LocalRoomViewmodel.current

    val hasVideo by viewmodel.hasVideo.collectAsState()
    val popupStateAddUrl = remember { mutableStateOf(false) }

    val videoPicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = vidExs)) { file ->
        file?.path?.let {
            loggy(it)
            viewmodel.viewModelScope.launch {
                viewmodel.player?.injectVideo(it, false)
            }
        }
    }

    Box(modifier = Modifier.padding(4.dp)) {
        AddVideoButton(
            modifier = Modifier.padding(2.dp),
            expanded = !hasVideo,
            onClick = {
                showPopup = !showPopup
            }
        )

        val scope = rememberCoroutineScope()
        DropdownMenu(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map {
                    it.copy(alpha = 0.5f)
                })
            ),
            shape = RoundedCornerShape(8.dp),
            expanded = showPopup,
            properties = PopupProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            onDismissRequest = {
                scope.launch {
                    delay(50)
                    if (showPopup) showPopup = false
                }
            }
        ) {
            FancyText2(
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .padding(horizontal = 2.dp),
                string = "Add media",
                solid = Color.Black,
                size = 14f,
                font = syncplayFont
            )

            //From storage
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = CenterVertically) {
                        FlexibleIcon(
                            icon = Icons.Filled.CreateNewFolder,
                            size = ROOM_ICON_SIZE,
                            shadowColor = Color.Black
                        ) {}
                        Spacer(Modifier.width(8.dp))
                        Text(
                            color = Color.LightGray,
                            text = stringResource(Res.string.room_addmedia_offline),
                        )
                    }
                },
                onClick = {
                    showPopup = false
                    videoPicker.launch()
                }
            )

            //From network URL
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = CenterVertically) {
                        FlexibleIcon(
                            icon = Icons.Filled.AddLink,
                            size = ROOM_ICON_SIZE,
                            shadowColor = Color.Black
                        ) {}
                        Spacer(Modifier.width(8.dp))
                        Text(
                            color = Color.LightGray,
                            text = stringResource(Res.string.room_addmedia_online),
                        )
                    }
                },
                onClick = {
                    showPopup = false
                    popupStateAddUrl.value = true
                }
            )
        }
    }

    AddUrlPopup(visibilityState = popupStateAddUrl)
}

@Composable
fun AddVideoButton(modifier: Modifier, expanded: Boolean, onClick: () -> Unit) {
    if (!expanded) {
        FlexibleIcon(
            modifier = modifier,
            icon = Icons.Filled.AddToQueue, size = ROOM_ICON_SIZE + 6, shadowColor = Color.Black,
            onClick = onClick
        )
    } else {
        Surface(
            modifier = modifier.width(150.dp).height(48.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
            onClick = onClick,
            contentColor = Color.DarkGray.copy(0.5f)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
                    Icon(
                        tint = Color.DarkGray, imageVector = Icons.Filled.AddToQueue, contentDescription = "",
                        modifier = Modifier.size(32.dp).gradientOverlay() //.align(Alignment.Center)
                    )

                    Text(
                        modifier = Modifier.gradientOverlay(),
                        text = stringResource(Res.string.room_button_desc_add), textAlign = TextAlign.Center, maxLines = 1,
                        fontSize = 14.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


@Composable
fun AddUrlPopup(visibilityState: MutableState<Boolean>) {
    return SyncplayPopup(
        dialogOpen = visibilityState.value,
        strokeWidth = 0.5f,
        onDismiss = { visibilityState.value = false }
    ) {
        val scope = rememberCoroutineScope()
        val viewmodel = LocalRoomViewmodel.current
        val clipboard = LocalClipboard.current

        Column(
            modifier = Modifier.Companion.fillMaxSize().padding(6.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.Companion.CenterHorizontally
        ) {

            /* The title */
            FancyText2(
                string = "Load media from URL",
                solid = Color.Companion.Black,
                size = 18f,
                font = Font(Res.font.Directive4_Regular)
            )

            /* Title's subtext */
            Text(
                text = "Make sure to provide direct links (for example: www.example.com/video.mp4). YouTube and other media streaming services are not supported yet.",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontFamily = FontFamily(getRegularFont()),
                textAlign = TextAlign.Companion.Center,
                lineHeight = 14.sp,
                modifier = Modifier.Companion.fillMaxWidth(0.6f)
            )

            Spacer(Modifier.Companion.height(2.dp))

            /* The URL input box */
            val url = remember { mutableStateOf("") }
            TextField(
                modifier = Modifier.Companion.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                value = url.value,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Companion.DarkGray,
                    unfocusedContainerColor = Color.Companion.DarkGray,
                    disabledContainerColor = Color.Companion.DarkGray,
                    focusedIndicatorColor = Color.Companion.Transparent,
                    unfocusedIndicatorColor = Color.Companion.Transparent,
                    disabledIndicatorColor = Color.Companion.Transparent,
                ),
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            clipboard.getClipEntry()?.getText()?.let { clipboardData ->
                                url.value = clipboardData
                            }
                        }
                    }) {
                        Icon(imageVector = Icons.Filled.ContentPaste, "", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Link, "", tint = MaterialTheme.colorScheme.primary)
                },
                onValueChange = { url.value = it },
                textStyle = TextStyle(
                    brush = Brush.Companion.linearGradient(
                        colors = Theming.SP_GRADIENT
                    ),
                    fontFamily = FontFamily(getRegularFont()),
                    fontSize = 16.sp,
                ),
                label = {
                    Text("URL Address", color = Color.Companion.Gray)
                }
            )

            /* Ok button */
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                border = BorderStroke(width = 1.dp, color = Color.Companion.Black),
                onClick = {
                    visibilityState.value = false

                    if (url.value.trim().isNotBlank()) {
                        viewmodel.viewModelScope.launch {
                            viewmodel.player?.injectVideo(url.value.trim(), isUrl = true)
                        }
                    }

                },
            ) {
                Icon(imageVector = Icons.Filled.Done, "")
                Spacer(modifier = Modifier.Companion.width(8.dp))
                Text(stringResource(Res.string.done), fontSize = 14.sp)
            }
        }
    }
}