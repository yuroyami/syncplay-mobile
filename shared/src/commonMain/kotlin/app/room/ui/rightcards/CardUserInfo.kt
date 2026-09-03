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
import app.uicomponents.controls.pressFeedback
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.Feedback
import app.theme.Motion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalRoomViewmodel
import app.LocalRoomUiState
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
import app.uicomponents.controls.Icon
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.RowValue
import app.uicomponents.controls.SecondaryAction
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
import syncplaymobile.shared.generated.resources.room_details_user_position
import syncplaymobile.shared.generated.resources.room_file_different
import syncplaymobile.shared.generated.resources.room_file_has
import syncplaymobile.shared.generated.resources.room_file_none
import syncplaymobile.shared.generated.resources.room_file_same
import syncplaymobile.shared.generated.resources.room_user_not_ready_label
import syncplaymobile.shared.generated.resources.room_user_unmute
import syncplaymobile.shared.generated.resources.room_user_report
import syncplaymobile.shared.generated.resources.room_user_mute
import syncplaymobile.shared.generated.resources.room_user_ready_label
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
                ViewToggler(view) { next -> scope.launch { USER_INFO_VIEW.set(next.key) } }
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

    /** One key that cycles the views; its glyph scales out and the next one scales in. */
    @Composable
    private fun ViewToggler(view: RosterView, onNext: (RosterView) -> Unit) {
        val p = palette
        val source = remember { MutableInteractionSource() }
        val name = stringResource(view.label)
        val next = RosterView.entries[(view.ordinal + 1) % RosterView.entries.size]
        Box(
            modifier = Modifier
                .size(Space.row)
                .clip(Radius.controlShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button) { Feedback.tick(); onNext(next) }
                .hoverable(source)
                .semantics { contentDescription = name }
                .controlStates(source, Radius.controlShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .pressFeedback(source),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = view,
                transitionSpec = {
                    (scaleIn(Motion.move(), initialScale = 0.6f) + fadeIn(Motion.move()))
                        .togetherWith(scaleOut(Motion.quick(), targetScale = 0.6f) + fadeOut(Motion.quick()))
                },
                label = "rosterView",
            ) { v ->
                Icon(v.icon, contentDescription = null, tint = p.accent, modifier = Modifier.size(Space.glyph))
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
        val spoken = readinessLabel(user)
        Row(
            modifier = Modifier
                .height(30.dp)
                .semantics(mergeDescendants = true) { contentDescription = spoken }
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

/** "name, ready" or "name, not ready": the square's colour, in words, for screen readers. */
@Composable
private fun readinessLabel(user: User): String =
    stringResource(if (user.readiness) Res.string.room_user_ready_label else Res.string.room_user_not_ready_label, user.name)

/** A report the maintainer can act on: who, in which room, and on which build. */
private fun reportUserUrl(username: String): String {
    val body = "Reporting a user in a Synkplay room.\n\nUser: $username\nWhat happened:\n"
    return "https://github.com/yuroyami/syncplay-mobile/issues/new?title=" +
        "[Report]%20user%20report&body=" + body.replace(" ", "%20").replace("\n", "%0A")
}

@Composable
private fun UserRow(user: User, isSelf: Boolean, myFile: MediaFile?) {
    val p = palette
    val uriHandler = LocalUriHandler.current
    var expanded by remember(user.name) { mutableStateOf(false) }
    val file = user.file
    val state: StringResource = when {
        file == null -> Res.string.room_file_none
        myFile == null -> Res.string.room_file_has
        FileComparison.sameFilename(myFile.fileName, file.fileName) -> Res.string.room_file_same
        else -> Res.string.room_file_different
    }
    val spoken = readinessLabel(user) + ", " + stringResource(state)

    ListRow(
        modifier = (if (isSelf) Modifier.background(p.ink.copy(alpha = 0.06f)) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        // Always expandable now: even a user with no file can be muted from here.
        onClick = { expanded = !expanded },
        selected = user.isController,
    ) {
        val square = Modifier.size(6.dp)
        Box(if (user.readiness) square.background(p.ok, Radius.tightShape) else square.border(Space.hair, p.bad, Radius.tightShape))
        RowGap(Space.gapTight + 2.dp)
        RowLabel(user.name)
        RowValue(stringResource(state), accent = state == Res.string.room_file_same)
    }
    if (expanded && !isSelf) {
        val ui = LocalRoomUiState.current
        val isMuted = user.name in ui.mutedUsers
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Space.gutter + Space.gap + 2.dp, end = Space.gutter, bottom = Space.gapTight),
            horizontalArrangement = Arrangement.spacedBy(Space.gapTight),
        ) {
            SecondaryAction(
                text = stringResource(if (isMuted) Res.string.room_user_unmute else Res.string.room_user_mute),
                onClick = { ui.toggleMute(user.name) },
            )
            SecondaryAction(
                text = stringResource(Res.string.room_user_report),
                onClick = { uriHandler.openUri(reportUserUrl(user.name)) },
            )
        }
    }
    if (expanded && file != null) {
        val megabytes = file.fileSize.toDoubleOrNull()?.div(1_000_000.0)?.let { ((it * 10).roundToLong() / 10.0).toString() } ?: "?"
        val duration = timestampFromMillis(((file.fileDuration ?: 0.0) * 1000).toLong())
        /* Where they actually are in the file. The server sends it on every List response and it
         * used to be dropped on decode, so the room could not say who was behind. */
        val at = user.position?.let { "\n" + stringResource(Res.string.room_details_user_position, timestampFromMillis((it * 1000).toLong())) } ?: ""
        Text(
            text = file.fileName + "\n" + stringResource(Res.string.room_details_file_properties, duration, megabytes) + at,
            style = Type.note,
            color = p.inkDim,
            modifier = Modifier.padding(start = Space.gutter + Space.gap + 2.dp, end = Space.gutter, bottom = Space.gapTight),
        )
    }
}
