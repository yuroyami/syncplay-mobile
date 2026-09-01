package app.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.LocalGlobalViewmodel
import app.preferences.Preferences.SERVER_DISABLE_CHAT
import app.preferences.Preferences.SERVER_DISABLE_READY
import app.preferences.Preferences.SERVER_ISOLATE_ROOMS
import app.preferences.Preferences.SERVER_MOTD
import app.preferences.Preferences.SERVER_PASSWORD
import app.preferences.Preferences.SERVER_PORT
import app.preferences.settings.Render
import app.preferences.settings.enabledWhen
import app.preferences.watchPref
import app.server.ServerLogEntry
import app.server.ServerLogLevel
import app.server.ServerStatus
import app.server.ServerViewmodel
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
import app.uicomponents.frames.ScreenFrame
import app.utils.ExitRoomMode
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
import syncplaymobile.shared.generated.resources.server_host_title
import kotlin.time.Instant

/**
 * Hosting: the joinable address first, with copy and share; then the status with its evidence;
 * the start or stop action; the configuration rows; and the log as a severity list. One lazy
 * list, so the log scrolls with the page and follows new lines only while the reader is at
 * the bottom.
 */
@Composable
fun ServerHostScreenUI(viewmodel: ServerViewmodel) {
    ExitRoomMode()
    val p = palette
    val globalViewmodel = LocalGlobalViewmodel.current
    val status by viewmodel.serverStatus.collectAsState()
    val detail by viewmodel.statusDetail.collectAsState()
    val clients by viewmodel.connectedClients.collectAsState()
    val running = status == ServerStatus.Running
    val starting = status == ServerStatus.Starting
    val port by SERVER_PORT.watchPref()

    val listState = rememberLazyListState()
    var errorsOnly by remember { mutableStateOf(false) }
    val logs = viewmodel.serverLogs
    val shown = if (errorsOnly) logs.filter { it.level == ServerLogLevel.Error } else logs.toList()

    // Follow new lines only while the reader was already at the bottom before they arrived.
    var followLog by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val info = listState.layoutInfo
                followLog = info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
            }
        }
    }
    LaunchedEffect(shown.size) {
        if (followLog && shown.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }

    ScreenFrame(
        title = stringResource(Res.string.server_host_title),
        onBack = { globalViewmodel.backstack.removeLastOrNull() },
        scrolled = scrolled,
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (running) {
                item("address") {
                    AddressBlock(
                        localIp = viewmodel.deviceIpAddress.value,
                        publicIp = viewmodel.publicIpAddress.value,
                        publicLoading = viewmodel.publicIpLoading.value,
                        port = port,
                    )
                }
            }
            item("status") {
                StatusRow(status, clients, detail)
                if (platform == Platform.IOS && running) {
                    Text(
                        text = stringResource(Res.string.server_host_ios_paused),
                        style = Type.note,
                        color = p.warn,
                        modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gapTight),
                    )
                }
            }
            item("action") {
                Box(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.gap)) {
                    if (running) {
                        DestructiveAction(stringResource(Res.string.server_host_stop), onClick = { viewmodel.stopServer() }, modifier = Modifier.fillMaxWidth())
                    } else {
                        PrimaryAction(stringResource(Res.string.server_host_start), onClick = { viewmodel.startServer() }, modifier = Modifier.fillMaxWidth(), enabled = !starting)
                    }
                }
            }
            item("config") {
                val editable = !running && !starting
                GroupHeading(stringResource(Res.string.server_host_config))
                SERVER_PORT.enabledWhen { editable }.Render()
                SERVER_PASSWORD.enabledWhen { editable }.Render()
                SERVER_MOTD.enabledWhen { editable }.Render()
                SERVER_ISOLATE_ROOMS.enabledWhen { editable }.Render()
                SERVER_DISABLE_CHAT.enabledWhen { editable }.Render()
                SERVER_DISABLE_READY.enabledWhen { editable }.Render()
            }
            if (logs.isNotEmpty()) {
                item("loghead") {
                    Row(Modifier.fillMaxWidth().padding(end = Space.gutter), verticalAlignment = Alignment.Bottom) {
                        GroupHeading(stringResource(Res.string.server_host_server_log), Modifier.weight(1f))
                        Tag(stringResource(Res.string.server_host_log_errors_only), tone = app.uicomponents.controls.Tone.Bad, filled = errorsOnly, onToggle = { errorsOnly = it })
                    }
                }
                items(shown) { entry -> LogRow(entry) }
                item("tail") { Spacer(Modifier.height(Space.gutter)) }
            }
        }
    }
}

/** The address block: the LAN row always, the public row while fetching or once known. */
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

/** A 6dp square, the state, the client count while running, and the error's evidence below. */
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

/** Time in the gutter, a 2dp stub by severity, the message in note type. */
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
