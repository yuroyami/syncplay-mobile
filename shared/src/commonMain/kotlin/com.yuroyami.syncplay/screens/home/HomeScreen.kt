package com.yuroyami.syncplay.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonPin
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.components.ComposeUtils.FlexibleFancyText
import com.yuroyami.syncplay.components.ComposeUtils.SmartFancyIcon
import com.yuroyami.syncplay.components.popups.PopupAPropos.AProposPopup
import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.settings.DataStoreKeys.MISC_PLAYER_ENGINE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_SP_MEDIA_DIRS
import com.yuroyami.syncplay.settings.SETTINGS_GLOBAL
import com.yuroyami.syncplay.settings.SettingsUI
import com.yuroyami.syncplay.settings.valueFlow
import com.yuroyami.syncplay.settings.writeValue
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.ui.Paletting.SP_GRADIENT
import com.yuroyami.syncplay.ui.ThemeMenu
import com.yuroyami.syncplay.utils.CommonUtils.substringSafely
import com.yuroyami.syncplay.utils.availablePlatformPlayerEngines
import com.yuroyami.syncplay.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.connect_address_empty_error
import syncplaymobile.shared.generated.resources.connect_button_join
import syncplaymobile.shared.generated.resources.connect_enter_custom_server
import syncplaymobile.shared.generated.resources.connect_port_empty_error
import syncplaymobile.shared.generated.resources.connect_roomname_a
import syncplaymobile.shared.generated.resources.connect_roomname_empty_error
import syncplaymobile.shared.generated.resources.connect_server_a
import syncplaymobile.shared.generated.resources.connect_username_a
import syncplaymobile.shared.generated.resources.connect_username_empty_error
import syncplaymobile.shared.generated.resources.syncplay_logo_gradient

