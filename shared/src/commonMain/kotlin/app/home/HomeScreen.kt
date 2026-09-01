package app.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.PersonPin
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import app.LocalGlobalViewmodel
import app.Screen
import app.home.components.HomeEngineWheel
import app.home.components.HomeTextField
import app.home.components.HomeTopBar
import app.home.components.PopupDidYaKnow.DidYaKnowPopup
import app.preferences.Preferences.NEVER_SHOW_TIPS
import app.preferences.Preferences.PLAYER_ENGINE
import app.preferences.set
import app.preferences.value
import app.preferences.watchPref
import app.theme.Theming
import app.uicomponents.FlexibleText
import app.uicomponents.dropdownMenuMaxHeight
import app.uicomponents.sairaFont
import app.uicomponents.tvFocusable
import app.utils.appName
import app.utils.ExitRoomMode
import app.utils.availablePlatformPlayerEngines
import app.utils.consumePendingShortcut
import app.utils.platformCallback
import app.utils.substringSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.connect_address_empty_error
import syncplaymobile.shared.generated.resources.connect_button_join
import syncplaymobile.shared.generated.resources.connect_choose_video_engine
import syncplaymobile.shared.generated.resources.connect_enter_custom_server
import syncplaymobile.shared.generated.resources.connect_port_empty_error
import syncplaymobile.shared.generated.resources.connect_roomname
import syncplaymobile.shared.generated.resources.connect_roomname_empty_error
import syncplaymobile.shared.generated.resources.connect_roomname_tooltip
import syncplaymobile.shared.generated.resources.connect_server
import syncplaymobile.shared.generated.resources.connect_server_tooltip
import syncplaymobile.shared.generated.resources.connect_username
import syncplaymobile.shared.generated.resources.connect_username_empty_error
import syncplaymobile.shared.generated.resources.connect_username_tooltip
import syncplaymobile.shared.generated.resources.home_engine_unavailable_error
import syncplaymobile.shared.generated.resources.home_ip_address
import syncplaymobile.shared.generated.resources.home_password_if_any
import syncplaymobile.shared.generated.resources.connect_host_own_server
import syncplaymobile.shared.generated.resources.home_port
import app.uicomponents.GlassExposedDropdownMenu
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Check
import syncplaymobile.shared.generated.resources.connect_official_server_desc
import syncplaymobile.shared.generated.resources.connect_official_server
import androidx.compose.material3.ripple
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

val officialServers = listOf("syncplay.pl:8995", "syncplay.pl:8996", "syncplay.pl:8997", "syncplay.pl:8998", "syncplay.pl:8999")

