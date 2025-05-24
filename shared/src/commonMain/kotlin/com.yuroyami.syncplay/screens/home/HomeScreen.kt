package com.yuroyami.syncplay.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgeDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.components.ComposeUtils.FlexibleFancyText
import com.yuroyami.syncplay.components.ComposeUtils.SmartFancyIcon
import com.yuroyami.syncplay.components.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.components.getRegularFont
import com.yuroyami.syncplay.components.popups.PopupAPropos.AProposPopup
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.screens.adam.LocalViewmodel
import com.yuroyami.syncplay.settings.DataStoreKeys.MISC_PLAYER_ENGINE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_SP_MEDIA_DIRS
import com.yuroyami.syncplay.settings.SettingCategory
import com.yuroyami.syncplay.settings.SettingsUI
import com.yuroyami.syncplay.settings.sgGLOBAL
import com.yuroyami.syncplay.settings.valueFlow
import com.yuroyami.syncplay.settings.writeValue
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.ui.ThemeMenu
import com.yuroyami.syncplay.utils.CommonUtils.substringSafely
import com.yuroyami.syncplay.utils.getDefaultEngine
import com.yuroyami.syncplay.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.connect_address_empty_error
import syncplaymobile.shared.generated.resources.connect_button_current_engine
import syncplaymobile.shared.generated.resources.connect_button_join
import syncplaymobile.shared.generated.resources.connect_enter_custom_server
import syncplaymobile.shared.generated.resources.connect_port_empty_error
import syncplaymobile.shared.generated.resources.connect_roomname_a
import syncplaymobile.shared.generated.resources.connect_roomname_b
import syncplaymobile.shared.generated.resources.connect_roomname_empty_error
import syncplaymobile.shared.generated.resources.connect_server_a
import syncplaymobile.shared.generated.resources.connect_username_a
import syncplaymobile.shared.generated.resources.connect_username_b
import syncplaymobile.shared.generated.resources.connect_username_empty_error
import syncplaymobile.shared.generated.resources.exoplayer
import syncplaymobile.shared.generated.resources.mpv
import syncplaymobile.shared.generated.resources.swift
import syncplaymobile.shared.generated.resources.syncplay_logo_gradient
import syncplaymobile.shared.generated.resources.vlc

