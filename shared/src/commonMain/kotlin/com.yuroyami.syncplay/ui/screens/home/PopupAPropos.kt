package com.yuroyami.syncplay.ui.screens.home

import SyncplayMobile.shared.BuildConfig
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
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.ui.components.SyncplayPopup
import com.yuroyami.syncplay.ui.components.SyncplayishText
import com.yuroyami.syncplay.ui.screens.adam.LocalGlobalViewmodel
import com.yuroyami.syncplay.viewmodels.HomeViewmodel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.about_client_is_unofficial_disclaimer
import syncplaymobile.shared.generated.resources.about_client_platforms
import syncplaymobile.shared.generated.resources.about_developed_by
import syncplaymobile.shared.generated.resources.about_official_website
import syncplaymobile.shared.generated.resources.about_version
import syncplaymobile.shared.generated.resources.connect_solomode
import syncplaymobile.shared.generated.resources.github
import syncplaymobile.shared.generated.resources.syncplay_logo_gradient

object PopupAPropos {

    @Composable
    fun AProposPopup(visibilityState: MutableState<Boolean>, homeViewmodel: HomeViewmodel) {
        val globalViewmodel = LocalGlobalViewmodel.current

        return SyncplayPopup(
            dialogOpen = visibilityState.value,
            strokeWidth = 0f,
            onDismiss = { visibilityState.value = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround
            ) {

                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        imageVector = vectorResource(Res.drawable.syncplay_logo_gradient),
                        contentDescription = "",
                        modifier = Modifier.requiredSize(96.dp)
                        //      .radiantOverlay(offset = Offset(x = 50f, y = 65f))
                    )

                    Spacer(Modifier.width(10.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceAround
                    ) {
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

                Text(
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.primary, text = stringResource(Res.string.about_version, BuildConfig.APP_VERSION), fontSize = 11.sp, maxLines = 1
                )

                Text(
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.primary, text = stringResource(Res.string.about_developed_by), fontSize = 11.sp, maxLines = 1
                )

                Text(
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.primary,
                    text = stringResource(Res.string.about_client_is_unofficial_disclaimer),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )

                Text(
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.primary,
                    text = stringResource(Res.string.about_client_platforms),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )

                Text(
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.primary, text = stringResource(Res.string.about_official_website), fontSize = 11.sp, maxLines = 1
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /* Solo mode */
                    Button(
                        modifier = Modifier.wrapContentWidth(),
                        onClick = {
                            visibilityState.value = false

                            globalViewmodel.viewModelScope.launch {
                                //Passing null to indicate we're in offline mode
                                homeViewmodel.joinRoom(null)
                            }
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Tv, "")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.connect_solomode), textAlign = TextAlign.Center, fontSize = 14.sp)
                    }

                    val uriHandler = LocalUriHandler.current
                    Image(
                        painter = painterResource(Res.drawable.github),
                        contentDescription = "",
                        modifier = Modifier.size(64.dp)
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