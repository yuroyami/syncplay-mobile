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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
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
import app.home.components.RecentRooms
import app.home.components.PopupDidYaKnow.DidYaKnowPopup
import app.i18n.AppStrings
import app.i18n.strings
import app.preferences.Preferences
import app.preferences.Preferences.NEVER_SHOW_TIPS
import app.preferences.Preferences.PLAYER_ENGINE
import app.preferences.Preferences.REMEMBER_INFO
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
import app.uicomponents.frames.ScrollbarHost
import app.uicomponents.DropPlan
import app.uicomponents.DroppedMedia
import app.uicomponents.MediaDropOverlay
import app.uicomponents.MediaDropTarget
import app.uicomponents.dropRefusal
import app.uicomponents.mediaDropTarget
import app.i18n.Localization
import app.utils.ExitRoomMode
import app.utils.Platform
import app.utils.availablePlatformPlayerEngines
import app.utils.consumePendingShortcut
import app.utils.ioDispatcher
import app.utils.platform
import app.uicomponents.LocalIsTelevision
import app.utils.platformCallback
import app.utils.substringSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

val officialServers = listOf("syncplay.pl:8995", "syncplay.pl:8996", "syncplay.pl:8997", "syncplay.pl:8998", "syncplay.pl:8999")

/** The official server's ports. Its host never changes, so the picker offers only these. */
val officialPorts = listOf("8995", "8996", "8997", "8998", "8999")

private const val OFFICIAL_HOST = OFFICIAL_SERVER_NAME
internal const val LOCAL_HOST = "127.0.0.1"

/** Where the join goes: the official server, someone else's, or the one this app hosts. */
private enum class ServerMode { Official, Custom, Host }

/** The widest that one column of the form gets. Two columns get twice this plus one gutter. */
private val FORM_MAX_WIDTH = 420.dp

/**
 * The form uses two columns from this width up. Each column is then at least 284dp, the narrowest
 * width at which the three-engine picker still shows every name and badge whole.
 */
private val SPLIT_MIN_WIDTH = 640.dp

/**
 * Below this height (times the text scale) the form is short. The identity fields can then share
 * one row, and two columns spread from the top and use the compact engine picker. The height is
 * the form's own, under the top bar. A phone in landscape is short. An open keyboard never makes
 * the form short, because the form measures its height without the keyboard.
 */
private val SHORT_HEIGHT = 400.dp

/**
 * Below this height (times the text scale) the form tightens its spacing, so the join key stays on
 * screen while no help or error line shows. Custom and Host show more rows, so their threshold is
 * 100dp higher.
 */
private val COMPACT_HEIGHT = 600.dp
private val COMPACT_HEIGHT_WITH_SERVER_ROWS = 700.dp

/** The largest gap between blocks. On a taller window, the form centres instead of stretching. */
private val MAX_BLOCK_GAP = 72.dp

/**
 * A short window puts the identity fields in one row only when the column is at least this wide
 * (times the text scale). A narrow phone with large text keeps them stacked and scrolls.
 */
private val PAIR_MIN_COLUMN = 280.dp

/** The spacing of one height tier. Everything else about the form is the same in both tiers. */
private class FormMetrics(val compact: Boolean) {
    val blockGap: Dp get() = if (compact) Space.gap else Space.gap * 2
    val top: Dp get() = if (compact) Space.gapTight else Space.gap
    val bottom: Dp get() = if (compact) Space.gap else Space.gutter
}

/**
 * The home screen: the form to join a room. A room is the group of people who watch together.
 *
 * Everything shares one left edge. Each field has its label above it, its help below it while
 * focused, and its error inline. Then come the server choice, the engine picker (an engine is one
 * of the video players the app can drive), and the join key with the shortcut key beside it. The
 * window picks the arrangement: one column whose gaps grow up to a limit, a centred two-column
 * block, or two spread columns on a short, wide window. The four blocks are composed once and
 * only placed differently, so no window change (the keyboard included) can replace a focused field.
 */
