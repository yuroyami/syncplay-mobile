package com.yuroyami.syncplay.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.MR
import com.yuroyami.syncplay.compose.ComposeUtils.RoomPopup
import com.yuroyami.syncplay.compose.ComposeUtils.SyncplayishText
import com.yuroyami.syncplay.compose.ComposeUtils.gradientOverlay
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource

object PopupAPropos {


    @Composable
    fun AProposPopup(visibilityState: MutableState<Boolean>) {
        return RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.85f,
            heightPercent = 0.5f,
            strokeWidth = 0f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {

                Row {
                    Image(
                        painter = painterResource(MR.images.syncplay_logo_gradient),
                        contentDescription = "About Syncplay Mobile - Enter Solo mode",
                        modifier = Modifier.requiredSize(96.dp).gradientOverlay()
                    )

                    Column {
                        /** 1st title */
                        SyncplayishText(
                            string = "Syncplay",
                            textAlign = TextAlign.Center,
                            size = 20f
                        )

                        /* The 2nd title TODO: Platform-specific text with colors */
                        SyncplayishText(
                            string = "for Android & iOS",
                            textAlign = TextAlign.Center,
                            colorStops = listOf(Color(50, 222, 132), Color(4, 219, 107), Color(50, 222, 132)),
                            size = 20f
                        )

                    }
                }

                Text(color = MaterialTheme.colorScheme.primary, text = "• Version: 0.13.0", fontSize = 11.sp, maxLines = 1)
                Text(color = MaterialTheme.colorScheme.primary, text = "• Developed by: yuroyami", fontSize = 11.sp, maxLines = 1)
                Text(
                    color = MaterialTheme.colorScheme.primary,
                    text = "• This client is not official. Thanks to official Syncplay team for their amazing software.",
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
                Text(
                    color = MaterialTheme.colorScheme.primary,
                    text = "• Syncplay is officially available for Windows, macOS, and Linux.",
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
                Text(color = MaterialTheme.colorScheme.primary, text = "• Official Website: www.syncplay.pl", fontSize = 11.sp, maxLines = 1)



                Row {
                    /* Solo mode */
                    Button(
                        onClick = {
                            visibilityState.value = false

                            //TODO: soloMode()
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Tv, "")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(MR.strings.connect_solomode), textAlign = TextAlign.Center, fontSize = 14.sp)
                    }

                    Image(
                        painter = painterResource(MR.images.github),
                        contentDescription = "",
                        modifier = Modifier.aspectRatio(1f)
                            .clickable(
                                enabled = true,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(
                                    bounded = true,
                                    color = Color(100, 100, 100, 200)
                                )
                            ) {
                                //TODO: expect platform call for browser
                                //val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.github.com/yuroyami/syncplay-android/releases"))
                                //ContextCompat.startActivity(this@AProposPopup, browserIntent, null)
                            }
                    )
                }
            }
        }
    }
}