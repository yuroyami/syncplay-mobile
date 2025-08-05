package com.yuroyami.syncplay.screens.room.subcomponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.yuroyami.syncplay.components.ComposeUtils
import com.yuroyami.syncplay.components.ComposeUtils.FancyIcon2
import com.yuroyami.syncplay.components.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.components.syncplayFont
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.ui.Paletting.ROOM_ICON_SIZE
import com.yuroyami.syncplay.utils.CommonUtils
import com.yuroyami.syncplay.utils.loggy
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_addmedia_offline
import syncplaymobile.shared.generated.resources.room_addmedia_online
import syncplaymobile.shared.generated.resources.room_button_desc_add

@Composable
fun RoomMediaAdderButton() {
    val viewmodel = LocalViewmodel.current
    val hasVideo by viewmodel.hasVideo.collectAsState()

    var addMediaCardVisibility by remember { mutableStateOf(!viewmodel.hasDoneStartupSlideAnimation) }

    val videoPicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = CommonUtils.vidExs)) { file ->
        file?.path?.let {
            loggy(it, 0)
            viewmodel.player?.injectVideo(it, false)
        }
    }

    Box(modifier = Modifier.padding(4.dp)) {
        AddVideoButton(
            modifier = Modifier,
            expanded = !hasVideo,
            onClick = {
                addMediaCardVisibility = !addMediaCardVisibility
                //TODO controlcardvisible = false
            }
        )

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
            expanded = addMediaCardVisibility,
            properties = PopupProperties(
                dismissOnBackPress = true,
                focusable = true,
                dismissOnClickOutside = true
            ),
            onDismissRequest = { addMediaCardVisibility = false }) {

            ComposeUtils.FancyText2(
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .padding(horizontal = 2.dp),
                string = "Add media",
                solid = Color.Black,
                size = 14f,
                font = syncplayFont
            )

            //From storage
            DropdownMenuItem(text = {
                Row(verticalAlignment = CenterVertically) {
                    FancyIcon2(
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
            }, onClick = {
                addMediaCardVisibility = false
                videoPicker.launch()
            })

            //From network URL
            DropdownMenuItem(text = {
                Row(verticalAlignment = CenterVertically) {
                    FancyIcon2(
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
            }, onClick = {
                addMediaCardVisibility = false
                //TODO addurlpopupstate.value = true
            })
        }
    }
}

@Composable
fun AddVideoButton(modifier: Modifier, expanded: Boolean, onClick: () -> Unit) {
    if (!expanded) {
        FancyIcon2(
            modifier = modifier,
            icon = Icons.Filled.AddToQueue, size = Paletting.ROOM_ICON_SIZE + 6, shadowColor = Color.Black,
            onClick = {
                onClick.invoke()
            })
    } else {
        Surface(
            modifier = modifier.width(150.dp).height(48.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
            onClick = { onClick.invoke() },
            contentColor = Color.DarkGray.copy(0.5f)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
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
