package com.yuroyami.syncplay.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.shared.MR
import com.yuroyami.syncplay.compose.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.utils.timeStamper
import com.yuroyami.syncplay.watchroom.p
import dev.icerock.moko.resources.compose.asFont
import dev.icerock.moko.resources.compose.stringResource

object CardUserInfo {

    @Composable
    fun UserInfoCard() {
        Card(
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            ComposeUtils.FancyText2(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp),
                string = "User info",
                solid = Color.Transparent,
                size = 18f,
                font = MR.fonts.Directive4.regular.asFont()!!
            )

            Column(
                modifier = Modifier
                    .padding(PaddingValues(start = 8.dp, top = 4.dp, bottom = 8.dp, end = 12.dp))
                    .verticalScroll(rememberScrollState())
            ) {
                val userlist = remember { p.session.userList }
                for (user in userlist) {
                    /* A row for Username, Filename, and File properties */
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        /* User's readiness icon */
                        Icon(
                            modifier = Modifier.size(Paletting.USER_INFO_IC_SIZE.dp),
                            imageVector = if (user.readiness) Icons.Filled.Check else Icons.Filled.Clear,
                            contentDescription = "",
                            tint = when (user.readiness) {
                                true -> Paletting.ROOM_USER_READY_ICON
                                false -> Paletting.ROOM_USER_UNREADY_ICON
                            }
                        )

                        /* User's 'person' icon, it doesn't change. */
                        Icon(
                            modifier = Modifier
                                .size(Paletting.USER_INFO_IC_SIZE.dp)
                                .gradientOverlay(),
                            imageVector = Icons.Filled.Person,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.primary
                        )

                        /* User's name */
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .fillMaxWidth(),
                            text = user.name,
                            lineHeight = (Paletting.USER_INFO_TXT_SIZE + 6).sp,
                            fontSize = (Paletting.USER_INFO_TXT_SIZE + 2).sp,
                            color = Paletting.OLD_SP_YELLOW,
                            fontWeight = if (user.name == p.session.currentUsername) FontWeight.W900 else FontWeight.W400
                        )
                    }

                    /* Filename row */
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                        /* Small spacer to align texts together */
                        Spacer(Modifier.width((Paletting.USER_INFO_IC_SIZE * 1.25).dp))

                        /* Small arrow to indicate user's file name */
                        Icon(
                            modifier = Modifier
                                .size(Paletting.USER_INFO_IC_SIZE.dp)
                                .gradientOverlay(),
                            imageVector = Icons.Filled.SubdirectoryArrowRight,
                            contentDescription = "",
                            tint = Paletting.OLD_SP_YELLOW
                        )

                        /* Actual filename */
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = Paletting.USER_INFO_TXT_SIZE.sp,
                            lineHeight = (Paletting.USER_INFO_TXT_SIZE + 4).sp,
                            color = Paletting.SP_CUTE_PINK,
                            text = user.file?.fileName ?: stringResource(MR.strings.room_details_nofileplayed),
                            fontWeight = FontWeight.W300
                        )
                    }

                    /* File properties row (only if file does exist) */
                    if (user.file != null) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                            /* Small spacer to align texts together */
                            Spacer(Modifier.width((Paletting.USER_INFO_IC_SIZE * 2.5).dp))

                            /* File properties */
                            val fileSize = user.file?.fileSize?.toDoubleOrNull()?.div(1000000.0)?.toString() ?: "???"
                            Text(
                                text = stringResource(
                                    MR.strings.room_details_file_properties,
                                    timeStamper(user.file?.fileDuration?.toLong() ?: 0),
                                    fileSize
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = (Paletting.USER_INFO_TXT_SIZE - 2).sp,
                                fontWeight = FontWeight.W300,
                                color = Paletting.SP_CUTE_PINK, /* Paletting.ROOM_USER_PERSON_ICON */
                            )
                        }
                    }
                }
            }
        }
    }

}