private val LocalGlobalSettings = staticCompositionLocalOf<List<SettingCategory>> { error("No Global Settings provided") }

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

    CompositionLocalProvider(
        LocalGlobalSettings provides sgGLOBAL()
    ) {
        val scope = rememberCoroutineScope { Dispatchers.IO }
        val snacky = remember { SnackbarHostState().also { viewmodel.snack = it } }
        val focusManager = LocalFocusManager.current

        /* Using a Scaffold manages our top-level layout */
        Scaffold(
            snackbarHost = { SnackbarHost(snacky) },
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
                            horizontalAlignment = CenterHorizontally,
                        ) {

                            FlexibleFancyText(
                                text = stringResource(Res.string.connect_username_a),
                                size = 20f,
                                textAlign = TextAlign.Center,
                                fillingColors = listOf(MaterialTheme.colorScheme.primary),
                                font = Font(Res.font.Directive4_Regular),
                                shadowColors = listOf(Color.Gray)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(contentAlignment = Alignment.Center) {
                                OutlinedTextField(
                                    modifier = Modifier.focusable(false),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    ),
                                    singleLine = true,
                                    readOnly = true,
                                    value = "",
                                    label = { Text(" ") },
                                    supportingText = { },
                                    onValueChange = { },
                                )

                                OutlinedTextField(
                                    modifier = Modifier.gradientOverlay(),
                                    singleLine = true,
                                    label = { Text(stringResource(Res.string.connect_username_b)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.PersonPin, ""
                                        )
                                    },
                                    supportingText = { /* Text(stringResource(R.string.connect_username_c), fontSize = 10.sp) */ },
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.moveFocus(focusDirection = FocusDirection.Next)
                                    }),
                                    value = textUsername,
                                    onValueChange = { s ->
                                        textUsername = s.trim()
                                    }
                                )
                            }
                        }

                        /* Roomname */
                        Column(
                            modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                            horizontalAlignment = CenterHorizontally,
                        ) {


                            FlexibleFancyText(
                                text = stringResource(Res.string.connect_roomname_a),
                                size = 20f,
                                textAlign = TextAlign.Center,
                                fillingColors = listOf(MaterialTheme.colorScheme.primary),
                                font = Font(Res.font.Directive4_Regular),
                                shadowColors = listOf(Color.Gray)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Box {
                                OutlinedTextField(
                                    modifier = Modifier.focusable(false),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    ),
                                    singleLine = true,
                                    readOnly = true,
                                    value = "",
                                    label = { Text(" ") },
                                    supportingText = { Text("") },
                                    onValueChange = { },
                                )

                                OutlinedTextField(
                                    modifier = Modifier.gradientOverlay(),
                                    singleLine = true,
                                    label = { Text(stringResource(Res.string.connect_roomname_b)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.MeetingRoom, ""
                                        )
                                    },
                                    supportingText = { /* Text(stringResource(R.string.connect_roomname_c), fontSize = 10.sp) */ },
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.moveFocus(focusDirection = FocusDirection.Next)
                                    }),
                                    value = textRoomname,
                                    onValueChange = { s -> textRoomname = s.trim() })
                            }
                        }

                        /* Server */
                        val expanded = remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                            horizontalAlignment = CenterHorizontally,
                        ) {
                            FlexibleFancyText(
                                text = stringResource(Res.string.connect_server_a),
                                size = 20f,
                                textAlign = TextAlign.Center,
                                fillingColors = listOf(MaterialTheme.colorScheme.primary),
                                font = Font(Res.font.Directive4_Regular),
                                shadowColors = listOf(Color.Gray)
                            )

                            Spacer(modifier = Modifier.height(10.dp))



                            ExposedDropdownMenuBox(
                                expanded = expanded.value, onExpandedChange = {
                                    expanded.value = !expanded.value
                                }) {
                                Box {
                                    OutlinedTextField(
                                        modifier = Modifier.focusable(false),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        ),
                                        singleLine = true,
                                        readOnly = true,
                                        value = "",
                                        supportingText = { Text("") },
                                        onValueChange = { },
                                    )
                                    OutlinedTextField(
                                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).gradientOverlay(),
                                        singleLine = true,
                                        readOnly = true,
                                        value = selectedServer.replace(
                                            "151.80.32.178", "syncplay.pl"
                                        ),
                                        supportingText = { /* Text(stringResource(R.string.connect_server_c), fontSize = 9.sp) */ },
                                        onValueChange = { },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value)
                                        })
                                }
                                ExposedDropdownMenu(
                                    modifier = Modifier.background(color = MaterialTheme.colorScheme.tertiaryContainer),
                                    expanded = expanded.value,
                                    onDismissRequest = {
                                        expanded.value = false
                                    }) {
                                    servers.forEach { server ->
                                        DropdownMenuItem(text = {
                                            Text(
                                                server.replace(
                                                    "151.80.32.178", "syncplay.pl"
                                                ), color = Color.White
                                            )
                                        }, onClick = {
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
                                        })
                                    }
                                }
                            }


                            Spacer(modifier = Modifier.height(12.dp))

                            if (!serverIsPublic) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    TextField(
                                        modifier = Modifier.fillMaxWidth(0.5f),
                                        shape = RoundedCornerShape(16.dp),
                                        singleLine = true,
                                        value = serverAddress,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent,
                                        ),
                                        onValueChange = { serverAddress = it.trim() },
                                        keyboardActions = KeyboardActions(onDone = {
                                            focusManager.moveFocus(FocusDirection.Next)
                                        }),
                                        textStyle = TextStyle(
                                            brush = Brush.linearGradient(
                                                colors = Paletting.SP_GRADIENT
                                            ),
                                            fontFamily = FontFamily(getRegularFont()),
                                            fontSize = 16.sp,
                                        ),
                                        label = {
                                            Text("IP Address", color = Color.Gray)
                                        })


                                    TextField(
                                        modifier = Modifier.fillMaxWidth(0.5f),
                                        shape = RoundedCornerShape(16.dp),
                                        singleLine = true,
                                        value = serverPort,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        keyboardActions = KeyboardActions(onDone = {
                                            focusManager.moveFocus(FocusDirection.Next)
                                        }),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent,
                                        ),
                                        onValueChange = { serverPort = it.trim() },
                                        textStyle = TextStyle(
                                            brush = Brush.linearGradient(
                                                colors = Paletting.SP_GRADIENT
                                            ),
                                            fontFamily = FontFamily(getRegularFont()),
                                            fontSize = 16.sp,
                                        ),
                                        label = {
                                            Text("Port", color = Color.Gray)
                                        })
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                TextField(
                                    modifier = Modifier.fillMaxWidth(0.8f),
                                    shape = RoundedCornerShape(16.dp),
                                    singleLine = true,
                                    enabled = !serverIsPublic,
                                    value = serverPassword,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.moveFocus(FocusDirection.Next)
                                    }),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                    ),
                                    onValueChange = { serverPassword = it },
                                    textStyle = TextStyle(
                                        brush = Brush.linearGradient(
                                            colors = Paletting.SP_GRADIENT
                                        ),
                                        fontFamily = FontFamily(getRegularFont()),
                                        fontSize = 16.sp,
                                    ),
                                    label = {
                                        Text("Password (empty if undefined)", color = Color.Gray)
                                    })

                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        /* Buttons */
                        val defaultEngine = remember { getDefaultEngine() }
                        val player = valueFlow(
                            MISC_PLAYER_ENGINE, defaultEngine
                        ).collectAsState(initial = defaultEngine)

                        Column(horizontalAlignment = CenterHorizontally) {
                            Row(horizontalArrangement = Arrangement.Center) {
                                /* shortcut button */
                                Button(
                                    border = BorderStroke(
                                        width = 2.dp, color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.height(54.dp).aspectRatio(1.6f),
                                    shape = RoundedCornerShape(25),
                                    onClick = {
                                        platformCallback.onSaveConfigShortcut(
                                            JoinConfig(
                                                textUsername.replace("\\", "").trim(),
                                                textRoomname.replace("\\", "").trim(),
                                                serverAddress,
                                                serverPort.toInt(),
                                                serverPassword
                                            )
                                        )
                                    }) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                modifier = Modifier.padding(8.dp),
                                                containerColor = BadgeDefaults.containerColor.copy(0.5f)
                                            ) {

                                                Icon(
                                                    imageVector = Icons.Filled.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }) {

                                        Icon(
                                            imageVector = Icons.Filled.Widgets,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))


                                Button(
                                    border = BorderStroke(
                                        width = 2.dp, color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.height(54.dp).aspectRatio(1.6f),
                                    shape = RoundedCornerShape(25),
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            if (defaultEngine != BasePlayer.ENGINE.ANDROID_EXOPLAYER.name) {
                                                writeValue(
                                                    MISC_PLAYER_ENGINE,
                                                    BasePlayer.ENGINE.valueOf(player.value)
                                                        .getNextPlayer().name
                                                )
                                            }
                                        }
                                    }) {
                                    Image(
                                        painter = painterResource(
                                            with(Res.drawable) {
                                                when (player.value) {
                                                    BasePlayer.ENGINE.ANDROID_EXOPLAYER.name -> exoplayer
                                                    BasePlayer.ENGINE.ANDROID_MPV.name -> mpv
                                                    BasePlayer.ENGINE.ANDROID_VLC.name -> vlc
                                                    BasePlayer.ENGINE.IOS_AVPLAYER.name -> swift
                                                    BasePlayer.ENGINE.IOS_VLC.name -> vlc
                                                    else -> exoplayer
                                                }
                                            }),
                                        contentScale = ContentScale.FillHeight,
                                        contentDescription = "",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                stringResource(
                                    Res.string.connect_button_current_engine,
                                    when (player.value) {
                                        BasePlayer.ENGINE.ANDROID_EXOPLAYER.name -> "Google ExoPlayer (System)"
                                        BasePlayer.ENGINE.ANDROID_MPV.name -> "mpv (Default, Recommended)"
                                        BasePlayer.ENGINE.ANDROID_VLC.name -> "VLC (Experimental, Unstable)"
                                        BasePlayer.ENGINE.IOS_AVPLAYER.name -> "Apple AVPlayer (System)"
                                        BasePlayer.ENGINE.IOS_VLC.name -> "VLC (Experimental, Unstable)"
                                        else -> "Undefined"
                                    }
                                ), textAlign = TextAlign.Center, fontSize = 9.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        /* join button */
                        Button(
                            border = BorderStroke(
                                width = 1.dp, brush = Brush.sweepGradient(
                                    colors = Paletting.SP_GRADIENT,
                                    center = Offset.Unspecified // Will use center of the composable
                                )
                            ),
                            shape = RoundedCornerShape(20),
                            modifier = Modifier.height(56.dp).fillMaxWidth(0.92f),
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
                                        snacky.showSnackbar(getString(errorMessage))
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
                        ) {
                            Icon(imageVector = Icons.Filled.Api, "")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.connect_button_join), fontSize = 18.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        )
    }
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
                            tintColors = Paletting.SP_GRADIENT,
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
                            tintColors = Paletting.SP_GRADIENT,
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
                                    Paletting.SP_GRADIENT.first().copy(0.05f),
                                    Paletting.SP_GRADIENT.last().copy(0.05f)
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
                                    brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT),
                                    fontFamily = FontFamily(Font(Res.font.Directive4_Regular)),
                                    fontSize = 24.sp,
                                )
                            )
                        }
                    }
                },
            )/* Settings */

            androidx.compose.animation.AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = settingState.intValue != 0,
                enter = scaleIn(),
                exit = scaleOut()
            ) {
                SettingsUI.SettingsGrid(
                    modifier = Modifier.fillMaxWidth(),
                    settingcategories = LocalGlobalSettings.current,
                    state = settingState,
                    onCardClicked = {
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
                        .clip(RoundedCornerShape(8.dp)).clickable {
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