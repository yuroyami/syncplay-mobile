package app.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.PersonPin
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import syncplaymobile.shared.generated.resources.connect_server_pick_note
import syncplaymobile.shared.generated.resources.connect_server_pick_error
import app.preferences.Preferences
import syncplaymobile.shared.generated.resources.home_shortcut_explain
import app.uicomponents.controls.pressFeedback
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Feedback
import app.theme.Radius
import app.theme.Motion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
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
import app.home.components.HomeEnginePicker
import app.home.components.HomeTopBar
import app.home.components.PopupDidYaKnow.DidYaKnowPopup
import app.preferences.Preferences.NEVER_SHOW_TIPS
import app.preferences.Preferences.TIPS_SHOWN_COUNT
import app.preferences.Preferences.PLAYER_ENGINE
import app.preferences.set
import app.preferences.value
import app.preferences.watchPref
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.Field
import app.uicomponents.controls.PrimaryAction
import app.uicomponents.controls.Segmented
import app.uicomponents.controls.HelpTip
import app.server.ui.ServerHostPanel
import app.preferences.Preferences.SERVER_PASSWORD
import app.preferences.Preferences.SERVER_PORT
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
import syncplaymobile.shared.generated.resources.connect_host_mine
import syncplaymobile.shared.generated.resources.connect_host_join_note
import syncplaymobile.shared.generated.resources.connect_custom_tip
import syncplaymobile.shared.generated.resources.connect_official
import syncplaymobile.shared.generated.resources.connect_password_help
import syncplaymobile.shared.generated.resources.connect_port_empty_error
import syncplaymobile.shared.generated.resources.connect_roomname
import syncplaymobile.shared.generated.resources.connect_roomname_empty_error
import syncplaymobile.shared.generated.resources.connect_roomname_tooltip
import syncplaymobile.shared.generated.resources.connect_server
import syncplaymobile.shared.generated.resources.connect_username
import syncplaymobile.shared.generated.resources.connect_username_empty_error
import syncplaymobile.shared.generated.resources.connect_username_tooltip
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
private const val LOCAL_HOST = "127.0.0.1"

