package com.yuroyami.syncplay.home

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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuroyami.syncplay.MR
import com.yuroyami.syncplay.compose.ComposeUtils.FlexibleFancyText
import com.yuroyami.syncplay.compose.ComposeUtils.gradientOverlay
import com.yuroyami.syncplay.compose.PopupAPropos.AProposPopup
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_MISC_PREFS
import com.yuroyami.syncplay.datastore.DataStoreKeys.MISC_NIGHTMODE
import com.yuroyami.syncplay.datastore.DataStoreKeys.MISC_PLAYER_ENGINE
import com.yuroyami.syncplay.datastore.MySettings.globalSettings
import com.yuroyami.syncplay.datastore.booleanFlow
import com.yuroyami.syncplay.datastore.ds
import com.yuroyami.syncplay.datastore.stringFlow
import com.yuroyami.syncplay.datastore.writeString
import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.settings.SettingsUI
import com.yuroyami.syncplay.ui.AppTheme
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.utils.getDefaultEngine
import com.yuroyami.syncplay.utils.joinCallback
import dev.icerock.moko.resources.compose.asFont
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/** This is what previously used to be HomeActivity before we migrated towards KMM.*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(config: HomeConfig) {
    val nightMode = DATASTORE_MISC_PREFS.ds().booleanFlow(MISC_NIGHTMODE, true).collectAsState(initial = true)

    val directive = MR.fonts.Directive4.regular.asFont()!!
    val inter = MR.fonts.Inter.regular.asFont()!!

    val savedConfig = remember { config }

    val servers = listOf(
        "syncplay.pl:8995",
        "syncplay.pl:8996",
        "syncplay.pl:8997",
        "syncplay.pl:8998",
        "syncplay.pl:8999",
        stringResource(MR.strings.connect_enter_custom_server)
    )

    AppTheme(nightMode.value) {
        /* Remembering stuff like scope for onClicks, snackBar host state for snackbars ... etc */
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val focusManager = LocalFocusManager.current

        val aboutpopupState = remember { mutableStateOf(false) }

        /* Using a Scaffold manages our top-level layout */
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },

            /* The top bar contains a syncplay logo, text, nightmode toggle button, and a setting button + its screen */
            topBar = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        //.wrapContentHeight()
                        .background(color = Color.Transparent /* Paletting.BG_DARK_1 */),
                    shape = RoundedCornerShape(topEnd = 0.dp, topStart = 0.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
                ) {
                    /* Settings Button */
                    val settingState = remember { mutableIntStateOf(0) }

                    Column(horizontalAlignment = CenterHorizontally) {
                        ListItem(
                            modifier = Modifier.fillMaxWidth()
                                .padding(bottom = 12.dp, top = (TopAppBarDefaults.windowInsets.asPaddingValues().calculateTopPadding() + 12.dp)),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            trailingContent = {
                                Row {
                                    /** FIXME: NightModeToggle(
                                    modifier = Modifier
                                    .size(62.dp)
                                    .constrainAs(nightmode) {
                                    top.linkTo(settingsbutton.top)
                                    bottom.linkTo(settingsbutton.bottom)
                                    start.linkTo(parent.start, (4.dp))
                                    },
                                    state = nightMode
                                    )
                                     */
                                    IconButton(
                                        onClick = {
                                            when (settingState.intValue) {
                                                0 -> settingState.intValue = 1
                                                1 -> settingState.intValue = 0
                                                else -> settingState.intValue = 1
                                            }

                                        }) {
                                        Box {

                                            Icon(
                                                imageVector = when (settingState.intValue) {
                                                    0 -> Icons.Filled.Settings
                                                    1 -> Icons.Filled.Close
                                                    else -> Icons.Filled.Redo
                                                },
                                                contentDescription = "",
                                                modifier = Modifier.size(31.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Icon(
                                                imageVector = when (settingState.intValue) {
                                                    0 -> Icons.Filled.Settings
                                                    1 -> Icons.Filled.Close
                                                    else -> Icons.Filled.Redo
                                                },
                                                contentDescription = "",
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .gradientOverlay(),
                                            )
                                        }
                                    }
                                }
                            },

                            /* Syncplay Header (logo + text) */
                            headlineContent = {
                                Row(modifier = Modifier.clickable(
                                    enabled = true,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(
                                        bounded = false,
                                        color = Color(100, 100, 100, 200)
                                    )
                                ) { aboutpopupState.value = true }
                                ) {
                                    Image(
                                        painter = painterResource(MR.images.syncplay_logo_gradient), contentDescription = "",
                                        modifier = Modifier
                                            .height(32.dp)
                                            .aspectRatio(1f)
                                            .gradientOverlay()
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
                                                fontFamily = FontFamily(directive),
                                                fontSize = 24.sp,
                                            )
                                        )

                                        Text(
                                            text = "Syncplay",
                                            style = TextStyle(
                                                brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT),
                                                fontFamily = FontFamily(directive),
                                                fontSize = 24.sp,
                                            )
                                        )
                                    }
                                }
                            },
                        )
                        /* Settings */

                        androidx.compose.animation.AnimatedVisibility(
                            modifier = Modifier
                                .fillMaxWidth(),
                            visible = settingState.intValue != 0,
                            enter = scaleIn(),
                            exit = scaleOut()
                        ) {
                            SettingsUI.SettingsGrid(
                                modifier = Modifier.fillMaxWidth(),
                                settingcategories = globalSettings(), //remember { testSettings()globalSettings() },
                                state = settingState,
                                onCardClicked = {
                                    settingState.intValue = 2
                                }
                            )
                        }
                    }
                }
            },

            /* The actual content of the log-in screen */
            content = { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(BottomAppBarDefaults.windowInsets)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    /* Instead of consuming paddingValues, we create a spacer with that height */
                    Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))

                    /* higher-level variables which are needed for logging in */
                    var textUsername by remember { mutableStateOf(savedConfig.savedUser) }
                    var textRoomname by remember { mutableStateOf(savedConfig.savedRoom) }

                    var serverIsPublic by remember { mutableStateOf(true) }

                    var selectedServer by remember { mutableStateOf("${savedConfig.savedIP}:${savedConfig.savedPort}") }

                    var serverAddress by remember { mutableStateOf(savedConfig.savedIP) }
                    var serverPort by remember { mutableStateOf(savedConfig.savedPort.toString()) }
                    var serverPassword by remember { mutableStateOf(savedConfig.savedPassword) }

                    /* Username */
                    Column(
                        modifier = Modifier.wrapContentHeight().fillMaxWidth(),
                        horizontalAlignment = CenterHorizontally,
                    ) {

                        FlexibleFancyText(
                            text = stringResource(MR.strings.connect_username_a),
                            size = 20f,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = directive,
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
                                label = { Text(stringResource(MR.strings.connect_username_b)) },
                                leadingIcon = { Icon(imageVector = Icons.Filled.PersonPin, "") },
                                supportingText = { /* Text(stringResource(R.string.connect_username_c), fontSize = 10.sp) */ },
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.moveFocus(focusDirection = FocusDirection.Next)
                                }),
                                value = textUsername,
                                onValueChange = { s ->
                                    textUsername = s

                                })
                        }
                    }

                    /* Roomname */
                    Column(
                        modifier = Modifier
                            .wrapContentHeight()
                            .fillMaxWidth(),
                        horizontalAlignment = CenterHorizontally,
                    ) {


                        FlexibleFancyText(
                            text = stringResource(MR.strings.connect_roomname_a),
                            size = 20f,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = directive,
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
                                label = { Text(stringResource(MR.strings.connect_roomname_b)) },
                                leadingIcon = { Icon(imageVector = Icons.Filled.MeetingRoom, "") },
                                supportingText = { /* Text(stringResource(R.string.connect_roomname_c), fontSize = 10.sp) */ },
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.moveFocus(focusDirection = FocusDirection.Next)
                                }),
                                value = textRoomname,
                                onValueChange = { s -> textRoomname = s })
                        }
                    }

                    /* Server */
                    val expanded = remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .wrapContentHeight()
                            .fillMaxWidth(),
                        horizontalAlignment = CenterHorizontally,
                    ) {
                        FlexibleFancyText(
                            text = stringResource(MR.strings.connect_server_a),
                            size = 20f,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = directive,
                            shadowColors = listOf(Color.Gray)
                        )

                        Spacer(modifier = Modifier.height(10.dp))



                        ExposedDropdownMenuBox(
                            expanded = expanded.value,
                            onExpandedChange = {
                                expanded.value = !expanded.value
                            }
                        ) {
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
                                    modifier = Modifier
                                        .menuAnchor()
                                        .gradientOverlay(),
                                    singleLine = true,
                                    readOnly = true,
                                    value = selectedServer,
                                    supportingText = { /* Text(stringResource(R.string.connect_server_c), fontSize = 9.sp) */ },
                                    onValueChange = { },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) }
                                )
                            }
                            ExposedDropdownMenu(
                                modifier = Modifier.background(color = MaterialTheme.colorScheme.tertiaryContainer),
                                expanded = expanded.value,
                                onDismissRequest = {
                                    expanded.value = false
                                }
                            ) {
                                servers.forEach { server ->
                                    DropdownMenuItem(
                                        text = { Text(server, color = Color.White) },
                                        onClick = {
                                            selectedServer = server
                                            expanded.value = false

                                            if (server != servers[5]) {
                                                serverAddress = "syncplay.pl"
                                                serverPort = selectedServer.substringAfter("syncplay.pl:")
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

                        Spacer(modifier = Modifier.height(12.dp))

                        if (!serverIsPublic) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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
                                    onValueChange = { serverAddress = it },
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.moveFocus(FocusDirection.Next)
                                    }),
                                    textStyle = TextStyle(
                                        brush = Brush.linearGradient(
                                            colors = Paletting.SP_GRADIENT
                                        ),
                                        fontFamily = FontFamily(inter),
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
                                    onValueChange = { serverPort = it },
                                    textStyle = TextStyle(
                                        brush = Brush.linearGradient(
                                            colors = Paletting.SP_GRADIENT
                                        ),
                                        fontFamily = FontFamily(inter),
                                        fontSize = 16.sp,
                                    ),
                                    label = {
                                        Text("Port", color = Color.Gray)
                                    }
                                )
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
                                    fontFamily = FontFamily(inter),
                                    fontSize = 16.sp,
                                ),
                                label = {
                                    Text("Password (empty if undefined)", color = Color.Gray)
                                })
                        }
                    }

                    /* Buttons */
                    val defaultEngine = remember { getDefaultEngine() }
                    val player = DATASTORE_MISC_PREFS.ds().stringFlow(MISC_PLAYER_ENGINE, defaultEngine).collectAsState(initial = defaultEngine)

                    Column(horizontalAlignment = CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            /* shortcut button */
                            Button(
                                border = BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.primary),
                                shape = CircleShape,
                                modifier = Modifier.padding(2.dp).size(64.dp),
                                onClick = {
                                    joinCallback?.onSaveConfigShortcut(
                                        JoinInfo(
                                            textUsername.replace("\\", "").trim(),
                                            textRoomname.replace("\\", "").trim(),
                                            serverAddress,
                                            serverPort.toInt(),
                                            serverPassword
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BookmarkAdd, "",
                                    modifier = Modifier.size(62.dp).padding(2.dp)
                                )
                            }

                            Button(
                                border = BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.padding(2.dp).size(64.dp),
                                shape = CircleShape,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        if (defaultEngine != "exo") {
                                            DATASTORE_MISC_PREFS.ds().writeString(
                                                MISC_PLAYER_ENGINE,
                                                when (player.value) {
                                                    "exo" -> "mpv"
                                                    "mpv" -> "exo"
                                                    else -> "avplayer"
                                                }
                                            )
                                        }
                                    }
                                }
                            ) {
                                Image(
                                    painter = painterResource(
                                        when (player.value) {
                                            "exo" -> MR.images.exoplayer
                                            "mpv" -> MR.images.mpv
                                            "avplayer" -> MR.images.swift
                                            else -> MR.images.exoplayer
                                        }
                                    ),
                                    contentDescription = "",
                                    modifier = Modifier.size(62.dp).padding(2.dp)
                                )
                            }
                        }

                        Text(
                            stringResource(
                                MR.strings.connect_button_current_engine,
                                when (player.value) {
                                    "exo" -> "ExoPlayer"
                                    "mpv" -> "mpv"
                                    "avplayer" -> "Apple AVPlayer"
                                    else -> MR.images.exoplayer
                                }
                            ),
                            fontSize = 9.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    /* join button */
                    val snacktxtEmptyUSER = stringResource(MR.strings.connect_username_empty_error)
                    val snacktxtEmptyROOM = stringResource(MR.strings.connect_roomname_empty_error)
                    val snacktxtEmptyIP = stringResource(MR.strings.connect_address_empty_error)
                    val snacktxtEmptyPORT = stringResource(MR.strings.connect_port_empty_error)

                    Button(
                        border = BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(0.8f),
                        onClick = {
                            /* Trimming whitespaces */
                            textUsername = textUsername.trim()
                            textRoomname = textRoomname.trim()
                            serverAddress = serverAddress.trim()
                            serverPort = serverPort.trim()
                            serverPassword = serverPassword.trim()

                            /* Checking whether username is empty */
                            if (textUsername.isBlank()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(snacktxtEmptyUSER)
                                }
                                return@Button
                            }

                            /* Taking the first 150 letters of the username if it's too long */
                            textUsername.let {
                                if (it.length > 150) textUsername = it.substring(0, 149)
                            }

                            /* Taking only 35 letters from the roomname if it's too long */
                            textRoomname.let {
                                if (it.length > 35) textRoomname = it.substring(0, 34)
                            }

                            /* Checking whether roomname is empty */
                            if (textRoomname.isBlank()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(snacktxtEmptyROOM)
                                }
                                return@Button
                            }

                            /* Checking whether address is empty */
                            if (serverAddress.isBlank()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(snacktxtEmptyIP)
                                }
                                return@Button
                            }

                            /* Checking whether port is empty */
                            if (serverPort.isBlank()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(snacktxtEmptyPORT)
                                }
                                return@Button
                            }

                            /* Checking whether port is a number */
                            if (serverPort.toIntOrNull() == null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(snacktxtEmptyPORT)
                                }
                                return@Button
                            }

                            val info = JoinInfo(
                                textUsername.replace("\\", "").trim(),
                                textRoomname.replace("\\", "").trim(),
                                serverAddress,
                                serverPort.toInt(),
                                serverPassword
                            )

                            joinCallback?.onJoin(info)
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Api, "")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(MR.strings.connect_button_join), fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        )

        AProposPopup(aboutpopupState)
    }
}