@Composable
fun HomeScreenUI(viewmodel: HomeViewmodel) {
    ExitRoomMode()
    val p = palette
    val globalViewmodel = LocalGlobalViewmodel.current
    val focusManager = LocalFocusManager.current

    // The preference store is an in-memory snapshot, so the saved join details are read in the
    // first composition. The fields never show defaults first and then swap values while the
    // user types.
    val savedConfig by remember { mutableStateOf(JoinConfig.savedConfigNow()) }
    // A fresh install has no saved join details, so the server choice starts empty and the user
    // must pick one.
    val hasSavedConfig = remember { Preferences.JOIN_CONFIG.value() != null }

    // A join from a link, a shortcut or the command line runs once, with the same limits as the
    // form: when the screen appears, and at once when one arrives while the screen shows.
    LaunchedEffect(Unit) {
        consumePendingShortcut()?.let(PendingJoin::post)
        PendingJoin.waiting.collect { waiting ->
            if (waiting != null) PendingJoin.take()?.let { viewmodel.joinRoom(it.sanitised()) }
        }
    }

    // When the settings file could not be read, the app runs on defaults. The screen says so once,
    // because otherwise the only sign is that every preference is suddenly back to its default.
    val settingsWereReset = strings.homeSettingsWereReset
    LaunchedEffect(Unit) {
        if (preferencesLoadFailure != null) {
            viewmodel.notices.post(settingsWereReset, NoticeSeverity.Warn, holdMs = 6000L)
        }
    }

    val television = LocalIsTelevision.current
    val didYaKnowPopup = remember { mutableStateOf(false) }
    DidYaKnowPopup(didYaKnowPopup)
    LaunchedEffect(null) {
        withContext(ioDispatcher) {
            delay(1000)
            // The tips show a few times only, and never after the user has entered a room in this
            // session. The switch in the popup turns them off for good.
            val shown = TIPS_SHOWN_COUNT.value()
            if (!globalViewmodel.hasEnteredRoomOnce && !NEVER_SHOW_TIPS.value() && shown < TIPS_MAX_SHOWINGS) {
                TIPS_SHOWN_COUNT.set(shown + 1)
                didYaKnowPopup.value = true
            }
        }
    }

    // A file or link dropped onto the home screen plays in a room of its own, as Watch alone does (desktop).
    val drop = remember(viewmodel) {
        MediaDropTarget { plan ->
            when (plan) {
                is DropPlan.Open -> globalViewmodel.viewModelScope.launch { viewmodel.joinRoom(null, startMedia = plan.media) }
                is DropPlan.Refuse -> viewmodel.notices.post(Localization.strings.dropRefusal(plan.why), NoticeSeverity.Warn, holdMs = 3000L)
            }
        }
    }

    Box(Modifier.fillMaxSize().mediaDropTarget(drop)) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(viewmodel)

            /* The top bar already pads for the status bar and the top of the cutout, so the form
             * pads only the sides and the bottom. Padding the top again leaves an empty band
             * under the bar. The keyboard is left out on purpose (safeDrawing would include it):
             * the form measures the window without the keyboard, so an opening keyboard changes
             * neither the arrangement nor the spacing. The keyboard pads the scroll container
             * further down, and only scrolls the form. */
            val config = savedConfig
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
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
                // The hosted port is edited in the hosting panel, so a port error clears when that
                // port changes.
                LaunchedEffect(hostPort) { if (error == JoinError.PortRange && mode == ServerMode.Host) error = null }

                val usernameFocus = remember { FocusRequester() }
                val roomFocus = remember { FocusRequester() }
                val portFocus = remember { FocusRequester() }
                val passwordFocus = remember { FocusRequester() }

                // The recent rooms show while the remember setting is on, which is also when joins are kept.
                val rememberInfo by REMEMBER_INFO.watchPref()
                val recentJson by Preferences.RECENT_JOINS.watchPref()
                val recents = remember(recentJson) { RecentJoins.decode(recentJson) }
                // A recent room whose server has a password fills the form, and then the password
                // field takes focus. The field exists only after the Custom fields compose.
                var askPassword by remember { mutableStateOf(0) }
                LaunchedEffect(askPassword) {
                    if (askPassword == 0) return@LaunchedEffect
                    delay(100)
                    runCatching { passwordFocus.requestFocus() }
                }

                // Focuses the first field only in keyboard input mode, so a touch user gets no soft
                // keyboard when the screen opens.
                val inputModeManager = LocalInputModeManager.current
                LaunchedEffect(Unit) {
                    if (inputModeManager.inputMode == InputMode.Keyboard) {
                        delay(150)
                        runCatching { usernameFocus.requestFocus() }
                    }
                }

                /* The join key and the shortcut key share one validation, so the shortcut key
                 * cannot crash on a blank port either. */
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

                /* The four blocks of the form. Each block is one composable with one root, and
                 * FormLayout below only decides where each block goes.
                 *
                 * Identity: the two fields, stacked and as wide as the rows under them. On a short
                 * window they sit side by side in one row and hide their clear buttons, which
                 * would take the width that the names need. */
                val identityBlock: @Composable (paired: Boolean) -> Unit = { paired ->
                    PairOrStack(paired = paired, modifier = Modifier.fillMaxWidth()) {
                        FormField(
                            label = strings.connectUsername,
                            help = strings.connectUsernameTooltip,
                            error = error?.takeIf { it == JoinError.Username }?.message(strings),
                            value = username,
                            onValueChange = { username = it; error = null },
                            icon = Icons.Outlined.PersonPin,
                            focusRequester = usernameFocus,
                            imeAction = ImeAction.Next,
                            onImeAction = { roomFocus.requestFocus() },
                            showClear = !paired,
                        )
                        FormField(
                            label = strings.connectRoomname,
                            help = strings.connectRoomnameTooltip,
                            error = error?.takeIf { it == JoinError.Room }?.message(strings),
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
                            showClear = !paired,
                        )
                    }
                }

                /* Server: the official server, someone else's, or the one this app hosts. Official
                 * replaces a non-official port with 8997 and clears the password. Custom clears
                 * the address, the port and the password. Host points the join at the local
                 * server. */
                val serverBlock: @Composable () -> Unit = {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.gapTight)) {
                        FormLabel(
                            text = strings.connectServer,
                            tip = if (mode == ServerMode.Custom) strings.connectCustomTip else null,
                        )
                        Segmented(
                            options = listOf(strings.connectOfficial, strings.connectCustom, strings.connectHostMine),
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
                            autoSize = true,
                        )
                        /* The new content slides in from the side of the selected tab (from the
                         * right when that tab is to the right of the old one) and fades in. The
                         * form's height animates with it, so a switch reads as a move and not
                         * as a swap. */
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
                                        val pickError = error?.takeIf { it == JoinError.ServerChoice }
                                        Text(
                                            text = pickError?.message(strings) ?: strings.connectServerPickNote,
                                            style = Type.note,
                                            color = if (pickError != null) p.bad else p.inkDim,
                                        )
                                    }
                                    ServerMode.Official -> Segmented(
                                        options = officialPorts,
                                        selected = officialPorts.indexOf(port).coerceAtLeast(0),
                                        onSelect = { port = officialPorts[it]; address = OFFICIAL_HOST },
                                        height = Space.row,
                                        autoSize = true,
                                    )
                                    ServerMode.Custom -> {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.gap)) {
                                            Field(
                                                value = address,
                                                onValueChange = { address = it.trim(); error = null },
                                                modifier = Modifier.weight(2f),
                                                placeholder = strings.homeIpAddress,
                                                leading = Icons.Outlined.Lan,
                                                keyboardType = KeyboardType.Uri,
                                                imeAction = ImeAction.Next,
                                                onImeAction = { portFocus.requestFocus() },
                                                name = strings.homeIpAddress,
                                            )
                                            Field(
                                                value = port,
                                                onValueChange = { port = it.trim(); error = null },
                                                modifier = Modifier.weight(1f),
                                                placeholder = strings.homePort,
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Next,
                                                onImeAction = { passwordFocus.requestFocus() },
                                                focusRequester = portFocus,
                                                name = strings.homePort,
                                            )
                                        }
                                        Field(
                                            value = password,
                                            onValueChange = { password = it.trim() },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = strings.homePasswordIfAny,
                                            imeAction = ImeAction.Done,
                                            onImeAction = { focusManager.clearFocus(true) },
                                            focusRequester = passwordFocus,
                                            name = strings.homePasswordIfAny,
                                        )
                                        val serverError = error?.takeIf { it == JoinError.Address || it == JoinError.Port || it == JoinError.PortRange }
                                        if (serverError != null) Text(serverError.message(strings), style = Type.note, color = p.bad)
                                    }
                                    ServerMode.Host -> {
                                        Text(strings.connectHostJoinNote("$LOCAL_HOST:$hostPort"), style = Type.note, color = p.inkDim)
                                        if (error == JoinError.PortRange) Text(JoinError.PortRange.message(strings), style = Type.note, color = p.bad)
                                        ServerHostPanel(Modifier.fillMaxWidth())
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
                        /* An engine is unavailable for one of two reasons. On Android, it ships
                         * only in the other build flavour. On any other platform, it does not
                         * exist for that platform at all, so the message there names no APK. */
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

                /* Join, with the shortcut key beside it, and the recent rooms under both. Desktop and
                 * the web have no home screen to pin a shortcut to, so they show the join key alone. */
                val joinBlock: @Composable () -> Unit = {
                    val shortcutSaved = strings.homeShortcutSaved(room)
                    val passwordNeeded = strings.homeRecentPassword
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.gap)) {
                        JoinRow(
                            onJoin = {
                                error = validate()
                                if (error == null) globalViewmodel.viewModelScope.launch(Dispatchers.Default) { viewmodel.joinRoom(currentConfig()) }
                            },
                            // A TV launcher cannot pin a shortcut, so a TV shows no shortcut key.
                            onSaveShortcut = if (platform == Platform.Desktop || platform == Platform.Web || television) null else {
                                {
                                    error = validate()
                                    if (error == null) {
                                        with(platformCallback) { viewmodel.onSaveConfigShortcut(currentConfig()) }
                                        viewmodel.snackItAsync(shortcutSaved)
                                    }
                                }
                            },
                        )
                        if (rememberInfo) {
                            RecentRooms(
                                entries = recents,
                                onPick = { entry ->
                                    if (!entry.hasPassword) {
                                        globalViewmodel.viewModelScope.launch(Dispatchers.Default) { viewmodel.joinRoom(entry.toJoinConfig().sanitised()) }
                                    } else {
                                        // No password is kept, so the form takes the entry and asks for it.
                                        username = entry.user
                                        room = entry.room
                                        mode = ServerMode.Custom
                                        address = entry.ip
                                        port = entry.port.toString()
                                        password = ""
                                        error = null
                                        viewmodel.notices.post(passwordNeeded, NoticeSeverity.Info, holdMs = 4000L)
                                        askPassword++
                                    }
                                },
                                onForget = { entry -> globalViewmodel.viewModelScope.launch { RecentJoins.remove(entry) } },
                            )
                        }
                    }
                }

                /* The arrangement, measured from the window without the keyboard. The width picks
                 * one column or two, and the height picks the spacing tier and the identity row.
                 * All of it reaches the layouts as plain values, never as an `if` around a block,
                 * so no threshold can replace a focused field. The height thresholds grow with the
                 * text scale, up to 1.5 times, because larger text needs the tighter tier
                 * sooner. */
                val textScale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
                val split = maxWidth >= SPLIT_MIN_WIDTH
                val short = viewport < SHORT_HEIGHT * textScale
                val columnWidth = if (split) (minOf(maxWidth, FORM_MAX_WIDTH * 2 + Space.gutter) - Space.gutter * 4) / 2 else minOf(maxWidth, FORM_MAX_WIDTH) - Space.gutter * 2
                val pairIdentity = short && columnWidth >= PAIR_MIN_COLUMN * textScale
                val compactBelow = if (mode == ServerMode.Custom || mode == ServerMode.Host) COMPACT_HEIGHT_WITH_SERVER_ROWS else COMPACT_HEIGHT
                val metrics = FormMetrics(compact = viewport < compactBelow * textScale)
                // In two columns the picker shares the right column with the join key only, so it
                // uses the compact size only on a short window.
                val compactPicker = if (split) short else metrics.compact
                val arrangement = when {
                    !split -> FormArrangement.OneColumn
                    short -> FormArrangement.TwoShortColumns
                    else -> FormArrangement.TwoColumns
                }
                // The centred two-column block keeps the same gutter on all sides. The other
                // arrangements use the top and bottom spacing of the height tier.
                val evenGutter = arrangement == FormArrangement.TwoColumns
                val blockGap by animateDpAsState(metrics.blockGap, Motion.move(), label = "blockGap")
                /* Tapping the background hides the keyboard. This is a plain tap detector, not
                 * `clickable`: it adds no semantics and no focus stop, so a screen reader and a
                 * D-pad reach only the controls. The form sits inside this modifier, so clearing
                 * semantics here would hide every control from a screen reader. */
                val clearFocus = Modifier.pointerInput(focusManager) {
                    detectTapGestures { focusManager.clearFocus(force = true) }
                }
                /* imePadding before verticalScroll: the keyboard shortens the scroll container,
                 * not the form, and the container keeps the focused field in view. The form
                 * scrolls as one piece, so two columns always scroll together. */
                val formScroll = rememberScrollState()
                ScrollbarHost(formScroll, Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(formScroll).then(clearFocus),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FormLayout(
                            modifier = Modifier
                                .widthIn(max = if (split) FORM_MAX_WIDTH * 2 + Space.gutter else FORM_MAX_WIDTH)
                                .fillMaxWidth()
                                .heightIn(min = viewport)
                                .padding(
                                    start = Space.gutter,
                                    end = Space.gutter,
                                    top = if (evenGutter) Space.gutter else metrics.top,
                                    bottom = if (evenGutter) Space.gutter else metrics.bottom,
                                ),
                            arrangement = arrangement,
                            leftBlocks = 2,
                            minGap = blockGap,
                            maxGap = MAX_BLOCK_GAP,
                        ) {
                            identityBlock(pairIdentity)
                            serverBlock()
                            engineBlock(compactPicker)
                            joinBlock()
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

        MediaDropOverlay(drop) { media ->
            when (media) {
                is DroppedMedia.File -> strings.homeDropWatchAloneFile(media.name)
                is DroppedMedia.Link -> strings.homeDropWatchAloneLink
            }
        }
    }
}

