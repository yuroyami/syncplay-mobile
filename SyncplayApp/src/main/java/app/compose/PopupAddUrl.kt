package app.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
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
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.net.toUri
import app.R
import app.activities.WatchActivity
import app.ui.Paletting
import app.utils.ComposeUtils.FancyText2
import app.utils.ComposeUtils.RoomPopup
import app.utils.ExoUtils.injectVideo
import app.utils.MiscUtils.toasty

object PopupAddUrl {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
    @Composable
    fun WatchActivity.AddUrlPopup(visibilityState: MutableState<Boolean>) {
        return RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.8f,
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
                FancyText2(
                    modifier = Modifier.constrainAs(title) {
                        top.linkTo(parent.top, 12.dp)
                        end.linkTo(parent.end)
                        start.linkTo(parent.start)
                    },
                    string = "Load media from URL",
                    solid = Color.Black,
                    size = 18f,
                    font = Font(R.font.directive4bold)
                )

                /* Title's subtext */
                Text(
                    text = "Make sure to provide direct links (for example: www.example.com/video.mp4). YouTube and other media streaming services are not supported yet.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily(Font(R.font.inter_regular)),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    modifier = Modifier.constrainAs(soustitre) {
                        top.linkTo(title.bottom, 6.dp)
                        end.linkTo(parent.end, 12.dp)
                        start.linkTo(parent.start, 12.dp)
                        width = Dimension.percent(0.6f)
                    })

                /* The URL input box */
                val url = remember { mutableStateOf("") }
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
                    singleLine = true,
                    value = url.value,
                    colors = TextFieldDefaults.textFieldColors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        containerColor = Color.DarkGray
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            url.value = clipboardManager.getText().toString()
                            toasty("Pasted clipboard content")
                        }) {
                            Icon(imageVector = Icons.Filled.ContentPaste, "", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Link, "", tint = MaterialTheme.colorScheme.primary)
                    },
                    onValueChange = { url.value = it },
                    textStyle = TextStyle(
                        brush = Brush.linearGradient(
                            colors = Paletting.SP_GRADIENT
                        ),
                        fontFamily = FontFamily(Font(R.font.inter)),
                        fontSize = 16.sp,
                    ),
                    label = {
                        Text("URL Address", color = Color.Gray)
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

                        if (url.value.trim().isNotBlank()) {
                            injectVideo(url.value.trim().toUri(), isUrl = true)
                        }

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