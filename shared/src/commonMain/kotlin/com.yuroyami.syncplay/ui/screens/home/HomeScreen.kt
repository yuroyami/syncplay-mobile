package com.yuroyami.syncplay.ui.screens.home

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.yuroyami.syncplay.managers.preferences.Preferences.NEVER_SHOW_TIPS
import com.yuroyami.syncplay.managers.preferences.Preferences.PLAYER_ENGINE
import com.yuroyami.syncplay.managers.preferences.get
import com.yuroyami.syncplay.managers.preferences.set
import com.yuroyami.syncplay.managers.preferences.watchPref
import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.sairaFont
import com.yuroyami.syncplay.ui.popups.PopupDidYaKnow.DidYaKnowPopup
import com.yuroyami.syncplay.ui.screens.adam.LocalGlobalViewmodel
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.theme.Theming.useSyncplayGradient
import com.yuroyami.syncplay.utils.ShowSystemBars
import com.yuroyami.syncplay.utils.availablePlatformPlayerEngines
import com.yuroyami.syncplay.utils.platformCallback
import com.yuroyami.syncplay.utils.substringSafely
import com.yuroyami.syncplay.viewmodels.HomeViewmodel
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
import syncplaymobile.shared.generated.resources.connect_server
import syncplaymobile.shared.generated.resources.connect_username
import syncplaymobile.shared.generated.resources.connect_username_empty_error

val officialServers = listOf("syncplay.pl:8995", "syncplay.pl:8996", "syncplay.pl:8997", "syncplay.pl:8998", "syncplay.pl:8999")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenUI(viewmodel: HomeViewmodel) {
    ShowSystemBars()

    val servers = officialServers + stringResource(Res.string.connect_enter_custom_server)
    var savedConfig by remember { mutableStateOf<JoinConfig?>(null) }

    LaunchedEffect(null) {
        withContext(Dispatchers.IO) {
            savedConfig = JoinConfig.savedConfig()
        }
    }

    val didYaKnowPopup = remember { mutableStateOf(false) }
    DidYaKnowPopup(didYaKnowPopup)

    val globalViewmodel = LocalGlobalViewmodel.current
    LaunchedEffect(null) {
        withContext(Dispatchers.IO) {
            delay(1000)
            val neverShowTips = NEVER_SHOW_TIPS.get()
            if (!globalViewmodel.hasEnteredRoomOnce && !neverShowTips) {
                didYaKnowPopup.value = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(viewmodel.snackManager.snack) },
        topBar = {
            HomeTopBar()
        },
        content = { paddingValues ->
            val focusManager = LocalFocusManager.current

            savedConfig?.let { config ->
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
                        HomeLeadingTitle(
                            string = stringResource(Res.string.connect_username)
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
                        HomeLeadingTitle(
                            string = stringResource(Res.string.connect_roomname)
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
                        HomeLeadingTitle(
                            string = stringResource(Res.string.connect_server)
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
                                onValueChange = {}
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
                                        modifier = Modifier.weight(2.25f).padding(end = 4.dp),
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

                    //TODO There should be no default video engine, force user to choose at first launch
                    //TODO Anchor a tooltip next to our engine selection that explains each engine for better selection

                    Column(
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                        horizontalAlignment = CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeLeadingTitle(
                            string = stringResource(Res.string.connect_choose_video_engine)
                        )

                        //TODO Move to an extension function that retrieves current engine
                        val defaultEngine = availablePlatformPlayerEngines.first { it.isDefault }.name
                        val selectedEngine by PLAYER_ENGINE.watchPref(defaultEngine)

                        HomeAnimatedEngineButtonGroup(
                            modifier = Modifier.fillMaxWidth(0.75f),
                            engines = availablePlatformPlayerEngines,
                            selectedEngine = selectedEngine,
                            onSelectEngine = { engine ->
                                viewmodel.viewModelScope.launch(Dispatchers.IO) {
                                    if (engine.isAvailable) {
                                        PLAYER_ENGINE.set(engine.name)
                                    } else {
                                        viewmodel.snackManager.snackIt("This engine is unavailable. Did you download the right APK?") //TODO Localize
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
                                contentPadding = PaddingValues(vertical = 16.dp),
                                modifier = Modifier.fillMaxWidth(0.85f),
                                onClick = {
                                    viewmodel.viewModelScope.launch(Dispatchers.Default) {
                                        val errorMessage: StringResource? = when {
                                            textUsername.isBlank() -> Res.string.connect_username_empty_error
                                            textRoomname.isBlank() -> Res.string.connect_roomname_empty_error
                                            serverAddress.isBlank() -> Res.string.connect_address_empty_error
                                            serverPort.isBlank() || serverPort.toIntOrNull() == null -> Res.string.connect_port_empty_error
                                            else -> null
                                        }

                                        if (errorMessage != null) {
                                            viewmodel.snackManager.snackIt(getString(errorMessage))
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
                                contentPadding = PaddingValues(vertical = 16.dp),
                                checked = checked,
                                onCheckedChange = { b ->
                                    checked = b
                                    with (platformCallback) {
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
fun HomeLeadingTitle(string: String) {
    FlexibleText(
        text = string,
        size = 18f,
        textAlign = TextAlign.Center,
        fillingColors = listOf(MaterialTheme.colorScheme.primary),
        font = sairaFont,
        strokeColors = listOf(MaterialTheme.colorScheme.scrim),
        shadowColors = if (useSyncplayGradient) Theming.SP_GRADIENT.map { it.copy(alpha = 0.5f) } else listOf(),
        shadowSize = 6f,
        fontWeight = FontWeight.W700
    )
}