/**
 * Cleans join details the same way for the form and for a shortcut: it removes backslashes, trims
 * and caps the lengths. A room pasted as the whole `+name:HASH:PASSWORD` string, which the app
 * prints when it creates a managed room, is split. The join then enters the managed room as its
 * operator, instead of creating a room with that literal name.
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
 * The join key, with the shortcut key beside it when [onSaveShortcut] is not null. The shortcut
 * key unfolds over the join key to explain itself before it acts, so both keys share one row.
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
 * The shortcut key: an icon key at the end of its row. The first tap unfolds the key across the
 * row to explain what it does. The second tap saves the shortcut and folds the key back. Left
 * alone for three seconds, the key folds back without saving.
 */
@Composable
private fun ShortcutKey(onSave: () -> Unit) {
    val p = palette
    var expanded by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val name = strings.connectButtonSaveshortcut
    // A press holds the key open, so it never folds under a finger that is about to confirm.
    LaunchedEffect(expanded, pressed) {
        if (expanded && !pressed) {
            delay(3000)
            expanded = false
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width by animateDpAsState(if (expanded) maxWidth else Space.touchMin, Motion.move(), label = "shortcutWidth")
        val textAlpha by animateFloatAsState(if (expanded) 1f else 0f, Motion.move(), label = "shortcutText")
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(width)
                // As tall as the join key, so the unfolded key covers the join key fully.
                .height(Space.touchMin)
                .clip(Radius.controlShape)
                // Opaque, so the explanation stays readable over the join key.
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
 * Places its children in a stack, or side by side with equal widths when [paired]. The flag only
 * changes how the same children are measured and placed, so a switch never replaces a focused
 * field.
 */
@Composable
private fun PairOrStack(paired: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val gap = Space.gap
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val gaps = (measurables.size - 1).coerceAtLeast(0) * gapPx
        val width = if (paired) ((constraints.maxWidth - gaps) / measurables.size.coerceAtLeast(1)).coerceAtLeast(0) else constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = Constraints.Infinity)) }
        val height = if (paired) placeables.maxOfOrNull { it.height } ?: 0 else placeables.sumOf { it.height } + gaps
        layout(constraints.maxWidth, height) {
            var x = 0
            var y = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, y)
                if (paired) x += width + gapPx else y += placeable.height + gapPx
            }
        }
    }
}

