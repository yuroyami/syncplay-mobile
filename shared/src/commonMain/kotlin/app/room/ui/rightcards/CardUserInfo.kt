package app.room.ui.rightcards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.player.models.MediaFile
import app.preferences.Preferences.USER_INFO_VIEW
import app.preferences.set
import app.preferences.watchPref
import app.protocol.models.User
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.Text
import app.uicomponents.frames.PanelFrame
import app.utils.FileComparison
import app.utils.timestampFromMillis
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
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
import syncplaymobile.shared.generated.resources.room_roster_view_compact
import syncplaymobile.shared.generated.resources.room_roster_view_files
import syncplaymobile.shared.generated.resources.room_roster_view_standard

/** The three ways to read the roster. Persisted, so the panel opens the way it was left. */
private enum class RosterView(val key: String, val icon: ImageVector, val label: StringResource) {
    Compact("compact", Icons.Filled.ViewCompact, Res.string.room_roster_view_compact),
    Standard("standard", Icons.AutoMirrored.Filled.ViewList, Res.string.room_roster_view_standard),
    Files("files", Icons.Filled.Folder, Res.string.room_roster_view_files),
}

object CardUserInfo {

    /**
     * The roster, three ways. Standard: one row per user with a readiness square, the name and
     * whether their file matches ours, a tap opening the file line. Compact: the same people as
     * dense chips, for a full room. By file: users grouped under the file each has loaded, our
     * own group marked, so who is on the wrong file is one glance.
     */
    @Composable
    fun UserInfoCard(shape: Shape = Radius.panelShape) {
        val viewmodel = LocalRoomViewmodel.current
        val users by viewmodel.session.userList.collectAsState()
        val me = viewmodel.session.currentUsername
        val myFile = viewmodel.media
        val p = palette
        val scope = rememberCoroutineScope { Dispatchers.IO }
        val viewKey by USER_INFO_VIEW.watchPref()
        val view = RosterView.entries.firstOrNull { it.key == viewKey } ?: RosterView.Standard

        PanelFrame(
            title = stringResource(Res.string.room_card_title_user_info),
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            actions = {
                RosterView.entries.forEach { v ->
                    GlyphButton(v.icon, name = stringResource(v.label), target = Space.row, tint = if (v == view) p.accent else p.inkDim) {
                        scope.launch { USER_INFO_VIEW.set(v.key) }
                    }
                }
            },
        ) {
            if (users.size <= 1) {
                Text(
                    text = stringResource(Res.string.room_alone),
                    style = Type.note,
                    color = p.inkDim,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gap),
                )
            }
            when (view) {
                RosterView.Standard -> users.forEach { user -> UserRow(user, isSelf = user.name == me, myFile = myFile) }
                RosterView.Compact -> FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gap, vertical = Space.gapTight),
                    horizontalArrangement = Arrangement.spacedBy(Space.gapTight),
                    verticalArrangement = Arrangement.spacedBy(Space.gapTight),
                ) {
                    users.forEach { user -> UserChip(user, isSelf = user.name == me) }
                }
                RosterView.Files -> {
                    val groups = users.groupBy { it.file?.fileName }
                    // Our own file first, then the other files, the file-less last.
                    val ordered = groups.entries.sortedWith(
                        compareBy<Map.Entry<String?, List<User>>> { it.key == null }
                            .thenBy { !(myFile != null && it.key != null && FileComparison.sameFilename(myFile.fileName, it.key)) }
                            .thenBy { it.key ?: "" },
                    )
                    ordered.forEach { (fileName, group) ->
                        val mine = myFile != null && fileName != null && FileComparison.sameFilename(myFile.fileName, fileName)
                        FileGroupHeading(fileName, mine, group.firstOrNull()?.file)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gap, vertical = Space.gapTight),
                            horizontalArrangement = Arrangement.spacedBy(Space.gapTight),
                            verticalArrangement = Arrangement.spacedBy(Space.gapTight),
                        ) {
                            group.forEach { user -> UserChip(user, isSelf = user.name == me) }
                        }
                    }
                }
            }
        }
    }

    /** A file line: the name, its duration, and whether it is ours or nobody's. */
    @Composable
    private fun FileGroupHeading(fileName: String?, mine: Boolean, file: MediaFile?) {
        val p = palette
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = Space.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(if (fileName == null) p.inkFaint else if (mine) p.ok else p.warn, Radius.tightShape))
            RowGap(Space.gapTight + 2.dp)
            Text(
                text = fileName ?: stringResource(Res.string.room_file_none),
                style = Type.value,
                color = if (fileName == null) p.inkDim else p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (file != null) {
                RowGap()
                Text(timestampFromMillis(((file.fileDuration ?: 0.0) * 1000).toLong()), style = Type.value, color = p.inkDim, maxLines = 1)
            }
        }
    }

    /** A 30dp chip: the readiness square and the name; a controller carries the accent edge. */
    @Composable
    private fun UserChip(user: User, isSelf: Boolean) {
        val p = palette
        val square = Modifier.size(6.dp)
        Row(
            modifier = Modifier
                .height(30.dp)
                .clip(Radius.controlShape)
                .background(if (isSelf) p.ink.copy(alpha = 0.08f) else Color.Transparent)
                .border(Space.hair, if (user.isController) p.accent else p.rule, Radius.controlShape)
                .padding(horizontal = Space.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(if (user.readiness) square.background(p.ok, Radius.tightShape) else square.border(Space.hair, p.bad, Radius.tightShape))
            RowGap(Space.gapTight + 2.dp)
            Text(user.name, style = Type.value, color = p.ink, maxLines = 1)
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
