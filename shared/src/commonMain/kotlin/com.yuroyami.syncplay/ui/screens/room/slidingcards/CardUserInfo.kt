package com.yuroyami.syncplay.ui.screens.room.slidingcards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.gradientOverlay
import com.yuroyami.syncplay.ui.screens.adam.LocalRoomViewmodel
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.utils.timeStamper
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_card_title_user_info
import syncplaymobile.shared.generated.resources.room_details_file_properties
import syncplaymobile.shared.generated.resources.room_details_nofileplayed
import syncplaymobile.shared.generated.resources.user_key

object CardUserInfo {

    @Composable
    fun UserInfoCard() {
        val viewmodel = LocalRoomViewmodel.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f)),
        ) {
            FlexibleText(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp),
                text = stringResource(Res.string.room_card_title_user_info),
                fillingColors = Theming.SP_GRADIENT,
                size = 18f,
                font = Font(Res.font.Directive4_Regular)
            )

            val userlist by viewmodel.session.userList.collectAsState()

            LazyColumn(
                modifier = Modifier
                    .padding(PaddingValues(start = 8.dp, top = 4.dp, bottom = 8.dp, end = 12.dp))
            ) {
                items(userlist) { user ->
                    /* A row for Username, Filename, and File properties */
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        /* User's readiness icon */
                        Icon(
                            modifier = Modifier.size(Theming.USER_INFO_IC_SIZE.dp),
                            imageVector = if (user.readiness) Icons.Filled.Check else Icons.Filled.Clear,
                            contentDescription = "",
                            tint = when (user.readiness) {
                                true -> Theming.ROOM_USER_READY_ICON
                                false -> Theming.ROOM_USER_UNREADY_ICON
                            }
                        )



                        if (user.isController) {
                            /* If user is a controller (room operator), add corresponding icon */
                            Image(
                                modifier = Modifier.size((Theming.USER_INFO_IC_SIZE-1).dp).padding(2.dp),
                                painter = painterResource(Res.drawable.user_key),
                                contentDescription = null
                            )
                        } else {
                            /* User's 'person' icon if he's not a room operatir */
                            Icon(
                                modifier = Modifier
                                    .size(Theming.USER_INFO_IC_SIZE.dp)
                                    .gradientOverlay(),
                                imageVector = Icons.Filled.Person,
                                contentDescription = "",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        /* User's name */
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .fillMaxWidth(),
                            text = user.name,
                            lineHeight = (Theming.USER_INFO_TXT_SIZE + 6).sp,
                            fontSize = (Theming.USER_INFO_TXT_SIZE + 2).sp,
                            color = Theming.OLD_SP_YELLOW,
                            fontWeight = if (user.name == viewmodel.session.currentUsername) FontWeight.W900 else FontWeight.W400
                        )
                    }

                    /* Filename row */
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                        /* Small spacer to align texts together */
                        Spacer(Modifier.width((Theming.USER_INFO_IC_SIZE * 1.25).dp))

                        /* Small arrow to indicate user's file name */
                        Icon(
                            modifier = Modifier
                                .size(Theming.USER_INFO_IC_SIZE.dp)
                                .gradientOverlay(),
                            imageVector = Icons.Filled.SubdirectoryArrowRight,
                            contentDescription = "",
                            tint = Theming.OLD_SP_YELLOW
                        )

                        /* Actual filename */
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = Theming.USER_INFO_TXT_SIZE.sp,
                            lineHeight = (Theming.USER_INFO_TXT_SIZE + 4).sp,
                            color = Theming.SP_CUTE_PINK,
                            text = user.file?.fileName ?: stringResource(Res.string.room_details_nofileplayed),
                            fontWeight = FontWeight.W300
                        )
                    }

                    /* File properties row (only if file does exist) */
                    if (user.file != null) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                            /* Small spacer to align texts together */
                            Spacer(Modifier.width((Theming.USER_INFO_IC_SIZE * 2.5).dp))

                            /* File properties */
                            val fileSize = user.file?.fileSize?.toDoubleOrNull()?.div(1000000.0)?.toString() ?: "???"
                            Text(
                                text = stringResource(
                                    Res.string.room_details_file_properties, timeStamper(user.file?.fileDuration?.toLong() ?: 0),
                                    fileSize
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = (Theming.USER_INFO_TXT_SIZE - 2).sp,
                                fontWeight = FontWeight.W300,
                                color = Theming.SP_CUTE_PINK, /* Paletting.ROOM_USER_PERSON_ICON */
                            )
                        }
                    }
                }
            }
        }
    }

}