/** Where [FormLayout] puts the blocks. Always a value passed down, never a branch in composition. */
private enum class FormArrangement {
    /**
     * One column. The gaps grow, up to a limit, to fill the height, and any leftover height
     * centres the blocks.
     */
    OneColumn,

    /** Two columns as one centred block, with the last block at the foot of the right column. */
    TwoColumns,

    /** Two columns on a short window: each one spreads its blocks from the top. */
    TwoShortColumns,
}

/**
 * Lays out the blocks of the form. Each direct child is one block, and the first [leftBlocks] make
 * the left column. [arrangement] only changes how the same children are measured and placed, so a
 * window that crosses a threshold keeps every block alive, with its focus, caret and scroll
 * position.
 *
 * A spread column gives every gap the same size, from [minGap] up to [maxGap]. In the centred
 * block the gaps stay at [minGap]. The last block (the join key) sits at the foot of the left
 * column or of the window, whichever is higher, so a left column taller than the window never
 * pushes the join key off screen.
 *
 * Content taller than the minimum height makes the form taller, and the host scrolls it. The
 * layout uses no intrinsic measurement, so subcompose children are fine.
 */
@Composable
private fun FormLayout(
    modifier: Modifier,
    arrangement: FormArrangement,
    leftBlocks: Int,
    minGap: Dp,
    maxGap: Dp,
    content: @Composable () -> Unit,
) {
    val columnGap = Space.gutter * 2
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val gapMin = minGap.roundToPx()
        val gapMax = maxOf(maxGap.roundToPx(), gapMin)
        val twoColumns = arrangement != FormArrangement.OneColumn
        val columnGapPx = if (twoColumns) columnGap.roundToPx() else 0
        val column = if (twoColumns) ((constraints.maxWidth - columnGapPx) / 2).coerceAtLeast(0) else constraints.maxWidth
        // Loose width constraints: every block fills its column by itself.
        val placeables = measurables.map { it.measure(Constraints(maxWidth = column)) }
        val left = if (twoColumns) placeables.take(leftBlocks) else placeables
        val right = placeables.drop(left.size)
        val rightX = column + columnGapPx

        fun stacked(blocks: List<Placeable>, gap: Int) = blocks.sumOf { it.height } + (blocks.size - 1).coerceAtLeast(0) * gap
        fun spreadGap(blocks: List<Placeable>): Int {
            val slots = (blocks.size - 1).coerceAtLeast(0)
            if (slots == 0) return 0
            val free = (constraints.minHeight - stacked(blocks, gapMin)).coerceAtLeast(0)
            return gapMin + minOf(free / slots, gapMax - gapMin)
        }
        fun Placeable.PlacementScope.stack(blocks: List<Placeable>, x: Int, top: Int, gap: Int) {
            var y = top
            blocks.forEach { block ->
                block.placeRelative(x, y)
                y += block.height + gap
            }
        }

        when (arrangement) {
            FormArrangement.OneColumn -> {
                val gap = spreadGap(left)
                val used = stacked(left, gap)
                val height = maxOf(constraints.minHeight, used)
                layout(constraints.maxWidth, height) { stack(left, 0, (height - used) / 2, gap) }
            }
            FormArrangement.TwoShortColumns -> {
                val leftGap = spreadGap(left)
                val rightGap = spreadGap(right)
                val height = maxOf(constraints.minHeight, stacked(left, leftGap), stacked(right, rightGap))
                layout(constraints.maxWidth, height) {
                    stack(left, 0, 0, leftGap)
                    stack(right, rightX, 0, rightGap)
                }
            }
            FormArrangement.TwoColumns -> {
                val leftHeight = stacked(left, gapMin)
                val rightHeight = maxOf(stacked(right, gapMin), minOf(leftHeight, constraints.minHeight))
                val blockHeight = maxOf(leftHeight, rightHeight)
                val height = maxOf(constraints.minHeight, blockHeight)
                layout(constraints.maxWidth, height) {
                    // Rounds half up, the way Alignment.Center rounds an odd remainder.
                    val top = ((height - blockHeight) / 2f).roundToInt()
                    stack(left, 0, top, gapMin)
                    stack(right.dropLast(1), rightX, top, gapMin)
                    right.lastOrNull()?.let { it.placeRelative(rightX, top + rightHeight - it.height) }
                }
            }
        }
    }
}

/** A section label. With a [tip] it also shows a help button, the only place for the long text. */
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
 * A label over the field, and one note line under it: the help while the field is focused, or the
 * error (in the `bad` colour) after validation fails. A field with no focus and no error shows
 * nothing under it, which keeps the form short enough for the join key to stay on screen.
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

/**
 * How many times the tips popup shows before it stops by itself. Every time the home screen
 * appears before the first room of a session counts, not only a cold start.
 */
private const val TIPS_MAX_SHOWINGS = 3

/** A validation error of the join form. [message] gives its text in the current language. */
private enum class JoinError { Username, Room, ServerChoice, Address, Port, PortRange }

private fun JoinError.message(s: AppStrings): String = when (this) {
    JoinError.Username -> s.connectUsernameEmptyError
    JoinError.Room -> s.connectRoomnameEmptyError
    JoinError.ServerChoice -> s.connectServerPickError
    JoinError.Address -> s.connectAddressEmptyError
    JoinError.Port -> s.connectPortEmptyError
    JoinError.PortRange -> s.connectPortRangeError
}