/** The official server is one host; only the port varies, so the picker offers just these. */
val officialPorts = listOf("8995", "8996", "8997", "8998", "8999")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenUI(viewmodel: HomeViewmodel) {
    ExitRoomMode()

    var savedConfig by remember { mutableStateOf<JoinConfig?>(null) }

    LaunchedEffect(null) {
        withContext(Dispatchers.IO) {
            savedConfig = JoinConfig.savedConfig()
        }
    }

    // Consume any pending shortcut (iOS cold-start Quick Actions)
    LaunchedEffect(Unit) {
        consumePendingShortcut()?.let { joinConfig ->
            viewmodel.joinRoom(joinConfig)
        }
    }

    val didYaKnowPopup = remember { mutableStateOf(false) }
    DidYaKnowPopup(didYaKnowPopup)

    val globalViewmodel = LocalGlobalViewmodel.current
    LaunchedEffect(null) {
        withContext(Dispatchers.IO) {
            delay(1000)
            val neverShowTips = NEVER_SHOW_TIPS.value()
            if (!globalViewmodel.hasEnteredRoomOnce && !neverShowTips) {
                didYaKnowPopup.value = true
            }
        }
    }

    // The bar's height when the settings grid is CLOSED. The page is spaced by this, not by the
    // Scaffold's live top padding, so opening the grid overlays the page instead of re-laying it
    // out (which on a short screen squeezed every field into the next).
    var topBarRestingHeight by remember { mutableStateOf(0.dp) }

    Scaffold(
        snackbarHost = { SnackbarHost(viewmodel.snack) },
        topBar = {
            HomeTopBar(viewmodel, onRestingHeight = { topBarRestingHeight = it })
        },
        content = { _ ->
            val focusManager = LocalFocusManager.current

            // Render the form immediately with defaults; re-key the field states once the
            // saved config finishes loading (sub-250ms) instead of flashing a blank screen.
            val config = savedConfig ?: remember { JoinConfig() }
            run {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .windowInsetsPadding(BottomAppBarDefaults.windowInsets)
                        .imePadding() //Prevents textfields from getting hidden by software keyboard
                        .verticalScroll(rememberScrollState())
                        .clickable(
                            interactionSource = null,
                            indication = null
                        ) {
                            //Clicking outside a text field should dismiss the keyboard
                            focusManager.clearFocus(force = true)
                        },
                    horizontalAlignment = CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    /* Spacer instead of consuming paddingValues, and deliberately keyed to the
                     * bar's RESTING height so the settings grid opening over it changes nothing
                     * down here. */
                    Spacer(modifier = Modifier.height(topBarRestingHeight))

                    /* higher-level variables which are needed for logging in */
                    val roomFocusRequester = remember { FocusRequester() }
                    var textUsername by remember(savedConfig) { mutableStateOf(config.user) }
                    var textRoomname by remember(savedConfig) { mutableStateOf(config.room) }

                    var serverIsPublic by remember(savedConfig) {
                        mutableStateOf(officialServers.contains("${config.ip.replace("151.80.32.178", "syncplay.pl")}:${config.port}"))
                    }

                    var selectedServer by remember(savedConfig) { mutableStateOf("${config.ip}:${config.port}") }

                    var serverAddress by remember(savedConfig) { mutableStateOf(config.ip) }
                    var serverPort by remember(savedConfig) { mutableStateOf(config.port.toString()) }
                    var serverPassword by remember(savedConfig) { mutableStateOf(config.pw) }

                    /* Username — first focusable on the screen; grabs initial focus for D-pad/TV
                     * users. Skipped under touch input mode so the soft keyboard doesn't pop on
                     * phone/tablet entry. */
                    val usernameFocusRequester = remember { FocusRequester() }
                    val inputModeManager = LocalInputModeManager.current
                    LaunchedEffect(Unit) {
                        if (inputModeManager.inputMode == InputMode.Keyboard) {
                            kotlinx.coroutines.delay(150)
                            runCatching { usernameFocusRequester.requestFocus() }
                        }
                    }

                    Column(
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(0.75f),
                        horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeLeadingTitle(
                            string = stringResource(Res.string.connect_username),
                            tooltip = stringResource(Res.string.connect_username_tooltip)
                        )

                        HomeTextField(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.PersonPin,
                            value = textUsername,
                            onValueChange = { textUsername = it },
                            focusRequester = usernameFocusRequester,
                            onNext = { roomFocusRequester.requestFocus() }
                        )
                    }

                    /* Roomname */
                    Column(
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(0.75f),
                        horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeLeadingTitle(
                            string = stringResource(Res.string.connect_roomname),
                            tooltip = stringResource(Res.string.connect_roomname_tooltip)
                        )

                        HomeTextField(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.MeetingRoom,
                            value = textRoomname,
                            onValueChange = { textRoomname = it },
                            focusRequester = roomFocusRequester,
                            clearFocusWhenDone = true
                        )
                    }

                    /* Server */
                    val expanded = remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(0.75f),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeLeadingTitle(
                            string = stringResource(Res.string.connect_server, appName),
                            tooltip = stringResource(Res.string.connect_server_tooltip)
                        )

                        ExposedDropdownMenuBox(
                            expanded = expanded.value,
                            onExpandedChange = {
                                expanded.value = !expanded.value
                            }
                        ) {
                            HomeTextField(
                                modifier = Modifier.fillMaxWidth().menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                icon = Icons.Outlined.Lan,
                                value = if (serverIsPublic) {
                                    stringResource(Res.string.connect_official_server)
                                } else {
                                    stringResource(Res.string.connect_enter_custom_server)
                                },
                                dropdownState = expanded,
                                onValueChange = {}
                            )

                            val customServerLabel = stringResource(Res.string.connect_enter_custom_server)
                            val hostServerLabel = stringResource(Res.string.connect_host_own_server)
                            val officialLabel = stringResource(Res.string.connect_official_server)

                            /* Three rows, not seven. The five official entries differed only by
                             * port, so they collapse into one row with the port chosen beside it;
                             * that also stops the list outgrowing the screen and hiding the two
                             * rows that actually go somewhere. */
                            GlassExposedDropdownMenu(
                                modifier = Modifier.heightIn(max = dropdownMenuMaxHeight),
                                expanded = expanded.value,
                                // Square top meets the anchor's squared bottom: field and menu
                                // read as one panel that split open.
                                shape = RoundedCornerShape(
                                    topStart = 0.dp, topEnd = 0.dp,
                                    bottomStart = 16.dp, bottomEnd = 16.dp
                                ),
                                onDismissRequest = { expanded.value = false }
                            ) {
                                ServerMenuRow(
                                    icon = Icons.Outlined.Lan,
                                    label = officialLabel,
                                    supporting = stringResource(
                                        Res.string.connect_official_server_desc,
                                        serverPort.ifBlank { "8997" }
                                    ),
                                    selected = serverIsPublic,
                                    onClick = {
                                        expanded.value = false
                                        serverIsPublic = true
                                        serverAddress = "syncplay.pl"
                                        if (serverPort !in officialPorts) serverPort = "8997"
                                        selectedServer = "syncplay.pl:$serverPort"
                                        serverPassword = ""
                                    }
                                )

                                ServerMenuRow(
                                    icon = Icons.Outlined.Edit,
                                    label = customServerLabel,
                                    selected = !serverIsPublic,
                                    onClick = {
                                        expanded.value = false
                                        serverIsPublic = false
                                        selectedServer = customServerLabel
                                        serverAddress = ""
                                        serverPort = ""
                                    }
                                )

                                ServerMenuRow(
                                    icon = Icons.Outlined.Dns,
                                    label = hostServerLabel,
                                    onClick = {
                                        expanded.value = false
                                        globalViewmodel.backstack.add(Screen.ServerHost)
                                    }
                                )
                            }
                        }

                        /* Port picker: only meaningful for the official server, where it is the
                         * only thing that varied between the old five rows. */
                        AnimatedVisibility(
                            visible = serverIsPublic,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                officialPorts.forEach { port ->
                                    val active = serverPort == port
                                    Text(
                                        text = port,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (active) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHigh
                                            )
                                            .clickable(interactionSource = null, indication = ripple()) {
                                                serverPort = port
                                                serverAddress = "syncplay.pl"
                                                selectedServer = "syncplay.pl:$port"
                                            }
                                            .padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = !serverIsPublic,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val portFocusRequester = remember { FocusRequester() }
                                val passwordFocusRequester = remember { FocusRequester() }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    HomeTextField(
                                        modifier = Modifier.weight(2.25f).padding(end = 4.dp),
                                        value = serverAddress,
                                        onValueChange = { serverAddress = it.trim() },
                                        label = stringResource(Res.string.home_ip_address),
                                        cornerRadius = 16.dp,
                                        onNext = { portFocusRequester.requestFocus() }
                                    )

                                    HomeTextField(
                                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                                        value = serverPort,
                                        onValueChange = { serverPort = it.trim() },
                                        type = KeyboardType.Number,
                                        label = stringResource(Res.string.home_port),
                                        cornerRadius = 16.dp,
                                        focusRequester = portFocusRequester,
                                        onNext = { passwordFocusRequester.requestFocus() }
                                    )
                                }

                                HomeTextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = serverPassword,
                                    onValueChange = { serverPassword = it.trim() },
                                    type = KeyboardType.Password,
                                    label = stringResource(Res.string.home_password_if_any),
                                    clearFocusWhenDone = true,
                                    focusRequester = passwordFocusRequester
                                )
                            }
                        }
                    }

                    //TODO There should be no default video engine, force user to choose at first launch
                    //TODO Anchor a tooltip next to our engine selection that explains each engine for better selection

                    Column(
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(0.75f),
                        horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeLeadingTitle(
                            string = stringResource(Res.string.connect_choose_video_engine)
                        )

                        val selectedEngine by PLAYER_ENGINE.watchPref()

                        // A saved engine that this build no longer has is not a selection, it is a
                        // leftover. Engines do get dropped between releases, so an existing install
                        // can open with PLAYER_ENGINE naming a dead one; the wheel then sits on
                        // whatever is at index 0 while the preference still says otherwise.
                        // Write the platform default over it once instead.
                        LaunchedEffect(selectedEngine, availablePlatformPlayerEngines) {
                            if (availablePlatformPlayerEngines.none { it.name == selectedEngine }) {
                                availablePlatformPlayerEngines
                                    .firstOrNull { it.isDefault }
                                    ?.let { PLAYER_ENGINE.set(it.name) }
                            }
                        }

                        val errorString = stringResource(Res.string.home_engine_unavailable_error)
                        HomeEngineWheel(
                            modifier = Modifier.fillMaxWidth(),
                            engines = availablePlatformPlayerEngines,
                            selectedEngine = selectedEngine,
                            onSelectEngine = { engine ->
                                viewmodel.viewModelScope.launch(Dispatchers.IO) {
                                    if (engine.isAvailable) {
                                        PLAYER_ENGINE.set(engine.name)
                                    } else {
                                        viewmodel.snackIt(errorString)
                                    }
                                }
                            }
                        )
                    }

                    /* join button + shortcut saver. A plain Row with a weighted leading button
                     * instead of SplitButtonLayout: the pair then always spans exactly the same
                     * 0.75-fraction width as the text fields above, keeping the button symmetric
                     * within the screen on any width (tablets included). */
                    Row(
                        modifier = Modifier.fillMaxWidth(0.75f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SplitButtonDefaults.Spacing)
                    ) {
                            SplitButtonDefaults.LeadingButton(
                                contentPadding = PaddingValues(vertical = 16.dp),
                                modifier = Modifier.weight(1f).tvFocusable(addFocusable = false),
                                onClick = {
                                    globalViewmodel.viewModelScope.launch(Dispatchers.Default) {
                                        val errorMessage: StringResource? = when {
                                            textUsername.isBlank() -> Res.string.connect_username_empty_error
                                            textRoomname.isBlank() -> Res.string.connect_roomname_empty_error
                                            serverAddress.isBlank() -> Res.string.connect_address_empty_error
                                            serverPort.isBlank() || serverPort.toIntOrNull() == null -> Res.string.connect_port_empty_error
                                            else -> null
                                        }

                                        if (errorMessage != null) {
                                            viewmodel.snackIt(getString(errorMessage))
                                            return@launch
                                        }

                                        viewmodel.joinRoom(
                                            JoinConfig(
                                                textUsername.replace("\\", "").trim().substringSafely(0, 149),
                                                textRoomname.replace("\\", "").trim().substringSafely(0, 34),
                                                serverAddress,
                                                serverPort.toInt(),
                                                serverPassword
                                            )
                                        )
                                    }
                                },
                                content = {
                                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(Res.string.connect_button_join), fontSize = 18.sp)
                                }
                            )

                            SplitButtonDefaults.TrailingButton(
                                modifier = Modifier.tvFocusable(addFocusable = false),
                                contentPadding = PaddingValues(vertical = 16.dp),
                                checked = false,
                                onCheckedChange = {
                                    // Same validation as the Join path: a blank/garbage port must
                                    // not crash the shortcut saver with a NumberFormatException.
                                    globalViewmodel.viewModelScope.launch(Dispatchers.Default) {
                                        val errorMessage: StringResource? = when {
                                            textUsername.isBlank() -> Res.string.connect_username_empty_error
                                            textRoomname.isBlank() -> Res.string.connect_roomname_empty_error
                                            serverAddress.isBlank() -> Res.string.connect_address_empty_error
                                            serverPort.isBlank() || serverPort.toIntOrNull() == null -> Res.string.connect_port_empty_error
                                            else -> null
                                        }

                                        if (errorMessage != null) {
                                            viewmodel.snackIt(getString(errorMessage))
                                            return@launch
                                        }

                                        with(platformCallback) {
                                            viewmodel.onSaveConfigShortcut(
                                                JoinConfig(
                                                    textUsername.replace("\\", "").trim(),
                                                    textRoomname.replace("\\", "").trim(),
                                                    serverAddress,
                                                    serverPort.toInt(),
                                                    serverPassword
                                                )
                                            )
                                        }
                                    }
                                },
                                content = {
                                    Icon(imageVector = Icons.Filled.Widgets, null)
                                }
                            )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    )

}

@OptIn(ExperimentalMaterial3Api::class)
/** One row of the server menu: icon, label, optional second line, and a check when it is current. */
@Composable
private fun ServerMenuRow(
    icon: ImageVector,
    label: String,
    supporting: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column {
                Text(label, color = MaterialTheme.colorScheme.onSurface)
                if (supporting != null) {
                    Text(
                        supporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        },
        trailingIcon = if (!selected) null else {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        onClick = onClick
    )
}

@Composable
fun HomeLeadingTitle(string: String, tooltip: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = string,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (tooltip != null) {
            val tooltipState = rememberTooltipState(isPersistent = true)
            val scope = rememberCoroutineScope()

            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                state = tooltipState,
                tooltip = {
                    PlainTooltip { Text(tooltip) }
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = tooltip,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(15.dp)
                        .clickable(interactionSource = null, indication = null) {
                            scope.launch {
                                if (tooltipState.isVisible) tooltipState.dismiss() else tooltipState.show()
                            }
                        }
                )
            }
        }
    }
}
