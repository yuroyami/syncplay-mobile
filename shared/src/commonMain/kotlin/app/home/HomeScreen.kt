package app.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.PersonPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.LocalGlobalViewmodel
import app.home.components.HomeEnginePicker
import app.home.components.HomeTopBar
import app.home.components.PopupDidYaKnow.DidYaKnowPopup
import app.i18n.AppStrings
import app.i18n.strings
import app.preferences.Preferences
import app.preferences.Preferences.NEVER_SHOW_TIPS
import app.preferences.Preferences.PLAYER_ENGINE
import app.preferences.Preferences.SERVER_PASSWORD
import app.preferences.Preferences.SERVER_PORT
import app.preferences.Preferences.TIPS_SHOWN_COUNT
import app.preferences.preferencesLoadFailure
import app.preferences.set
import app.preferences.value
import app.preferences.watchPref
import app.protocol.OFFICIAL_SERVER_ADDRESS
import app.protocol.OFFICIAL_SERVER_NAME
import app.protocol.Session
import app.server.ui.ServerHostPanel
import app.theme.Motion
import app.theme.Radius
import app.theme.Space
import app.theme.Type
import app.theme.palette
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.Field
import app.uicomponents.controls.FontSizeRange
import app.uicomponents.controls.HelpTip
import app.uicomponents.controls.Icon
import app.uicomponents.controls.PrimaryAction
import app.uicomponents.controls.Segmented
import app.uicomponents.controls.Text
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.pressFeedback
import app.uicomponents.frames.NoticeHost
import app.uicomponents.frames.NoticeSeverity
import app.utils.ExitRoomMode
import app.utils.Platform
import app.utils.availablePlatformPlayerEngines
import app.utils.consumePendingShortcut
import app.utils.ioDispatcher
import app.utils.platform
import app.utils.platformCallback
import app.utils.substringSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val officialServers = listOf("syncplay.pl:8995", "syncplay.pl:8996", "syncplay.pl:8997", "syncplay.pl:8998", "syncplay.pl:8999")

/** The official server is one host; only the port varies, so the picker offers just these. */
val officialPorts = listOf("8995", "8996", "8997", "8998", "8999")

private const val OFFICIAL_HOST = OFFICIAL_SERVER_NAME
private const val LOCAL_HOST = "127.0.0.1"

/** Where the join goes: the official server, someone else's, or the one this app hosts. */
private enum class ServerMode { Official, Custom, Host }

/** One column of the form is never wider than this; two columns share twice it plus a gutter. */
private val FORM_MAX_WIDTH = 420.dp

/**
 * Two columns from here up. Each column is then at least 284dp, the narrowest width at which the
 * three-engine picker still shows every name and badge whole.
 */
private val SPLIT_MIN_WIDTH = 640.dp

/**
 * Under this height (times the text scale) the form is short: two columns pin and scroll on
 * their own, the identity fields share one row, and the spacing tightens. A phone on its side
 * lands here.
 */
private val SHORT_HEIGHT = 400.dp

/**
 * Under this height (times the text scale) one column tightens its spacing so the join key stays
 * on screen at rest. Custom and Host carry more rows, so they tighten a hundred points sooner.
 */
private val COMPACT_HEIGHT = 600.dp
private val COMPACT_HEIGHT_WITH_SERVER_ROWS = 700.dp

/** How far a gap between blocks may grow on a tall window; past it the form floats rather than stretches. */
private val MAX_BLOCK_GAP = 72.dp

/**
 * A short window pairs the identity fields in one row only when the column (times the text
 * scale) leaves each field this much: a narrow phone at big text keeps them stacked and scrolls.
 */
private val PAIR_MIN_COLUMN = 280.dp

/** The spacing set of a height tier. Everything else about the form is the same in both. */
private class FormMetrics(val compact: Boolean) {
    val blockGap: Dp get() = if (compact) Space.gap else Space.gap * 2
    val top: Dp get() = if (compact) Space.gapTight else Space.gap
    val bottom: Dp get() = if (compact) Space.gap else Space.gutter
}

