package app.room.ui.rightcards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.player.models.MediaFile
import app.protocol.models.User
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.frames.PanelFrame
import app.utils.FileComparison
import app.utils.timestampFromMillis
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_alone
import syncplaymobile.shared.generated.resources.room_card_title_user_info
import syncplaymobile.shared.generated.resources.room_details_file_properties
import syncplaymobile.shared.generated.resources.room_file_different
import syncplaymobile.shared.generated.resources.room_file_has
import syncplaymobile.shared.generated.resources.room_file_none
import syncplaymobile.shared.generated.resources.room_file_same
import kotlin.math.roundToLong

object CardUserInfo {

    /**
     * The roster: one row per user with a readiness square, the name, and whether their file
     * matches ours in the value column. A controller carries the accent edge, our own row sits on
     * a filled ground, and a tap opens the file line.
     */
    @Composable
    fun UserInfoCard(shape: Shape = Radius.panelShape) {
        val viewmodel = LocalRoomViewmodel.current
        val users by viewmodel.session.userList.collectAsState()
        val me = viewmodel.session.currentUsername
        val myFile = viewmodel.media

        PanelFrame(title = stringResource(Res.string.room_card_title_user_info), modifier = Modifier.fillMaxSize(), shape = shape) {
            if (users.size <= 1) {
                Text(
                    text = stringResource(Res.string.room_alone),
                    style = Type.note,
                    color = palette.inkDim,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
                )
            }
            users.forEach { user -> UserRow(user, isSelf = user.name == me, myFile = myFile) }
        }
    }
}

@Composable
private fun UserRow(user: User, isSelf: Boolean, myFile: MediaFile?) {
    val p = palette
    var expanded by remember(user.name) { mutableStateOf(false) }
    val file = user.file
    val state: StringResource = when {
        file == null -> Res.string.room_file_none
        myFile == null -> Res.string.room_file_has
        FileComparison.sameFilename(myFile.fileName, file.fileName) -> Res.string.room_file_same
        else -> Res.string.room_file_different
    }

    ListRow(
        modifier = if (isSelf) Modifier.background(p.ink.copy(alpha = 0.06f)) else Modifier,
        onClick = if (file != null) ({ expanded = !expanded }) else null,
        selected = user.isController,
    ) {
        val square = Modifier.size(6.dp)
        Box(if (user.readiness) square.background(p.ok, Radius.tightShape) else square.border(Space.hair, p.bad, Radius.tightShape))
        RowGap(Space.gapTight + 2.dp)
        RowLabel(user.name)
        RowValue(stringResource(state), accent = state == Res.string.room_file_same)
    }
    if (expanded && file != null) {
        val megabytes = file.fileSize.toDoubleOrNull()?.div(1_000_000.0)?.let { ((it * 10).roundToLong() / 10.0).toString() } ?: "?"
        val duration = timestampFromMillis(((file.fileDuration ?: 0.0) * 1000).toLong())
        Text(
            text = file.fileName + "\n" + stringResource(Res.string.room_details_file_properties, duration, megabytes),
            style = Type.note,
            color = p.inkDim,
            modifier = Modifier.padding(start = Space.gutter + Space.gap + 2.dp, end = Space.gutter, bottom = Space.gapTight),
        )
    }
}
