package app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.PersonPin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.LocalGlobalViewmodel
import app.Screen
import app.home.components.HomeEnginePicker
import app.home.components.HomeTopBar
import app.home.components.PopupDidYaKnow.DidYaKnowPopup
import app.preferences.Preferences.NEVER_SHOW_TIPS
import app.preferences.Preferences.PLAYER_ENGINE
import app.preferences.set
import app.preferences.value
import app.preferences.watchPref
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.Field
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.PrimaryAction
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.Segmented
import app.uicomponents.controls.Tag
import app.uicomponents.frames.NoticeHost
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
import syncplaymobile.shared.generated.resources.connect_button_saveshortcut
import syncplaymobile.shared.generated.resources.connect_choose_video_engine
import syncplaymobile.shared.generated.resources.connect_custom
import syncplaymobile.shared.generated.resources.connect_custom_help
import syncplaymobile.shared.generated.resources.connect_host_own_server
import syncplaymobile.shared.generated.resources.connect_official
import syncplaymobile.shared.generated.resources.connect_password_help
import syncplaymobile.shared.generated.resources.connect_port_empty_error
import syncplaymobile.shared.generated.resources.connect_roomname
import syncplaymobile.shared.generated.resources.connect_roomname_empty_error
import syncplaymobile.shared.generated.resources.connect_roomname_tooltip
import syncplaymobile.shared.generated.resources.connect_server
import syncplaymobile.shared.generated.resources.connect_server_tooltip
import syncplaymobile.shared.generated.resources.connect_username
import syncplaymobile.shared.generated.resources.connect_username_empty_error
import syncplaymobile.shared.generated.resources.connect_username_tooltip
import syncplaymobile.shared.generated.resources.connect_watch_alone
import syncplaymobile.shared.generated.resources.home_engine_unavailable_error
import syncplaymobile.shared.generated.resources.home_ip_address
import syncplaymobile.shared.generated.resources.home_password_if_any
import syncplaymobile.shared.generated.resources.home_port
import syncplaymobile.shared.generated.resources.home_shortcut_saved
import app.utils.appName

val officialServers = listOf("syncplay.pl:8995", "syncplay.pl:8996", "syncplay.pl:8997", "syncplay.pl:8998", "syncplay.pl:8999")

/** The official server is one host; only the port varies, so the picker offers just these. */
val officialPorts = listOf("8995", "8996", "8997", "8998", "8999")

private const val OFFICIAL_HOST = "syncplay.pl"
private val FORM_MAX_WIDTH = 420.dp

/**
 * The join form with one left edge: label over field, help under it while empty or focused,
 * inline errors, the server as a segmented choice, the engine wheel, then Join and Watch alone.
 */