/**
 * The join form with one left edge: label over field, help under it while focused, inline
 * errors, the server as a segmented choice, the engine picker, then the join key with the
 * shortcut saver beside it. The window decides the arrangement: one column that spreads but
 * never stretches, a centred two-column block, or two pinned columns on a short wide window.
 */
@Composable
fun HomeScreenUI(viewmodel: HomeViewmodel) {
    ExitRoomMode()
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

    // The settings file could not be read and the app is running on defaults. Said once, here,
    // because otherwise the only sign is that every preference is suddenly back to new.
    val settingsWereReset = strings.homeSettingsWereReset
    LaunchedEffect(Unit) {
        if (preferencesLoadFailure != null) {
            viewmodel.notices.post(settingsWereReset, NoticeSeverity.Warn, holdMs = 6000L)
        }
    }

    val didYaKnowPopup = remember { mutableStateOf(false) }
    DidYaKnowPopup(didYaKnowPopup)
    LaunchedEffect(null) {
        withContext(ioDispatcher) {
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

            /* The bar above has already paid the status bar and the cutout's top, so the form
             * pads only the sides and the bottom; padding the top again left a dead band under
             * the bar on every phone. imePadding before verticalScroll, so the keyboard scrolls
             * the form. */
            val config = savedConfig
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .imePadding(),
            ) {
                val viewport = maxHeight
                var username by remember(savedConfig) { mutableStateOf(config.user) }
                var room by remember(savedConfig) { mutableStateOf(config.room) }
                var mode by remember(savedConfig) {
                    mutableStateOf<ServerMode?>(
                        when {
                            !hasSavedConfig -> null
                            officialServers.contains("${config.ip.replace(OFFICIAL_SERVER_ADDRESS, OFFICIAL_HOST)}:${config.port}") -> ServerMode.Official
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
                var error by remember { mutableStateOf<JoinError?>(null) }
                // The hosted port is edited in the hosting panel, so an error about it clears from there.
                LaunchedEffect(hostPort) { if (error == JoinError.PortRange && mode == ServerMode.Host) error = null }

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
                fun validate(): JoinError? = when {
                    username.isBlank() -> JoinError.Username
                    room.isBlank() -> JoinError.Room
                    mode == null -> JoinError.ServerChoice
                    // The hosting panel's port field is free text, so the hosted port is checked here too.
                    mode == ServerMode.Host -> if (parsePort(hostPort) == null) JoinError.PortRange else null
                    address.isBlank() -> JoinError.Address
                    port.isBlank() -> JoinError.Port
                    // A port outside 1 to 65535 opens a room whose reconnect loop can never succeed.
                    parsePort(port) == null -> JoinError.PortRange
                    else -> null
                }
                fun currentConfig() = when (mode) {
                    ServerMode.Host -> JoinConfig(username, room, LOCAL_HOST, parsePort(hostPort) ?: 8999, hostPassword)
                    else -> JoinConfig(username, room, address, port.trim().toInt(), password)
                }.sanitised()

                /* Keyboard padding shortens the viewport, and the branches below would
                 * otherwise replace the focused editors. One remembered movable instance
                 * per editor, and per block that holds them, keeps their identity. */
                val onUsernameChange = rememberUpdatedState { value: String ->
                    username = value
                    error = null
                }
                val onRoomChange = rememberUpdatedState { typed: String ->
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
                }
                val onAddressChange = rememberUpdatedState { value: String ->
                    address = value.trim()
                    error = null
                }
                val onPortChange = rememberUpdatedState { value: String ->
                    port = value.trim()
                    error = null
                }
                val onPasswordChange = rememberUpdatedState { value: String ->
                    password = value.trim()
                }
                val onUsernameIme = rememberUpdatedState { roomFocus.requestFocus() }
                val onRoomIme = rememberUpdatedState { focusManager.clearFocus(true) }
                val onAddressIme = rememberUpdatedState { portFocus.requestFocus() }
                val onPortIme = rememberUpdatedState { passwordFocus.requestFocus() }
                val onPasswordIme = rememberUpdatedState { focusManager.clearFocus(true) }
                val onSelectMode = rememberUpdatedState { index: Int ->
                    val next = ServerMode.entries[index]
                    if (next != mode) {
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
                    }
                }
                val onOfficialPort = rememberUpdatedState { index: Int ->
                    port = officialPorts[index]
                    address = OFFICIAL_HOST
                }
                val currentError = rememberUpdatedState(error)
                val currentUsername = rememberUpdatedState(username)
                val currentRoom = rememberUpdatedState(room)
                val currentAddress = rememberUpdatedState(address)
                val currentPort = rememberUpdatedState(port)
                val currentPassword = rememberUpdatedState(password)
                val currentMode = rememberUpdatedState(mode)
                val currentHostPort = rememberUpdatedState(hostPort)

                /* The four blocks of the form. Each is one composable so the three arrangements
                 * below place the same content and only decide where it goes. PairOrStack keeps
                 * the username and room editors in the same slots when they share a row. */
                val identityBlock = remember {
                    movableContentOf { paired: Boolean ->
                        PairOrStack(
                            paired = paired,
                            modifier = Modifier.fillMaxWidth(),
                            first = {
                                FormField(
                                    label = strings.connectUsername,
                                    help = strings.connectUsernameTooltip,
                                    error = currentError.value?.takeIf { it == JoinError.Username }?.message(strings),
                                    value = currentUsername.value,
                                    onValueChange = { onUsernameChange.value(it) },
                                    icon = Icons.Outlined.PersonPin,
                                    focusRequester = usernameFocus,
                                    imeAction = ImeAction.Next,
                                    onImeAction = { onUsernameIme.value() },
                                    showClear = !paired,
                                )
                            },
                            second = {
                                FormField(
                                    label = strings.connectRoomname,
                                    help = strings.connectRoomnameTooltip,
                                    error = currentError.value?.takeIf { it == JoinError.Room }?.message(strings),
                                    value = currentRoom.value,
                                    onValueChange = { onRoomChange.value(it) },
                                    icon = Icons.Outlined.MeetingRoom,
                                    focusRequester = roomFocus,
                                    imeAction = ImeAction.Done,
                                    onImeAction = { onRoomIme.value() },
                                    showClear = !paired,
                                )
                            },
                        )
                    }
                }

                /* Server: official, someone else's, or the one this app hosts. Official keeps a
                 * non-official port from leaking through and clears the password; Custom blanks
                 * both; Host points the join at the local server. */
                val serverBlock = remember {
                    movableContentOf {
                        val colors = palette
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                            FormLabel(
                                text = strings.connectServer,
                                tip = if (currentMode.value == ServerMode.Custom) strings.connectCustomTip else null,
                            )
                            Segmented(
                                options = listOf(strings.connectOfficial, strings.connectCustom, strings.connectHostMine),
                                selected = currentMode.value?.ordinal ?: -1,
                                onSelect = { onSelectMode.value(it) },
                                autoSize = true,
                            )
                            /* The tab's content slides in from the side of the tab it came from
                             * and fades, and the form's height follows it, so a switch reads as a
                             * move rather than a swap. */
                            AnimatedContent(
                                targetState = currentMode.value,
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
                                            val pickError = currentError.value?.takeIf { it == JoinError.ServerChoice }
                                            Text(
                                                text = pickError?.message(strings) ?: strings.connectServerPickNote,
                                                style = Type.note,
                                                color = if (pickError != null) colors.bad else colors.inkDim,
                                            )
                                        }
                                        ServerMode.Official -> Segmented(
                                            options = officialPorts,
                                            selected = officialPorts.indexOf(currentPort.value).coerceAtLeast(0),
                                            onSelect = { onOfficialPort.value(it) },
                                            height = Space.row,
                                            autoSize = true,
                                        )
                                        ServerMode.Custom -> {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.gap)) {
                                                Field(
                                                    value = currentAddress.value,
                                                    onValueChange = { onAddressChange.value(it) },
                                                    modifier = Modifier.weight(2f),
                                                    placeholder = strings.homeIpAddress,
                                                    leading = Icons.Outlined.Lan,
                                                    keyboardType = KeyboardType.Uri,
                                                    imeAction = ImeAction.Next,
                                                    onImeAction = { onAddressIme.value() },
                                                    name = strings.homeIpAddress,
                                                )
                                                Field(
                                                    value = currentPort.value,
                                                    onValueChange = { onPortChange.value(it) },
                                                    modifier = Modifier.weight(1f),
                                                    placeholder = strings.homePort,
                                                    keyboardType = KeyboardType.Number,
                                                    imeAction = ImeAction.Next,
                                                    onImeAction = { onPortIme.value() },
                                                    focusRequester = portFocus,
                                                    name = strings.homePort,
                                                )
                                            }
                                            Field(
                                                value = currentPassword.value,
                                                onValueChange = { onPasswordChange.value(it) },
                                                modifier = Modifier.fillMaxWidth(),
                                                placeholder = strings.homePasswordIfAny,
                                                imeAction = ImeAction.Done,
                                                onImeAction = { onPasswordIme.value() },
                                                focusRequester = passwordFocus,
                                                name = strings.homePasswordIfAny,
                                            )
                                            val serverError = currentError.value?.takeIf { it == JoinError.Address || it == JoinError.Port || it == JoinError.PortRange }
                                            if (serverError != null) Text(serverError.message(strings), style = Type.note, color = colors.bad)
                                        }
                                        ServerMode.Host -> {
                                            Text(strings.connectHostJoinNote("$LOCAL_HOST:${currentHostPort.value}"), style = Type.note, color = colors.inkDim)
                                            if (currentError.value == JoinError.PortRange) Text(JoinError.PortRange.message(strings), style = Type.note, color = colors.bad)
                                            ServerHostPanel(Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val engineBlock: @Composable (compact: Boolean) -> Unit = { compact ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                        FormLabel(strings.connectChooseVideoEngine)
                        val selectedEngine by PLAYER_ENGINE.watchPref()
                        // A saved engine this build no longer ships is replaced once with the platform default.
                        LaunchedEffect(selectedEngine, availablePlatformPlayerEngines) {
                            if (availablePlatformPlayerEngines.none { it.name == selectedEngine }) {
                                availablePlatformPlayerEngines.firstOrNull { it.isDefault }?.let { PLAYER_ENGINE.set(it.name) }
                            }
                        }
                        /* Two different reasons, and asking an iPhone owner about an APK was
                         * neither of them: on Android the engine exists in the other flavour,
                         * anywhere else it does not exist for that platform at all. */
                        val unavailable =
                            if (platform == Platform.Android) strings.homeEngineUnavailableFlavor
                            else strings.homeEngineUnavailableError
                        HomeEnginePicker(
                            modifier = Modifier.fillMaxWidth(),
                            engines = availablePlatformPlayerEngines,
                            selectedEngine = selectedEngine,
                            onSelectEngine = { engine ->
                                viewmodel.viewModelScope.launch(ioDispatcher) {
                                    if (engine.isAvailable) PLAYER_ENGINE.set(engine.name) else viewmodel.snackIt(unavailable)
                                }
                            },
                            compact = compact,
                        )
                    }
                }

                /* Join, with the shortcut saver as its own key beside it. Desktop has no home
                 * screen to pin to, so it gets the join key alone. */
                val joinBlock: @Composable () -> Unit = {
                    val shortcutSaved = strings.homeShortcutSaved(room)
                    JoinRow(
                        onJoin = {
                            error = validate()
                            if (error == null) globalViewmodel.viewModelScope.launch(Dispatchers.Default) { viewmodel.joinRoom(currentConfig()) }
                        },
                        onSaveShortcut = if (platform == Platform.Desktop) null else {
                            {
                                error = validate()
                                if (error == null) {
                                    with(platformCallback) { viewmodel.onSaveConfigShortcut(currentConfig()) }
                                    viewmodel.snackItAsync(shortcutSaved)
                                }
                            }
                        },
                    )
                }

                /* The arrangement, from the window. The height thresholds grow with the text
                 * scale, up to half again, because bigger type needs the tighter tier sooner. */
                val textScale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
                val split = maxWidth >= SPLIT_MIN_WIDTH
                val short = viewport < SHORT_HEIGHT * textScale
                val columnWidth = if (split) (minOf(maxWidth, FORM_MAX_WIDTH * 2 + Space.gutter) - Space.gutter * 4) / 2 else minOf(maxWidth, FORM_MAX_WIDTH) - Space.gutter * 2
                val pairIdentity = short && columnWidth >= PAIR_MIN_COLUMN * textScale
                val compactBelow = if (mode == ServerMode.Custom || mode == ServerMode.Host) COMPACT_HEIGHT_WITH_SERVER_ROWS else COMPACT_HEIGHT
                val metrics = FormMetrics(compact = short || viewport < compactBelow * textScale)
                val blockGap by animateDpAsState(metrics.blockGap, Motion.move(), label = "blockGap")
                /* Tapping the background dismisses the keyboard. It is not a control, so it is
                 * hidden from assistive tech rather than announced as an unnamed button. */
                val clearFocus = Modifier
                    .clickable(interactionSource = null, indication = null) { focusManager.clearFocus(force = true) }
                    .clearAndSetSemantics { }
                when {
                    split -> {
                        /* Wide layouts share one composition: the editors stay in the same
                         * columns when the window shortens, so the keyboard does not dismiss.
                         * Tall centres one scrolling block; short pins two independently
                         * scrolling columns and caps their gaps. */
                        SplitForm(
                            short = short,
                            viewport = viewport,
                            metrics = metrics,
                            blockGap = blockGap,
                            clearFocus = clearFocus,
                            left = {
                                identityBlock(if (short) pairIdentity else false)
                                serverBlock()
                            },
                            right = {
                                engineBlock(short)
                                joinBlock()
                            },
                        )
                    }
                    else -> {
                        /* One column: the blocks spread over the height they are given, but each
                         * gap stops growing at the cap and what is left centres the form, so a
                         * tall window shows a form, not four islands. A window shorter than the
                         * form scrolls it. */
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(clearFocus),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SpreadColumn(
                                modifier = Modifier
                                    .widthIn(max = FORM_MAX_WIDTH)
                                    .fillMaxWidth()
                                    .heightIn(min = viewport)
                                    .padding(start = Space.gutter, end = Space.gutter, top = metrics.top, bottom = metrics.bottom),
                                minGap = blockGap,
                                maxGap = MAX_BLOCK_GAP,
                                centred = true,
                            ) {
                                identityBlock(pairIdentity)
                                serverBlock()
                                engineBlock(metrics.compact)
                                joinBlock()
                            }
                        }
                    }
                }
            }
        }

        NoticeHost(
            queue = viewmodel.notices,
            overVideo = false,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .imePadding()
                .padding(Space.gutter),
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
        room = roomName.replace("\\", "").trim().substringSafely(0, Session.MAX_ROOM_NAME_CHARS),
        operatorPassword = operator.ifEmpty { operatorPassword },
    )
}

/**
 * The join key, and beside it the shortcut saver when the platform has a home screen. The saver
 * unfolds over the join key to say what it does before it acts, so both share one row.
 */
@Composable
internal fun JoinRow(onJoin: () -> Unit, onSaveShortcut: (() -> Unit)?) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        PrimaryAction(
            text = strings.connectButtonJoin,
            modifier = Modifier.fillMaxWidth().padding(end = if (onSaveShortcut != null) Space.touchMin + Space.gap else 0.dp),
            onClick = onJoin,
        )
        if (onSaveShortcut != null) ShortcutKey(onSave = onSaveShortcut)
    }
}

/**
 * The shortcut saver: a glyph key at the end of its row. The first tap unfolds it across the
 * row to say what it does; the second tap does it and folds it back.
 */
@Composable
private fun ShortcutKey(onSave: () -> Unit) {
    val p = palette
    var expanded by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val name = strings.connectButtonSaveshortcut
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width by animateDpAsState(if (expanded) maxWidth else Space.touchMin, Motion.move(), label = "shortcutWidth")
        val textAlpha by animateFloatAsState(if (expanded) 1f else 0f, Motion.move(), label = "shortcutText")
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(width)
                // As tall as the join key beside it, so nothing of that key shows past the fold.
                .height(Space.touchMin)
                .clip(Radius.controlShape)
                // Opaque, so the explanation reads over the join key it unfolds across.
                .background(p.ground)
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
            Box(Modifier.size(Space.touchMin), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Widgets, contentDescription = null, tint = if (expanded) p.accent else p.ink, modifier = Modifier.size(Space.glyph))
            }
            if (textAlpha > 0f) {
                Text(
                    text = strings.homeShortcutExplain,
                    style = Type.label,
                    color = p.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = FontSizeRange(Type.label.fontSize),
                    modifier = Modifier.weight(1f).alpha(textAlpha).padding(end = Space.gap),
                )
            }
        }
    }
}