/** Where the join goes: the official server, someone else's, or the one this app hosts. */
private enum class ServerMode { Official, Custom, Host }
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

    // The store is a hot snapshot, so the saved join is read in the first composition: the
    // fields never paint defaults and then swap values under a user who started typing.
    val savedConfig by remember { mutableStateOf(JoinConfig.savedConfigNow()) }
    // A fresh install has no saved join: the server choice starts empty and must be made.
    val hasSavedConfig = remember { Preferences.JOIN_CONFIG.value() != null }

    // A pending shortcut joins once, on arrival, through the same caps as the form.
    LaunchedEffect(Unit) {
        consumePendingShortcut()?.let { viewmodel.joinRoom(it.sanitised()) }
    }

    val didYaKnowPopup = remember { mutableStateOf(false) }
    DidYaKnowPopup(didYaKnowPopup)
    LaunchedEffect(null) {
        withContext(Dispatchers.IO) {
            delay(1000)
            // A few first launches, then the tips leave on their own; the popup's own switch
            // silences them for good.
            val shown = TIPS_SHOWN_COUNT.value()
            if (!globalViewmodel.hasEnteredRoomOnce && !NEVER_SHOW_TIPS.value() && shown < TIPS_MAX_SHOWINGS) {
                TIPS_SHOWN_COUNT.set(shown + 1)
                didYaKnowPopup.value = true
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(viewmodel)

            /* The form renders with defaults at once and re-keys on the saved config when it
             * arrives. imePadding before verticalScroll, so the keyboard scrolls the form. */
            val config = savedConfig
            /* The form is centred in the height left under the bar when it is shorter than
             * that, and scrolls when it is taller (a small window, or the keyboard up). */
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding(),
            ) {
                val viewport = maxHeight
                        var username by remember(savedConfig) { mutableStateOf(config.user) }
                        var room by remember(savedConfig) { mutableStateOf(config.room) }
                        var mode by remember(savedConfig) {
                            mutableStateOf<ServerMode?>(
                                when {
                                    !hasSavedConfig -> null
                                    officialServers.contains("${config.ip.replace("151.80.32.178", OFFICIAL_HOST)}:${config.port}") -> ServerMode.Official
                                    config.ip == LOCAL_HOST || config.ip == "localhost" -> ServerMode.Host
                                    else -> ServerMode.Custom
                                }
                            )
                        }
                        val hostPort by SERVER_PORT.watchPref()
                        val hostPassword by SERVER_PASSWORD.watchPref()
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

                        /* One validation for both paths, so the shortcut saver cannot crash on a
                         * blank port either. */
                        fun validate(): StringResource? = when {
                            username.isBlank() -> Res.string.connect_username_empty_error
                            room.isBlank() -> Res.string.connect_roomname_empty_error
                            mode == null -> Res.string.connect_server_pick_error
                            mode == ServerMode.Host -> null
                            address.isBlank() -> Res.string.connect_address_empty_error
                            port.isBlank() || port.toIntOrNull() == null -> Res.string.connect_port_empty_error
                            else -> null
                        }
                        fun currentConfig() = when (mode) {
                            ServerMode.Host -> JoinConfig(username, room, LOCAL_HOST, hostPort.trim().toIntOrNull() ?: 8999, hostPassword)
                            else -> JoinConfig(username, room, address, port.toInt(), password)
                        }.sanitised()


                /* The four blocks of the form, placed by the window: one spread column when the
                 * window is narrow, two columns when it is wide, and two columns on a short wide
                 * window too, where one column would have to scroll to reach the join key. */
                val identityBlock: @Composable (dense: Boolean) -> Unit = { dense ->
                        Column(Modifier.fillMaxWidth(if (dense) 1f else 0.82f).padding(vertical = if (dense) 0.dp else Space.gap), verticalArrangement = Arrangement.spacedBy(Space.gap)) {
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
                                onValueChange = { typed ->
                                    // An invite link pasted into the room field fills the whole
                                    // form: the room, the server, the port and the password.
                                    val invite = InviteLink.parse(typed)
                                    if (invite != null) {
                                        room = invite.room
                                        address = invite.ip
                                        port = invite.port.toString()
                                        password = invite.pw
                                        mode = if (officialServers.contains("${invite.ip}:${invite.port}")) ServerMode.Official else ServerMode.Custom
                                    } else {
                                        room = typed
                                    }
                                    error = null
                                },
                                icon = Icons.Outlined.MeetingRoom,
                                focusRequester = roomFocus,
                                imeAction = ImeAction.Done,
                                onImeAction = { focusManager.clearFocus(true) },
                            )

                            /* Server: official, someone else's, or the one this app hosts. Official keeps a
                             * non-official port from leaking through and clears the password; Custom blanks
                             * both; Host points the join at the local server. */
                        }
                }
                val serverBlock: @Composable (dense: Boolean) -> Unit = { dense ->
                        Column(Modifier.padding(vertical = if (dense) 0.dp else Space.gap), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                            FormLabel(
                                text = stringResource(Res.string.connect_server, appName),
                                tip = if (mode == ServerMode.Custom) stringResource(Res.string.connect_custom_tip) else null,
                            )
                            Segmented(
                                options = listOf(stringResource(Res.string.connect_official), stringResource(Res.string.connect_custom), stringResource(Res.string.connect_host_mine)),
                                selected = mode?.ordinal ?: -1,
                                onSelect = { index ->
                                    val next = ServerMode.entries[index]
                                    if (next == mode) return@Segmented
                                    mode = next
                                    when (next) {
                                        ServerMode.Official -> {
                                            address = OFFICIAL_HOST
                                            if (port !in officialPorts) port = "8997"
                                            password = ""
                                        }
                                        ServerMode.Custom -> {
                                            address = ""
                                            port = ""
                                            password = ""
                                        }
                                        ServerMode.Host -> {
                                            address = LOCAL_HOST
                                            port = hostPort
                                            password = hostPassword
                                        }
                                    }
                                    error = null
                                },
                            )
                            /* The tab's content slides in from the side of the tab it came from
                             * and fades, and the form's height follows it, so a switch reads as a
                             * move rather than a swap. */
                            AnimatedContent(
                                targetState = mode,
                                transitionSpec = {
                                    val forward = (targetState?.ordinal ?: -1) > (initialState?.ordinal ?: -1)
                                    val distance = if (Motion.reduced) 0 else 1
                                    (fadeIn(Motion.move()) + slideInHorizontally(Motion.move()) { if (forward) it / 6 * distance else -it / 6 * distance })
                                        .togetherWith(fadeOut(Motion.quick()) + slideOutHorizontally(Motion.quick()) { if (forward) -it / 6 * distance else it / 6 * distance })
                                        .using(SizeTransform(clip = false) { _, _ -> Motion.move() })
                                },
                                label = "serverMode",
                            ) { m ->
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                                    when (m) {
                                        null -> {
                                            val pickError = error?.takeIf { it == Res.string.connect_server_pick_error }
                                            Text(
                                                text = stringResource(pickError ?: Res.string.connect_server_pick_note),
                                                style = Type.note,
                                                color = if (pickError != null) p.bad else p.inkDim,
                                            )
                                        }
                                        ServerMode.Official -> Segmented(
                                            options = officialPorts,
                                            selected = officialPorts.indexOf(port).coerceAtLeast(0),
                                            onSelect = { port = officialPorts[it]; address = OFFICIAL_HOST },
                                            height = if (dense) Space.row else Space.rowTall,
                                        )
                                        ServerMode.Custom -> {
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
                                            Field(
                                                value = password,
                                                onValueChange = { password = it.trim() },
                                                modifier = Modifier.fillMaxWidth(),
                                                placeholder = stringResource(Res.string.home_password_if_any),
                                                imeAction = ImeAction.Done,
                                                onImeAction = { focusManager.clearFocus(true) },
                                                focusRequester = passwordFocus,
                                                name = stringResource(Res.string.home_password_if_any),
                                            )
                                            val serverError = error?.takeIf { it == Res.string.connect_address_empty_error || it == Res.string.connect_port_empty_error }
                                            if (serverError != null) Text(stringResource(serverError), style = Type.note, color = p.bad)
                                        }
                                        ServerMode.Host -> {
                                            Text(stringResource(Res.string.connect_host_join_note, "$LOCAL_HOST:$hostPort"), style = Type.note, color = p.inkDim)
                                            ServerHostPanel(Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        }

                }
                val engineBlock: @Composable (dense: Boolean) -> Unit = { dense ->
                        Column(Modifier.padding(vertical = if (dense) 0.dp else Space.gap), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
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

                }
                val joinBlock: @Composable (dense: Boolean) -> Unit = { dense ->
                        val shortcutSaved = stringResource(Res.string.home_shortcut_saved, room)
                        Column(Modifier.padding(vertical = if (dense) 0.dp else Space.gap), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                            // The shortcut saver is its own key, a third of the width, over the join key's end.
                            ShortcutKey {
                                error = validate()
                                if (error == null) {
                                    with(platformCallback) { viewmodel.onSaveConfigShortcut(currentConfig()) }
                                    viewmodel.snackItAsync(shortcutSaved)
                                }
                            }
                            PrimaryAction(
                                text = stringResource(Res.string.connect_button_join),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    error = validate()
                                    if (error == null) globalViewmodel.viewModelScope.launch(Dispatchers.Default) { viewmodel.joinRoom(currentConfig()) }
                                },
                            )
                        }
                }

                val twoColumns = maxWidth >= 600.dp && (maxHeight < 620.dp || maxWidth >= 840.dp)
                // Roughly what the two columns need; above it the block sits centred as one unit.
                val blockFits = maxHeight >= 560.dp
                val clearFocus = Modifier.clickable(interactionSource = null, indication = null) { focusManager.clearFocus(force = true) }
                if (twoColumns && blockFits) {
                    /* Wide and tall (a tablet, a desktop window): the two columns are one block,
                     * centred in the window, the join key on the block's foot level with the end of
                     * the left column. A block taller than the window (the host panel open) scrolls
                     * as a whole. */
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(clearFocus),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().heightIn(min = viewport), contentAlignment = Alignment.Center) {
                            TwoColumnBlock(
                                modifier = Modifier.widthIn(max = FORM_MAX_WIDTH * 2 + Space.gutter).padding(horizontal = Space.gutter, vertical = Space.gutter),
                                left = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(Space.gap),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        identityBlock(false)
                                        serverBlock(false)
                                    }
                                },
                                right = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        engineBlock(false)
                                        joinBlock(false)
                                    }
                                },
                            )
                        }
                    }
                } else if (twoColumns) {
                    /* Wide and short (a phone on its side): each column scrolls on its own and the
                     * join key stays pinned to the window's foot, never below the fold. */
                    /* Dense blocks: no portrait spacing, so the form fits the height at rest and
                     * the room left over becomes breathing space between the blocks. Scrolling
                     * only starts when something grows, like the host panel. */
                    Box(Modifier.fillMaxSize().then(clearFocus), contentAlignment = Alignment.TopCenter) {
                        Row(Modifier.widthIn(max = FORM_MAX_WIDTH * 2 + Space.gutter).fillMaxSize().padding(horizontal = Space.gutter)) {
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = viewport).padding(vertical = Space.gap),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    identityBlock(true)
                                    serverBlock(true)
                                }
                            }
                            Spacer(Modifier.width(Space.gutter * 2))
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = viewport).padding(vertical = Space.gap),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    engineBlock(true)
                                    joinBlock(true)
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(clearFocus),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = FORM_MAX_WIDTH).fillMaxWidth().heightIn(min = viewport).padding(horizontal = Space.gutter, vertical = Space.gutter),
                            // Identity at the top, join at the bottom, the rest spread between; the
                            // padding keeps a floor between groups when there is no free height.
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            identityBlock(false)
                            serverBlock(false)
                            engineBlock(false)
                            joinBlock(false)
                        }
                    }
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

