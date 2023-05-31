package app.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.content.ContextCompat
import app.R
import app.activities.HomeActivity
import app.utils.ComposeUtils.RoomPopup
import app.utils.ComposeUtils.SyncplayishText


object PopupAPropos {

    @Composable
    fun HomeActivity.AProposPopup(visibilityState: MutableState<Boolean>) {
        return RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.85f,
            heightPercent = 0.5f,
            strokeWidth = 0f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {

                val (syncplaylogo, syncplaytitle1, syncplaytitle2, credits, github, solomode) = createRefs()

                Image(
                    painter = painterResource(R.drawable.syncplay_logo_gradient),
                    contentDescription = "",
                    modifier = Modifier
                        .constrainAs(syncplaylogo) {
                            top.linkTo(parent.top, 16.dp)
                            absoluteLeft.linkTo(parent.absoluteLeft, 16.dp)
                            width = Dimension.preferredValue(96.dp)
                            height = Dimension.ratio("1:1")
                        }
                )

                /** 1st title */
                SyncplayishText(
                    modifier = Modifier.constrainAs(syncplaytitle1) {
                        top.linkTo(syncplaylogo.top)
                        absoluteRight.linkTo(parent.absoluteRight, 2.dp)
                        absoluteLeft.linkTo(syncplaylogo.absoluteRight, 2.dp)
                        bottom.linkTo(syncplaytitle2.top)
                        width = Dimension.fillToConstraints
                    },
                    string = "Syncplay",
                    textAlign = TextAlign.Center,
                    size = 20f
                )

                /* The 2nd title */
                SyncplayishText(
                    modifier = Modifier.constrainAs(syncplaytitle2) {
                        top.linkTo(syncplaytitle1.bottom)
                        absoluteRight.linkTo(parent.absoluteRight, 2.dp)
                        absoluteLeft.linkTo(syncplaylogo.absoluteRight, 2.dp)
                        bottom.linkTo(syncplaylogo.bottom)
                        width = Dimension.fillToConstraints
                    },
                    string = "for Android",
                    textAlign = TextAlign.Center,
                    colorStops = listOf(Color(50, 222, 132), Color(4, 219, 107), Color(50, 222, 132)),
                    size = 20f
                )

                /* Credits */
                Column(modifier = Modifier.constrainAs(credits) {
                    top.linkTo(syncplaylogo.bottom, 4.dp)
                    end.linkTo(parent.end, 6.dp)
                    start.linkTo(parent.start, 6.dp)
                    bottom.linkTo(solomode.top, 4.dp)
                    width = Dimension.fillToConstraints
                    height = Dimension.wrapContent
                }) {
                    Text(color = MaterialTheme.colorScheme.primary, text = "• Version: 0.12.0", fontSize = 11.sp, maxLines = 1)
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
                }

                /* Solomode button */
                Button(
                    modifier = Modifier.constrainAs(solomode) {
                        bottom.linkTo(parent.bottom, 4.dp)
                        absoluteRight.linkTo(parent.absoluteRight, 12.dp)
                        absoluteLeft.linkTo(github.absoluteRight, 12.dp)
                        width = Dimension.percent(0.7f)
                    },
                    onClick = {
                        visibilityState.value = false

                        soloMode()
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Tv, "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.connect_solomode), textAlign = TextAlign.Center, fontSize = 14.sp)
                }

                Image(
                    painter = painterResource(R.drawable.github),
                    contentDescription = "",
                    modifier = Modifier
                        .clickable(
                            enabled = true,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(
                                bounded = true,
                                color = Color(100, 100, 100, 200)
                            )
                        ) {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.github.com/yuroyami/syncplay-android/releases"))
                            ContextCompat.startActivity(this@AProposPopup, browserIntent, null)
                        }
                        .constrainAs(github) {
                            top.linkTo(solomode.top, 4.dp)
                            bottom.linkTo(solomode.bottom, 4.dp)
                            absoluteLeft.linkTo(parent.absoluteLeft)
                            absoluteRight.linkTo(solomode.absoluteLeft)
                            height = Dimension.fillToConstraints
                            width = Dimension.ratio("1:1")
                        }
                )

            }
        }
    }
}