/**
 * Username and room in one row when [paired], otherwise stacked. The two editors stay in the
 * same composition slots either way, so shortening the window does not replace the focused one.
 */
@Composable
private fun PairOrStack(
    paired: Boolean,
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val gap = Space.gap
    Layout(
        modifier = modifier,
        content = {
            Box { first() }
            Box { second() }
        },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        if (paired) {
            val childWidth = ((constraints.maxWidth - gapPx) / 2).coerceAtLeast(0)
            val child = Constraints(minWidth = childWidth, maxWidth = childWidth, minHeight = 0, maxHeight = Constraints.Infinity)
            val firstPlaceable = measurables[0].measure(child)
            val secondPlaceable = measurables[1].measure(child)
            val height = maxOf(firstPlaceable.height, secondPlaceable.height)
            layout(constraints.maxWidth, height) {
                firstPlaceable.placeRelative(0, 0)
                secondPlaceable.placeRelative(childWidth + gapPx, 0)
            }
        } else {
            val child = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth, minHeight = 0, maxHeight = Constraints.Infinity)
            val firstPlaceable = measurables[0].measure(child)
            val secondPlaceable = measurables[1].measure(child)
            layout(constraints.maxWidth, firstPlaceable.height + gapPx + secondPlaceable.height) {
                firstPlaceable.placeRelative(0, 0)
                secondPlaceable.placeRelative(0, firstPlaceable.height + gapPx)
            }
        }
    }
}