/**
 * Backslashes out, trimmed, capped: the same on the form and on a shortcut. A room pasted as the
 * whole `+name:HASH:PASSWORD` string the app prints on creation is split, so it joins the managed
 * room and identifies as its operator instead of creating a room by that literal name.
 */
private fun JoinConfig.sanitised(): JoinConfig {
    val (roomName, operator) = InviteLink.splitOperatorRoom(room)
    return copy(
        user = user.replace("\\", "").trim().substringSafely(0, 149),
        room = roomName.replace("\\", "").trim().substringSafely(0, 34),
        operatorPassword = operator.ifEmpty { operatorPassword },
    )
}

/** A section label; with a [tip] it carries the help glyph, the only place the long words live. */
/**
 * The shortcut saver: a glyph key at the end of its row. The first tap unfolds it across the
 * row to say what it does; the second tap does it and folds it back.
 */
@Composable
private fun ShortcutKey(onSave: () -> Unit) {
    val p = palette
    var expanded by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val name = stringResource(Res.string.connect_button_saveshortcut)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width by animateDpAsState(if (expanded) maxWidth else Space.row, Motion.move(), label = "shortcutWidth")
        val textAlpha by animateFloatAsState(if (expanded) 1f else 0f, Motion.move(), label = "shortcutText")
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(width)
                .height(Space.row)
                .clip(Radius.controlShape)
                .border(Space.hair, if (expanded) p.accent else p.rule, Radius.controlShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button) {
                    Feedback.tick()
                    if (expanded) {
                        expanded = false
                        onSave()
                    } else {
                        expanded = true
                    }
                }
                .hoverable(source)
                .semantics { contentDescription = name }
                .controlStates(source, Radius.controlShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .pressFeedback(source),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(Space.row), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Widgets, contentDescription = null, tint = if (expanded) p.accent else p.ink, modifier = Modifier.size(Space.glyph))
            }
            if (textAlpha > 0f) {
                Text(
                    text = stringResource(Res.string.home_shortcut_explain),
                    style = Type.label,
                    color = p.ink,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f).alpha(textAlpha).padding(end = Space.gap),
                )
            }
        }
    }
}

