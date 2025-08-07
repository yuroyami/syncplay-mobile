package com.yuroyami.syncplay.ui.screens.room.bottombar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.ui.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.ui.utils.FancyText2
import com.yuroyami.syncplay.ui.utils.SyncplayPopup
import com.yuroyami.syncplay.ui.utils.getRegularFont
import com.yuroyami.syncplay.ui.theme.Paletting
import com.yuroyami.syncplay.utils.getText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.done

object PopupAddUrl {


    @Composable
    fun AddUrlPopup(visibilityState: MutableState<Boolean>) {
        return SyncplayPopup(
            dialogOpen = visibilityState.value,
            strokeWidth = 0.5f,
            onDismiss = { visibilityState.value = false }
        ) {
            val scope = rememberCoroutineScope()
            val viewmodel = LocalViewmodel.current
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
                            colors = Paletting.SP_GRADIENT
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
                            viewmodel.player?.injectVideo(url.value.trim(), isUrl = true)
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
}