/**
 * A column that spreads its children over the height it is given, with every gap the same and
 * none wider than [maxGap]. Room left after the gaps have reached the cap centres the stack, or
 * stays under it when [centred] is false so two such columns side by side start level. Content
 * taller than the minimum height simply makes the column taller; the host scrolls it.
 */
@Composable
private fun SpreadColumn(
    modifier: Modifier,
    minGap: Dp,
    maxGap: Dp,
    centred: Boolean,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val gapMin = minGap.roundToPx()
        val gapMax = maxOf(maxGap.roundToPx(), gapMin)
        // Loose width, so a block that asks for a fraction of the column (the identity fields) gets it.
        val child = Constraints(minWidth = 0, maxWidth = constraints.maxWidth, minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(child) }
        val slots = (placeables.size - 1).coerceAtLeast(0)
        val contentHeight = placeables.sumOf { it.height }
        val free = (constraints.minHeight - contentHeight - slots * gapMin).coerceAtLeast(0)
        val gap = if (slots == 0) 0 else gapMin + minOf(free / slots, gapMax - gapMin)
        val used = contentHeight + slots * gap
        val height = maxOf(constraints.minHeight, used)
        layout(constraints.maxWidth, height) {
            var y = if (centred) (height - used) / 2 else 0
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height + gap
            }
        }
    }
}