/**
 * Two columns of equal width with a double gutter between. The left column is measured first
 * and its height becomes the right column's minimum, so a right column that spreads its content
 * ends level with the left one. No intrinsic measurement, so subcompose children are fine.
 */
@Composable
private fun TwoColumnBlock(modifier: Modifier, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    val gap = Space.gutter * 2
    // propagateMinConstraints: the right column must receive the left column's height as its minimum.
    Layout(
        modifier = modifier,
        content = {
            Box(propagateMinConstraints = true) { left() }
            Box(propagateMinConstraints = true) { right() }
        },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val column = ((constraints.maxWidth - gapPx) / 2).coerceAtLeast(0)
        val loose = Constraints(minWidth = column, maxWidth = column, minHeight = 0, maxHeight = Constraints.Infinity)
        val leftPlaceable = measurables[0].measure(loose)
        val rightPlaceable = measurables[1].measure(loose.copy(minHeight = leftPlaceable.height))
        val height = maxOf(leftPlaceable.height, rightPlaceable.height)
        layout(constraints.maxWidth, height) {
            leftPlaceable.placeRelative(0, 0)
            rightPlaceable.placeRelative(column + gapPx, 0)
        }
    }
}

@Composable
private fun FormLabel(text: String, tip: String? = null) {
    Row(Modifier.height(Space.glyph), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.label, color = palette.inkDim)
        if (tip != null) {
            Spacer(Modifier.width(Space.gapTight))
            HelpTip(tip)
        }
    }
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
        /* The help is laid out at all times at its full wrapped height and only made visible
         * on focus, so its appearance never moves the fields; an error draws over the same space. */
        Box(Modifier.fillMaxWidth()) {
            Text(help, style = Type.note, color = p.inkDim, modifier = Modifier.alpha(if (focused && error == null) 1f else 0f))
            if (error != null) Text(error, style = Type.note, color = p.bad)
        }
    }
}

/** Cold starts that show the tips before they stop appearing by themselves. */
private const val TIPS_MAX_SHOWINGS = 3
