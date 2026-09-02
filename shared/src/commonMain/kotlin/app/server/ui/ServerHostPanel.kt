package app.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.preferences.Preferences.SERVER_DISABLE_CHAT
import app.preferences.Preferences.SERVER_DISABLE_READY
import app.preferences.Preferences.SERVER_ISOLATE_ROOMS
import app.preferences.Preferences.SERVER_MOTD
import app.preferences.Preferences.SERVER_PASSWORD
import app.preferences.Preferences.SERVER_PORT
import app.preferences.settings.Render
import app.preferences.settings.enabledWhen
import app.preferences.watchPref
import app.server.ServerHostSession
import app.server.ServerLogEntry
import app.server.ServerLogLevel
import app.server.ServerStatus
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.DestructiveAction
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.GroupHeading
import app.uicomponents.controls.PrimaryAction
import app.uicomponents.controls.ProgressBar
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.Tag
import app.uicomponents.controls.Text
import app.uicomponents.controls.Tone
import app.utils.Platform
import app.utils.platform
import app.utils.platformCallback
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.server_host_clients_connected
import syncplaymobile.shared.generated.resources.server_host_config
import syncplaymobile.shared.generated.resources.server_host_copy
import syncplaymobile.shared.generated.resources.server_host_ios_paused
import syncplaymobile.shared.generated.resources.server_host_log_errors_only
import syncplaymobile.shared.generated.resources.server_host_on_network
import syncplaymobile.shared.generated.resources.server_host_over_internet
import syncplaymobile.shared.generated.resources.server_host_port_forward_hint
import syncplaymobile.shared.generated.resources.server_host_server_log
import syncplaymobile.shared.generated.resources.server_host_share
import syncplaymobile.shared.generated.resources.server_host_start
import syncplaymobile.shared.generated.resources.server_host_status_error
import syncplaymobile.shared.generated.resources.server_host_status_running
import syncplaymobile.shared.generated.resources.server_host_status_starting
import syncplaymobile.shared.generated.resources.server_host_status_stopped
import syncplaymobile.shared.generated.resources.server_host_stop
import kotlin.time.Instant

private const val LOG_LINES_SHOWN = 60

/**
 * Hosting, inline under the home form's third server tab: the joinable address with copy and
 * share, the status with its evidence, the start or stop action, the configuration rows and the
 * tail of the log as a severity list. Bound straight to [ServerHostSession], which outlives
 * every screen. Its rows carry their own gutters, so it bleeds past the form's.
 */
@Composable
fun ServerHostPanel(modifier: Modifier = Modifier) {
    val p = palette
    val status by ServerHostSession.serverStatus.collectAsState()
    val detail by ServerHostSession.statusDetail.collectAsState()
    val clients by ServerHostSession.connectedClients.collectAsState()
    val running = status == ServerStatus.Running
    val starting = status == ServerStatus.Starting
    val port by SERVER_PORT.watchPref()
    var errorsOnly by remember { mutableStateOf(false) }
    val logs = ServerHostSession.serverLogs
    val shown = (if (errorsOnly) logs.filter { it.level == ServerLogLevel.Error } else logs.toList()).takeLast(LOG_LINES_SHOWN)

    Column(modifier.fillMaxWidth().bleed(Space.gutter)) {
        if (running) {
            AddressBlock(
                localIp = ServerHostSession.deviceIpAddress.value,
                publicIp = ServerHostSession.publicIpAddress.value,
                publicLoading = ServerHostSession.publicIpLoading.value,
                port = port,
            )
        }
        StatusRow(status, clients, detail)
        if (platform == Platform.IOS && running) {
            Text(
                text = stringResource(Res.string.server_host_ios_paused),
                style = Type.note,
                color = p.warn,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gapTight),
            )
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.gap)) {
            if (running) {
                DestructiveAction(stringResource(Res.string.server_host_stop), onClick = { ServerHostSession.stopServer() }, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryAction(stringResource(Res.string.server_host_start), onClick = { ServerHostSession.startServer() }, modifier = Modifier.fillMaxWidth(), enabled = !starting)
            }
        }
        val editable = !running && !starting
        GroupHeading(stringResource(Res.string.server_host_config))
        SERVER_PORT.enabledWhen { editable }.Render()
        SERVER_PASSWORD.enabledWhen { editable }.Render()
        SERVER_MOTD.enabledWhen { editable }.Render()
        SERVER_ISOLATE_ROOMS.enabledWhen { editable }.Render()
        SERVER_DISABLE_CHAT.enabledWhen { editable }.Render()
        SERVER_DISABLE_READY.enabledWhen { editable }.Render()
        if (logs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(end = Space.gutter), verticalAlignment = Alignment.Bottom) {
                GroupHeading(stringResource(Res.string.server_host_server_log), Modifier.weight(1f))
                Tag(stringResource(Res.string.server_host_log_errors_only), tone = Tone.Bad, filled = errorsOnly, onToggle = { errorsOnly = it })
            }
            shown.forEach { entry -> LogRow(entry) }
            Spacer(Modifier.height(Space.gapTight))
        }
    }
}