/**
 * The two-column form. [left] and [right] are composed in the same slots whether the window is
 * tall or short, so focused editors are not replaced when the keyboard opens.
 */
@Composable
private fun SplitForm(
    short: Boolean,
    viewport: Dp,
    metrics: FormMetrics,
    blockGap: Dp,
    clearFocus: Modifier,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    val gutter = Space.gutter
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!short) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .then(clearFocus),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (short) Modifier.height(viewport) else Modifier.heightIn(min = viewport)),
            contentAlignment = if (short) Alignment.TopCenter else Alignment.Center,
        ) {
            val gap = gutter * 2
            Layout(
                modifier = Modifier
                    .widthIn(max = FORM_MAX_WIDTH * 2 + gutter)
                    .then(if (short) Modifier.fillMaxHeight() else Modifier)
                    .padding(horizontal = gutter)
                    .padding(vertical = if (short) 0.dp else gutter),
                content = {
                    Box(if (short) Modifier.verticalScroll(rememberScrollState()) else Modifier) {
                        SpreadColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = if (short) viewport else 0.dp)
                                .padding(top = if (short) metrics.top else 0.dp, bottom = if (short) metrics.bottom else 0.dp),
                            minGap = blockGap,
                            maxGap = if (short) MAX_BLOCK_GAP else blockGap,
                            centred = false,
                            content = left,
                        )
                    }
                    // propagateMinConstraints: the tall minHeight must reach SpreadColumn so
                    // maxGap can place the join key at the foot of the left column.
                    Box(
                        modifier = if (short) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                        propagateMinConstraints = true,
                    ) {
                        SpreadColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = if (short) viewport else 0.dp)
                                .padding(top = if (short) metrics.top else 0.dp, bottom = if (short) metrics.bottom else 0.dp),
                            minGap = blockGap,
                            maxGap = if (short) MAX_BLOCK_GAP else viewport,
                            centred = false,
                            content = right,
                        )
                    }
                },
            ) { measurables, constraints ->
                val gapPx = gap.roundToPx()
                val column = ((constraints.maxWidth - gapPx) / 2).coerceAtLeast(0)
                if (short) {
                    val height = constraints.maxHeight
                    val child = Constraints(minWidth = column, maxWidth = column, minHeight = height, maxHeight = height)
                    val leftPlaceable = measurables[0].measure(child)
                    val rightPlaceable = measurables[1].measure(child)
                    layout(constraints.maxWidth, height) {
                        leftPlaceable.placeRelative(0, 0)
                        rightPlaceable.placeRelative(column + gapPx, 0)
                    }
                } else {
                    val loose = Constraints(minWidth = column, maxWidth = column, minHeight = 0, maxHeight = Constraints.Infinity)
                    val leftPlaceable = measurables[0].measure(loose)
                    val rightMin = minOf(leftPlaceable.height, (viewport - gutter * 2).roundToPx().coerceAtLeast(0))
                    val rightPlaceable = measurables[1].measure(loose.copy(minHeight = rightMin))
                    val height = maxOf(leftPlaceable.height, rightPlaceable.height)
                    layout(constraints.maxWidth, height) {
                        leftPlaceable.placeRelative(0, 0)
                        rightPlaceable.placeRelative(column + gapPx, 0)
                    }
                }
            }
        }
    }
}