@Composable
fun HomeScreenUI(viewmodel: HomeViewmodel) {
    ExitRoomMode()
    val p = palette
    val globalViewmodel = LocalGlobalViewmodel.current
    val focusManager = LocalFocusManager.current

    var savedConfig by remember { mutableStateOf<JoinConfig?>(null) }
    LaunchedEffect(null) {
        withContext(Dispatchers.IO) { savedConfig = JoinConfig.savedConfig() }
    }

    // A pending shortcut joins once, on arrival, through the same caps as the form.
    LaunchedEffect(Unit) {
        consumePendingShortcut()?.let { viewmodel.joinRoom(it.sanitised()) }
    }

    val didYaKnowPopup = remember { mutableStateOf(false) }
    DidYaKnowPopup(didYaKnowPopup)
    LaunchedEffect(null) {
        withContext(Dispatchers.IO) {
            delay(1000)
            if (!globalViewmodel.hasEnteredRoomOnce && !NEVER_SHOW_TIPS.value()) didYaKnowPopup.value = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(viewmodel)

            /* The form renders with defaults at once and re-keys on the saved config when it
             * arrives. imePadding before verticalScroll, so the keyboard scrolls the form. */
            val config = savedConfig ?: remember { JoinConfig() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .clickable(interactionSource = null, indication = null) { focusManager.clearFocus(force = true) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = FORM_MAX_WIDTH).fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.gutter),
                    verticalArrangement = Arrangement.spacedBy(Space.gap),
                ) {
                    var username by remember(savedConfig) { mutableStateOf(config.user) }
                    var room by remember(savedConfig) { mutableStateOf(config.room) }
                    var official by remember(savedConfig) {
                        mutableStateOf(officialServers.contains("${config.ip.replace("151.80.32.178", OFFICIAL_HOST)}:${config.port}"))
                    }
                    var address by remember(savedConfig) { mutableStateOf(config.ip) }
                    var port by remember(savedConfig) { mutableStateOf(config.port.toString()) }
                    var password by remember(savedConfig) { mutableStateOf(config.pw) }
                    var error by remember { mutableStateOf<StringResource?>(null) }

                    val usernameFocus = remember { FocusRequester() }
                    val roomFocus = remember { FocusRequester() }
                    val portFocus = remember { FocusRequester() }
                    val passwordFocus = remember { FocusRequester() }

                    // Initial focus only under keyboard input, so touch users get no keyboard on arrival.
                    val inputModeManager = LocalInputModeManager.current
                    LaunchedEffect(Unit) {
                        if (inputModeManager.inputMode == InputMode.Keyboard) {
                            delay(150)
                            runCatching { usernameFocus.requestFocus() }
                        }
                    }

                    FormField(
                        label = stringResource(Res.string.connect_username),
                        help = stringResource(Res.string.connect_username_tooltip),
                        error = error?.takeIf { it == Res.string.connect_username_empty_error }?.let { stringResource(it) },
                        value = username,
                        onValueChange = { username = it; error = null },
                        icon = Icons.Outlined.PersonPin,
                        focusRequester = usernameFocus,
                        imeAction = ImeAction.Next,
                        onImeAction = { roomFocus.requestFocus() },
                    )
                    FormField(
                        label = stringResource(Res.string.connect_roomname),
                        help = stringResource(Res.string.connect_roomname_tooltip),
                        error = error?.takeIf { it == Res.string.connect_roomname_empty_error }?.let { stringResource(it) },
                        value = room,
                        onValueChange = { room = it; error = null },
                        icon = Icons.Outlined.MeetingRoom,
                        focusRequester = roomFocus,
                        imeAction = ImeAction.Done,
                        onImeAction = { focusManager.clearFocus(true) },
                    )

                    /* Server: Official or Custom. Official keeps a non-official port from
                     * leaking through and clears the password; Custom blanks both. */
                    Column(verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                        FormLabel(stringResource(Res.string.connect_server, appName))
                        Segmented(
                            options = listOf(stringResource(Res.string.connect_official), stringResource(Res.string.connect_custom)),
                            selected = if (official) 0 else 1,
                            onSelect = { index ->
                                val toOfficial = index == 0
                                if (toOfficial == official) return@Segmented
                                official = toOfficial
                                if (toOfficial) {
                                    address = OFFICIAL_HOST
                                    if (port !in officialPorts) port = "8997"
                                    password = ""
                                } else {
                                    address = ""
                                    port = ""
                                }
                                error = null
                            },
                        )
                        if (official) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                                officialPorts.forEach { candidate ->
                                    Tag(candidate, filled = port == candidate, onToggle = { port = candidate; address = OFFICIAL_HOST })
                                }
                            }
                            Text(stringResource(Res.string.connect_server_tooltip), style = Type.note, color = p.inkDim)
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.gap)) {
                                Field(
                                    value = address,
                                    onValueChange = { address = it.trim(); error = null },
                                    modifier = Modifier.weight(2f),
                                    placeholder = stringResource(Res.string.home_ip_address),
                                    leading = Icons.Outlined.Lan,
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Next,
                                    onImeAction = { portFocus.requestFocus() },
                                    name = stringResource(Res.string.home_ip_address),
                                )
                                Field(
                                    value = port,
                                    onValueChange = { port = it.trim(); error = null },
                                    modifier = Modifier.weight(1f),
                                    placeholder = stringResource(Res.string.home_port),
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                    onImeAction = { passwordFocus.requestFocus() },
                                    focusRequester = portFocus,
                                    name = stringResource(Res.string.home_port),
                                )
                            }
                            val serverError = error?.takeIf { it == Res.string.connect_address_empty_error || it == Res.string.connect_port_empty_error }
                            Text(
                                text = serverError?.let { stringResource(it) } ?: stringResource(Res.string.connect_custom_help),
                                style = Type.note,
                                color = if (serverError != null) p.bad else p.inkDim,
                            )
                            FormField(
                                label = stringResource(Res.string.home_password_if_any),
                                help = stringResource(Res.string.connect_password_help),
                                error = null,
                                value = password,
                                onValueChange = { password = it.trim() },
                                icon = null,
                                focusRequester = passwordFocus,
                                imeAction = ImeAction.Done,
                                onImeAction = { focusManager.clearFocus(true) },
                            )
                        }
                        SecondaryAction(stringResource(Res.string.connect_host_own_server), onClick = { globalViewmodel.backstack.add(Screen.ServerHost) }, modifier = Modifier.fillMaxWidth())
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                        FormLabel(stringResource(Res.string.connect_choose_video_engine))
                        val selectedEngine by PLAYER_ENGINE.watchPref()
                        // A saved engine this build no longer ships is replaced once with the platform default.
                        LaunchedEffect(selectedEngine, availablePlatformPlayerEngines) {
                            if (availablePlatformPlayerEngines.none { it.name == selectedEngine }) {
                                availablePlatformPlayerEngines.firstOrNull { it.isDefault }?.let { PLAYER_ENGINE.set(it.name) }
                            }
                        }
                        val unavailable = stringResource(Res.string.home_engine_unavailable_error)
                        HomeEnginePicker(
                            modifier = Modifier.fillMaxWidth(),
                            engines = availablePlatformPlayerEngines,
                            selectedEngine = selectedEngine,
                            onSelectEngine = { engine ->
                                viewmodel.viewModelScope.launch(Dispatchers.IO) {
                                    if (engine.isAvailable) PLAYER_ENGINE.set(engine.name) else viewmodel.snackIt(unavailable)
                                }
                            },
                        )
                    }

                    /* One validation for both paths, so the shortcut saver cannot crash on a
                     * blank port either. */
                    fun validate(): StringResource? = when {
                        username.isBlank() -> Res.string.connect_username_empty_error
                        room.isBlank() -> Res.string.connect_roomname_empty_error
                        address.isBlank() -> Res.string.connect_address_empty_error
                        port.isBlank() || port.toIntOrNull() == null -> Res.string.connect_port_empty_error
                        else -> null
                    }
                    fun currentConfig() = JoinConfig(username, room, address, port.toInt(), password).sanitised()

                    val shortcutSaved = stringResource(Res.string.home_shortcut_saved, room)
                    PrimaryAction(
                        text = stringResource(Res.string.connect_button_join),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            error = validate()
                            if (error == null) globalViewmodel.viewModelScope.launch(Dispatchers.Default) { viewmodel.joinRoom(currentConfig()) }
                        },
                        trailing = {
                            GlyphButton(Icons.Filled.Widgets, name = stringResource(Res.string.connect_button_saveshortcut), tint = p.ground) {
                                error = validate()
                                if (error == null) {
                                    with(platformCallback) { viewmodel.onSaveConfigShortcut(currentConfig()) }
                                    viewmodel.snackItAsync(shortcutSaved)
                                }
                            }
                        },
                    )
                    SecondaryAction(
                        text = stringResource(Res.string.connect_watch_alone),
                        onClick = { globalViewmodel.viewModelScope.launch { viewmodel.joinRoom(null) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.gap))
                }
            }
        }

        NoticeHost(
            queue = viewmodel.notices,
            overVideo = false,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(Space.gutter),
        )
    }
}

/** Backslashes out, trimmed, capped: the same on the form and on a shortcut. */
private fun JoinConfig.sanitised() = copy(
    user = user.replace("\\", "").trim().substringSafely(0, 149),
    room = room.replace("\\", "").trim().substringSafely(0, 34),
)

@Composable
private fun FormLabel(text: String) {
    Text(text, style = Type.label, color = palette.inkDim, modifier = Modifier.height(Space.gutter))
}

/**
 * Label over the hairline field, and one note line under it: help while the field is empty or
 * focused, the error in `bad` when validation failed. Filled fields show nothing under them.
 */
@Composable
private fun FormField(
    label: String,
    help: String,
    error: String?,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector?,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    val p = palette
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
        FormLabel(label)
        Field(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            leading = icon,
            imeAction = imeAction,
            onImeAction = onImeAction,
            focusRequester = focusRequester,
            name = label,
        )
        when {
            error != null -> Text(error, style = Type.note, color = p.bad)
            value.isEmpty() || focused -> Text(help, style = Type.note, color = p.inkDim)
        }
    }
}
