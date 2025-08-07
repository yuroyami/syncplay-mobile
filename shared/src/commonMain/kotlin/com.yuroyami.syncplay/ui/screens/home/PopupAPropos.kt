package com.yuroyami.syncplay.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.ui.utils.SyncplayPopup
import com.yuroyami.syncplay.ui.utils.SyncplayishText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.connect_solomode
import syncplaymobile.shared.generated.resources.github
import syncplaymobile.shared.generated.resources.syncplay_logo_gradient

object PopupAPropos {

    @Composable
    fun AProposPopup(visibilityState: MutableState<Boolean>) {
        return SyncplayPopup(
            dialogOpen = visibilityState.value,
            strokeWidth = 0f,
            onDismiss = { visibilityState.value = false }
        ) {
            Column(
                modifier = Modifier.Companion.fillMaxWidth().padding(6.dp),
                horizontalAlignment = Alignment.Companion.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround
            ) {

                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Companion.CenterVertically
                ) {
                    Image(
                        imageVector = vectorResource(Res.drawable.syncplay_logo_gradient),
                        contentDescription = "",
                        modifier = Modifier.Companion.requiredSize(96.dp)
                        //      .radiantOverlay(offset = Offset(x = 50f, y = 65f))
                    )

                    Spacer(Modifier.Companion.width(10.dp))

                    Column(
                        horizontalAlignment = Alignment.Companion.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceAround
                    ) {
                        /** 1st title */
                        SyncplayishText(
                            string = "Syncplay",
                            textAlign = TextAlign.Companion.Center,
                            size = 20f
                        )

                        /* The 2nd title TODO: Platform-specific text with colors */
                        SyncplayishText(
                            string = "for Android & iOS",
                            textAlign = TextAlign.Companion.Center,
                            colorStops = listOf(Color(50, 222, 132), Color(4, 219, 107), Color(50, 222, 132)),
                            size = 20f
                        )

                    }
                }

                Text(
                    modifier = Modifier.Companion.fillMaxWidth(), textAlign = TextAlign.Companion.Start,
                    color = MaterialTheme.colorScheme.primary, text = "• Version: 0.14.0", fontSize = 11.sp, maxLines = 1
                )

                Text(
                    modifier = Modifier.Companion.fillMaxWidth(), textAlign = TextAlign.Companion.Start,
                    color = MaterialTheme.colorScheme.primary, text = "• Developed by: yuroyami", fontSize = 11.sp, maxLines = 1
                )

                Text(
                    modifier = Modifier.Companion.fillMaxWidth(), textAlign = TextAlign.Companion.Start,
                    color = MaterialTheme.colorScheme.primary,
                    text = "• This client is not official. Thanks to official Syncplay team for their amazing software.",
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )

                Text(
                    modifier = Modifier.Companion.fillMaxWidth(), textAlign = TextAlign.Companion.Start,
                    color = MaterialTheme.colorScheme.primary,
                    text = "• Syncplay is officially available for Windows, macOS, and Linux.",
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )

                Text(
                    modifier = Modifier.Companion.fillMaxWidth(), textAlign = TextAlign.Companion.Start,
                    color = MaterialTheme.colorScheme.primary, text = "• Official Website: www.syncplay.pl", fontSize = 11.sp, maxLines = 1
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Companion.CenterVertically
                ) {
                    /* Solo mode */
                    Button(
                        modifier = Modifier.Companion.wrapContentWidth(),
                        onClick = {
                            visibilityState.value = false

                            //TODO platformCallback?.onJoin(null) //Passing null to indicate we're in offline mode
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Tv, "")
                        Spacer(modifier = Modifier.Companion.width(8.dp))
                        Text(stringResource(Res.string.connect_solomode), textAlign = TextAlign.Companion.Center, fontSize = 14.sp)
                    }

                    val uriHandler = LocalUriHandler.current
                    Image(
                        painter = painterResource(Res.drawable.github),
                        contentDescription = "",
                        modifier = Modifier.Companion.size(64.dp)
                            .clickable(
                                enabled = true,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(
                                    bounded = true,
                                    color = Color(100, 100, 100, 200)
                                )
                            ) {
                                uriHandler.openUri("https://www.github.com/yuroyami/syncplay-mobile/releases")
                            }
                    )
                }
            }
        }
    }
}