/** A section label; with a [tip] it carries the help glyph, the only place the long words live. */
@Composable
private fun FormLabel(text: String, tip: String? = null) {
    // A minimum, not a fixed height: bigger system text makes the label taller, not clipped.
    Row(Modifier.heightIn(min = Space.glyph), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.label, color = palette.inkDim)
        if (tip != null) {
            Spacer(Modifier.width(Space.gapTight))
            HelpTip(tip)
        }
    }
}

/**
 * Label over the hairline field, and one note line under it: help while the field is focused,
 * the error in `bad` when validation failed. A field at rest has nothing under it, which is
 * what keeps the form short enough for the join key to stay on screen.
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
    showClear: Boolean = true,
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
            showClear = showClear,
            name = label,
        )
        AnimatedVisibility(
            visible = focused || error != null,
            enter = fadeIn(Motion.quick()) + expandVertically(Motion.move()),
            exit = fadeOut(Motion.quick()) + shrinkVertically(Motion.move()),
        ) {
            Text(error ?: help, style = Type.note, color = if (error != null) p.bad else p.inkDim)
        }
    }
}

/** Cold starts that show the tips before they stop appearing by themselves. */
private const val TIPS_MAX_SHOWINGS = 3

/** Which field the join form is complaining about. The wording comes from the current language. */
private enum class JoinError { Username, Room, ServerChoice, Address, Port, PortRange }

private fun JoinError.message(s: AppStrings): String = when (this) {
    JoinError.Username -> s.connectUsernameEmptyError
    JoinError.Room -> s.connectRoomnameEmptyError
    JoinError.ServerChoice -> s.connectServerPickError
    JoinError.Address -> s.connectAddressEmptyError
    JoinError.Port -> s.connectPortEmptyError
    JoinError.PortRange -> s.connectPortRangeError
}
