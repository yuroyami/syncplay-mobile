package app.server.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalGlobalViewmodel
import app.home.HomeLeadingTitle
import app.home.components.HomeTextField
import app.server.ServerLogEntry
import app.server.ServerStatus
import app.server.ServerViewmodel
import app.utils.ShowSystemBars
import app.utils.platform
import app.utils.Platform
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.server_host_clients_connected
import syncplaymobile.shared.generated.resources.server_host_disable_chat
import syncplaymobile.shared.generated.resources.server_host_disable_ready
import syncplaymobile.shared.generated.resources.server_host_isolate_rooms
import syncplaymobile.shared.generated.resources.server_host_ios_warning
import syncplaymobile.shared.generated.resources.server_host_motd
import syncplaymobile.shared.generated.resources.server_host_password
import syncplaymobile.shared.generated.resources.server_host_port
import syncplaymobile.shared.generated.resources.server_host_server_log
import syncplaymobile.shared.generated.resources.server_host_start
import syncplaymobile.shared.generated.resources.server_host_status_error
import syncplaymobile.shared.generated.resources.server_host_status_running
import syncplaymobile.shared.generated.resources.server_host_status_starting
import syncplaymobile.shared.generated.resources.server_host_status_stopped
import syncplaymobile.shared.generated.resources.server_host_stop
import syncplaymobile.shared.generated.resources.server_host_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerHostScreenUI(viewmodel: ServerViewmodel) {
    ShowSystemBars()

    val globalViewmodel = LocalGlobalViewmodel.current
    val status by viewmodel.serverStatus.collectAsState()
    val isRunning = status == ServerStatus.Running

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(Res.string.server_host_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        globalViewmodel.backstack.removeLastOrNull()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(BottomAppBarDefaults.windowInsets)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // --- Configuration ---
                HomeTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = viewmodel.port.value,
                    onValueChange = { viewmodel.port.value = it.trim() },
                    type = KeyboardType.Number,
                    label = stringResource(Res.string.server_host_port),
                    cornerRadius = 16.dp,
                    enabled = !isRunning
                )

                HomeTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = viewmodel.password.value,
                    onValueChange = { viewmodel.password.value = it.trim() },
                    type = KeyboardType.Password,
                    label = stringResource(Res.string.server_host_password),
                    cornerRadius = 16.dp,
                    enabled = !isRunning
                )

                HomeTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = viewmodel.motd.value,
                    onValueChange = { viewmodel.motd.value = it },
                    label = stringResource(Res.string.server_host_motd),
                    cornerRadius = 16.dp,
                    enabled = !isRunning
                )

                // --- Toggles ---
                ToggleRow(
                    label = stringResource(Res.string.server_host_isolate_rooms),
                    checked = viewmodel.isolateRooms.value,
                    onCheckedChange = { viewmodel.isolateRooms.value = it },
                    enabled = !isRunning
                )

                ToggleRow(
                    label = stringResource(Res.string.server_host_disable_chat),
                    checked = viewmodel.disableChat.value,
                    onCheckedChange = { viewmodel.disableChat.value = it },
                    enabled = !isRunning
                )

                ToggleRow(
                    label = stringResource(Res.string.server_host_disable_ready),
                    checked = viewmodel.disableReady.value,
                    onCheckedChange = { viewmodel.disableReady.value = it },
                    enabled = !isRunning
                )

                // --- iOS warning ---
                if (platform == Platform.IOS) {
                    Text(
                        text = stringResource(Res.string.server_host_ios_warning),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // --- Start/Stop Button ---
                Button(
                    onClick = {
                        if (isRunning) viewmodel.stopServer() else viewmodel.startServer()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = stringResource(if (isRunning) Res.string.server_host_stop else Res.string.server_host_start),
                        fontSize = 18.sp
                    )
                }

                // --- Status ---
                StatusIndicator(status)

                // --- Connected clients ---
                AnimatedVisibility(visible = isRunning) {
                    val clients by viewmodel.connectedClients.collectAsState()
                    Text(
                        text = stringResource(Res.string.server_host_clients_connected, clients),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // --- Server Log ---
                if (viewmodel.serverLogs.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.server_host_server_log),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        val listState = rememberLazyListState()
                        val logs = viewmodel.serverLogs.toList()

                        LaunchedEffect(logs.size) {
                            if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(logs) { entry ->
                                LogEntryRow(entry)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun StatusIndicator(status: ServerStatus) {
    val color by animateColorAsState(
        targetValue = when (status) {
            ServerStatus.Stopped -> MaterialTheme.colorScheme.outline
            ServerStatus.Starting -> Color(0xFFFFA000)
            ServerStatus.Running -> Color(0xFF4CAF50)
            ServerStatus.Error -> MaterialTheme.colorScheme.error
        }
    )

    val text = when (status) {
        ServerStatus.Stopped -> stringResource(Res.string.server_host_status_stopped)
        ServerStatus.Starting -> stringResource(Res.string.server_host_status_starting)
        ServerStatus.Running -> stringResource(Res.string.server_host_status_running)
        ServerStatus.Error -> stringResource(Res.string.server_host_status_error)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LogEntryRow(entry: ServerLogEntry) {
    Text(
        text = entry.message,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 14.sp
    )
}
