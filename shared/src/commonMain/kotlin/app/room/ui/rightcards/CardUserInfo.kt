package app.room.ui.rightcards

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalRoomViewmodel
import app.theme.Theming
import app.utils.timestampFromMillis
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_card_title_user_info
import syncplaymobile.shared.generated.resources.room_details_file_properties
import syncplaymobile.shared.generated.resources.room_details_nofileplayed
import syncplaymobile.shared.generated.resources.user_key

object CardUserInfo {

    @Composable
    fun UserInfoCard() {
        val viewmodel = LocalRoomViewmodel.current
        val uiOpacity by viewmodel.uiState.uiOpacity.collectAsState()

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(uiOpacity)),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        ) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp),
                text = stringResource(Res.string.room_card_title_user_info),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
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
                            tint = if (user.readiness) Theming.READY_GREEN else Theming.UNREADY_RED
                        )

                        if (user.isController) {
                            Image(
                                modifier = Modifier.size((Theming.USER_INFO_IC_SIZE - 1).dp).padding(2.dp),
                                painter = painterResource(Res.drawable.user_key),
                                contentDescription = null
                            )
                        } else {
                            Icon(
                                modifier = Modifier.size(Theming.USER_INFO_IC_SIZE.dp),
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
                            color = if (user.name == viewmodel.session.currentUsername) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (user.name == viewmodel.session.currentUsername) FontWeight.W700 else FontWeight.W400
                        )
                    }

                    /* Filename row */
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width((Theming.USER_INFO_IC_SIZE * 1.25).dp))

                        Icon(
                            modifier = Modifier.size(Theming.USER_INFO_IC_SIZE.dp),
                            imageVector = Icons.Filled.SubdirectoryArrowRight,
                            contentDescription = "",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = Theming.USER_INFO_TXT_SIZE.sp,
                            lineHeight = (Theming.USER_INFO_TXT_SIZE + 4).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = user.file?.fileName ?: stringResource(Res.string.room_details_nofileplayed),
                            fontWeight = FontWeight.W300,
                        )
                    }

                    /* File properties row (only if file does exist) */
                    if (user.file != null) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

                            /* Small spacer to align texts together */
                            Spacer(Modifier.width((Theming.USER_INFO_IC_SIZE * 2.5).dp))

                            /* File properties */
                            val fileSize = user.file?.fileSize?.toDoubleOrNull()?.div(1000000.0)?.toString() ?: "???"
                            val fileDuration = timestampFromMillis(user.file?.fileDuration?.times(1000)?.toLong() ?: 0)
                            Text(
                                text = stringResource(
                                    Res.string.room_details_file_properties,
                                    fileDuration,
                                    fileSize
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = (Theming.USER_INFO_TXT_SIZE - 2).sp,
                                fontWeight = FontWeight.W300,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }

}