/** Lets a block wider than its padded parent draw out to the parent's edges. */
private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtMost(constraints.maxWidth + extra),
            maxWidth = constraints.maxWidth + extra,
        )
    )
    layout(placeable.width - extra, placeable.height) { placeable.placeRelative(-horizontal.roundToPx(), 0) }
}

@Composable
private fun AddressBlock(localIp: String?, publicIp: String?, publicLoading: Boolean, port: String) {
    val p = palette
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.gap)) {
        if (localIp != null) AddressRow("$localIp:$port", stringResource(Res.string.server_host_on_network))
        when {
            publicLoading -> {
                Text(stringResource(Res.string.server_host_over_internet), style = Type.note, color = p.inkDim, modifier = Modifier.padding(top = Space.gap))
                ProgressBar(null, Modifier.fillMaxWidth().padding(top = Space.gapTight))
            }
            publicIp != null -> {
                Spacer(Modifier.height(Space.gap))
                AddressRow("$publicIp:$port", stringResource(Res.string.server_host_over_internet))
                Text(
                    text = stringResource(Res.string.server_host_port_forward_hint, port),
                    style = Type.note,
                    color = p.inkFaint,
                    modifier = Modifier.padding(top = Space.gapTight),
                )
            }
        }
    }
}

@Composable
private fun AddressRow(address: String, label: String) {
    val p = palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(address, style = Type.display, color = p.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text(label, style = Type.note, color = p.inkDim)
        }
        GlyphButton(Icons.Filled.ContentCopy, name = stringResource(Res.string.server_host_copy)) {
            platformCallback.copyText(address)
            Feedback.tick()
        }
        GlyphButton(Icons.Filled.Share, name = stringResource(Res.string.server_host_share)) {
            platformCallback.shareText(address)
        }
    }
}

@Composable
private fun StatusRow(status: ServerStatus, clients: Int, detail: String?) {
    val p = palette
    val square = when (status) {
        ServerStatus.Stopped -> p.inkFaint
        ServerStatus.Starting -> p.accent
        ServerStatus.Running -> p.ok
        ServerStatus.Error -> p.bad
    }
    val label = stringResource(
        when (status) {
            ServerStatus.Stopped -> Res.string.server_host_status_stopped
            ServerStatus.Starting -> Res.string.server_host_status_starting
            ServerStatus.Running -> Res.string.server_host_status_running
            ServerStatus.Error -> Res.string.server_host_status_error
        }
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter)) {
        Row(Modifier.fillMaxWidth().heightIn(min = Space.row), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(square, Radius.tightShape))
            RowGap(Space.gapTight + 2.dp)
            Text(label, style = Type.label, color = p.ink, modifier = Modifier.weight(1f))
            if (status == ServerStatus.Running) {
                Text(stringResource(Res.string.server_host_clients_connected, clients), style = Type.value, color = p.inkDim)
            }
        }
        if (status == ServerStatus.Error && detail != null) {
            Text(detail, style = Type.note, color = p.bad, modifier = Modifier.padding(bottom = Space.gapTight))
        }
    }
}

@Composable
private fun LogRow(entry: ServerLogEntry) {
    val p = palette
    val stub = when (entry.level) {
        ServerLogLevel.Ok -> p.ok
        ServerLogLevel.Error -> p.bad
        ServerLogLevel.Info -> p.inkFaint
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = Space.gutter, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(stub))
        RowGap(Space.gapTight + 2.dp)
        Text(clock(entry.timestamp), style = Type.value, color = p.inkFaint, modifier = Modifier.width(64.dp))
        Text(entry.message, style = Type.note, color = if (entry.level == ServerLogLevel.Error) p.bad else p.ink)
    }
}

private fun clock(epochMs: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).time
    fun Int.two() = toString().padStart(2, '0')
    return "${t.hour.two()}:${t.minute.two()}:${t.second.two()}"
}