val officialServers = listOf("syncplay.pl:8995", "syncplay.pl:8996", "syncplay.pl:8997", "syncplay.pl:8998", "syncplay.pl:8999")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenUI() {
    val viewmodel = LocalViewmodel.current
    val servers = officialServers + stringResource(Res.string.connect_enter_custom_server)
    var savedConfig by remember { mutableStateOf<JoinConfig?>(null) }

    LaunchedEffect(null) {
        withContext(Dispatchers.IO) {
            savedConfig = JoinConfig.savedConfig()
        }
    }

    val scope = rememberCoroutineScope { Dispatchers.IO }
    val focusManager = LocalFocusManager.current

    /* Using a Scaffold manages our top-level layout */
    Scaffold(
        snackbarHost = { SnackbarHost(viewmodel.snack) },
        topBar = {
            SyncplayTopBar()
        },
        content = { paddingValues ->
            savedConfig?.let { config ->
                Column(
                    modifier = Modifier.fillMaxSize()
                        .windowInsetsPadding(BottomAppBarDefaults.windowInsets)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    /* Instead of consuming paddingValues, we create a spacer with that height */
                    Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))

                    /* higher-level variables which are needed for logging in */
                    var textUsername by remember { mutableStateOf(config.user) }
                    var textRoomname by remember { mutableStateOf(config.room) }

                    var serverIsPublic by remember { mutableStateOf(true) }

                    var selectedServer by remember { mutableStateOf("${config.ip}:${config.port}") }

                    var serverAddress by remember { mutableStateOf(config.ip) }
                    var serverPort by remember { mutableStateOf(config.port.toString()) }
                    var serverPassword by remember { mutableStateOf(config.pw) }

                    /* Username */
                    Column(
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                        horizontalAlignment = CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FlexibleFancyText(
                            text = stringResource(Res.string.connect_username_a),
                            size = 20f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = Font(Res.font.Directive4_Regular),
                            shadowColors = listOf(Color.Gray)
                        )

                        HomeTextField(
                            modifier = Modifier.fillMaxWidth(0.75f),
                            icon = Icons.Outlined.PersonPin,
                            value = textUsername,
                            onValueChange = { s -> textUsername = s.trim() }
                        )
                    }

                    /* Roomname */
                    Column(
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                        horizontalAlignment = CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FlexibleFancyText(
                            text = stringResource(Res.string.connect_roomname_a),
                            size = 20f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = Font(Res.font.Directive4_Regular),
                            shadowColors = listOf(Color.Gray)
                        )

                        HomeTextField(
                            modifier = Modifier.fillMaxWidth(0.75f),
                            icon = Icons.Outlined.MeetingRoom,
                            value = textRoomname,
                            onValueChange = { s -> textRoomname = s.trim() }
                        )
                    }

                    /* Server */
                    val expanded = remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                        horizontalAlignment = CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FlexibleFancyText(
                            text = stringResource(Res.string.connect_server_a),
                            size = 20f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = Font(Res.font.Directive4_Regular),
                            shadowColors = listOf(Color.Gray)
                        )

                        ExposedDropdownMenuBox(
                            expanded = expanded.value,
                            onExpandedChange = {
                                expanded.value = !expanded.value
                            }
                        ) {
                            HomeTextField(
                                modifier = Modifier.fillMaxWidth(0.75f).menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                icon = Icons.Outlined.Lan,
                                value = selectedServer.replace("151.80.32.178", "syncplay.pl"),
                                dropdownState = expanded,
                                onValueChange = { s -> }
                            )

                            ExposedDropdownMenu(
                                modifier = Modifier.background(color = MaterialTheme.colorScheme.tertiaryContainer),
                                expanded = expanded.value,
                                onDismissRequest = {
                                    expanded.value = false
                                }) {
                                servers.forEach { server ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(server.replace("151.80.32.178", "syncplay.pl"), color = Color.White)
                                        },
                                        onClick = {
                                            selectedServer = server
                                            expanded.value = false

                                            if (server != servers[5]) {
                                                serverAddress = "syncplay.pl"
                                                serverPort =
                                                    selectedServer.substringAfter("syncplay.pl:")
                                                serverIsPublic = true
                                                serverPassword = ""
                                            } else {
                                                serverIsPublic = false
                                                serverAddress = ""
                                                serverPort = ""
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = !serverIsPublic,
                            modifier = Modifier.fillMaxWidth(0.75f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    HomeTextField(
                                        modifier = Modifier.weight(3f).padding(end = 4.dp),
                                        value = serverAddress,
                                        onValueChange = { serverAddress = it.trim() },
                                        label = "IP Address", //TODO Localize
                                        cornerRadius = 16.dp
                                    )

                                    HomeTextField(
                                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                                        value = serverPort,
                                        onValueChange = { serverPort = it.trim() },
                                        type = KeyboardType.Number,
                                        label = "Port", //TODO Localize
                                        cornerRadius = 16.dp
                                    )
                                }

                                HomeTextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = serverPassword,
                                    onValueChange = { serverPassword = it.trim() },
                                    type = KeyboardType.Password,
                                    label = "Password (if any)", //TODO Localize
                                    cornerRadius = 16.dp
                                )
                            }
                        }
                    }

                    /* Buttons */
                    Column(
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                        horizontalAlignment = CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FlexibleFancyText(
                            text = "Choose your video engine", //TODO Localize
                            size = 18f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = Font(Res.font.Directive4_Regular),
                            shadowColors = listOf(Color.Gray)
                        )

                        //TODO Move to an extension function that retrieves current engine
                        val defaultEngine = availablePlatformPlayerEngines.first { it.isDefault }.name
                        val selectedEngine by valueFlow(MISC_PLAYER_ENGINE, defaultEngine).collectAsState(initial = defaultEngine)

                        AnimatedEngineButtonGroup(
                            modifier = Modifier.fillMaxWidth(0.75f),
                            engines = availablePlatformPlayerEngines,
                            selectedEngine = selectedEngine,
                            onSelectEngine = { engine ->
                                scope.launch(Dispatchers.IO) {
                                    if (engine.isAvailable) {
                                        writeValue(MISC_PLAYER_ENGINE, engine.name)
                                    } else {
                                        viewmodel.snackIt("This engine is unavailable. Did you download the right APK?") //TODO Localize
                                    }
                                }
                            }
                        )
                    }

                    /* join button + shortcut saver */
                    SplitButtonLayout(
                        modifier = Modifier.fillMaxWidth(0.75f),
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(
                                contentPadding = PaddingValues(vertical = 24.dp),
                                modifier = Modifier.fillMaxWidth(0.85f),
                                onClick = {
                                    scope.launch {
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
                                    Icon(imageVector = Icons.Filled.Api, "")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(Res.string.connect_button_join), fontSize = 18.sp)
                                }
                            )
                        },
                        trailingButton = {
                            var checked by remember { mutableStateOf(false) }

                            SplitButtonDefaults.TrailingButton(
                                contentPadding = PaddingValues(vertical = 24.dp),
                                checked = checked,
                                onCheckedChange = { b ->
                                    checked = b
                                    platformCallback.onSaveConfigShortcut(
                                        JoinConfig(
                                            textUsername.replace("\\", "").trim(),
                                            textRoomname.replace("\\", "").trim(),
                                            serverAddress,
                                            serverPort.toInt(),
                                            serverPassword
                                        )
                                    )
                                },
                                content = {
                                    Icon(imageVector = Icons.Filled.Widgets, null)
                                }
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    )
}

@Composable
fun SyncplayTopBar() {
    val aboutpopupState = remember { mutableStateOf(false) }

    AProposPopup(aboutpopupState)

    Card(
        modifier = Modifier.fillMaxWidth()
            .background(color = Color.Transparent /* Paletting.BG_DARK_1 */),
        shape = RoundedCornerShape(
            topEnd = 0.dp, topStart = 0.dp, bottomEnd = 12.dp, bottomStart = 12.dp
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
    ) {
        /* Settings Button */
        val settingState = remember { mutableIntStateOf(0) }

        Column(
            horizontalAlignment = CenterHorizontally,
            modifier = Modifier.animateContentSize()
        ) {
            ListItem(
                modifier = Modifier.fillMaxWidth().padding(top = (TopAppBarDefaults.windowInsets.asPaddingValues().calculateTopPadding())),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                trailingContent = {
                    Row(verticalAlignment = CenterVertically) {
                        var themePopupState by remember { mutableStateOf(false) }
                        SmartFancyIcon(
                            icon = Icons.Outlined.Palette,
                            size = 38,
                            tintColors = SP_GRADIENT,
                            shadowColors = listOf(MaterialTheme.colorScheme.primary),
                            onClick = {
                                themePopupState = true
                            }
                        )
                        ThemeMenu(themePopupState, onDismiss = { themePopupState = false })

                        SmartFancyIcon(
                            icon = when (settingState.intValue) {
                                0 -> Icons.Filled.Settings
                                1 -> Icons.Filled.Close
                                else -> Icons.AutoMirrored.Filled.Redo
                            },
                            size = 38,
                            tintColors = SP_GRADIENT,
                            shadowColors = listOf(MaterialTheme.colorScheme.primary),
                            onClick = {
                                when (settingState.intValue) {
                                    0 -> settingState.intValue = 1
                                    1 -> settingState.intValue = 0
                                    else -> settingState.intValue = 1
                                }
                            }
                        )
                    }
                },

                /* Syncplay Header (logo + text) */
                headlineContent = {
                    Row(
                        modifier = Modifier.clip(CircleShape).background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    SP_GRADIENT.first().copy(0.05f),
                                    SP_GRADIENT.last().copy(0.05f)
                                )
                            )
                        ).clickable(
                            enabled = true,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(
                                bounded = false, color = Color(100, 100, 100, 200)
                            )
                        ) { aboutpopupState.value = true }.padding(16.dp)
                    ) {
                        Image(
                            imageVector = vectorResource(Res.drawable.syncplay_logo_gradient),
                            contentDescription = "",
                            modifier = Modifier.height(32.dp).aspectRatio(1f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(modifier = Modifier.padding(bottom = 6.dp)) {
                            Text(
                                modifier = Modifier.wrapContentWidth(),
                                text = "Syncplay",
                                style = TextStyle(
                                    color = Paletting.SP_PALE,
                                    drawStyle = Stroke(
                                        miter = 10f,
                                        width = 2f,
                                        join = StrokeJoin.Round
                                    ),
                                    shadow = Shadow(
                                        color = Paletting.SP_INTENSE_PINK,
                                        offset = Offset(0f, 10f),
                                        blurRadius = 5f
                                    ),
                                    fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                                    fontSize = 24.sp,
                                )
                            )

                            Text(
                                text = "Syncplay", style = TextStyle(
                                    brush = Brush.linearGradient(colors = SP_GRADIENT),
                                    fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                                    fontSize = 24.sp,
                                )
                            )
                        }
                    }
                },
            )/* Settings */


            AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = settingState.intValue != 0,
                enter = scaleIn(),
                exit = scaleOut()
            ) {
                SettingsUI.SettingsGrid(
                    modifier = Modifier.fillMaxWidth(),
                    settings = SETTINGS_GLOBAL,
                    state = settingState,
                    layout = SettingsUI.Layout.SETTINGS_GLOBAL,
                    onEnteredSomeCategory = {
                        settingState.intValue = 2
                    })
            }
            val dirs = valueFlow(
                PREF_SP_MEDIA_DIRS,
                emptySet<String>()
            ).collectAsState(initial = emptySet())
            var loaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                loaded = true
            }
            AnimatedVisibility(dirs.value.isEmpty() && loaded && settingState.intValue != 2) {
                Text(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            settingState.intValue = 2
                        }.padding(16.dp),
                    text = "Don't forget to set default media directories in Settings > General > Media Directories for Shared Playlist!",
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                )
            }
        }
    }
}


