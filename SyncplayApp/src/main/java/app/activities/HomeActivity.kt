package app.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.R
import app.compose.PopupAPropos.AProposPopup
import app.datastore.DataStoreKeys.DATASTORE_GLOBAL_SETTINGS
import app.datastore.DataStoreKeys.DATASTORE_MISC_PREFS
import app.datastore.DataStoreKeys.MISC_JOIN_ROOMNAME
import app.datastore.DataStoreKeys.MISC_JOIN_SERVER_ADDRESS
import app.datastore.DataStoreKeys.MISC_JOIN_SERVER_PORT
import app.datastore.DataStoreKeys.MISC_JOIN_SERVER_PW
import app.datastore.DataStoreKeys.MISC_JOIN_USERNAME
import app.datastore.DataStoreKeys.MISC_NIGHTMODE
import app.datastore.DataStoreKeys.PREF_DISPLAY_LANG
import app.datastore.DataStoreKeys.PREF_REMEMBER_INFO
import app.datastore.DataStoreUtils.booleanFlow
import app.datastore.DataStoreUtils.ds
import app.datastore.DataStoreUtils.obtainBoolean
import app.datastore.DataStoreUtils.obtainInt
import app.datastore.DataStoreUtils.obtainString
import app.datastore.DataStoreUtils.writeInt
import app.datastore.DataStoreUtils.writeString
import app.datastore.MySettings.globalSettings
import app.settings.SettingsUI
import app.ui.AppTheme
import app.ui.Paletting
import app.utils.ComposeUtils.FlexibleFancyText
import app.utils.ComposeUtils.NightModeToggle
import app.utils.ComposeUtils.gradientOverlay
import app.utils.MiscUtils.changeLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class HomeActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() /* This will be called only on cold starts */

        /** Applying saved language */
        val lang = runBlocking { DATASTORE_GLOBAL_SETTINGS.obtainString(PREF_DISPLAY_LANG, "en") }
        changeLanguage(lang = lang, appCompatWay = false, recreateActivity = false, showToast = false)

        super.onCreate(savedInstanceState)

        /** Randomized names for new users */
        val randomUser = "user" + (0..9999).random().toString()
        val randomRoom = "room" + (0..9999).random().toString()

        /** Getting saved info. We use DataStore so we need to obtain Flow value with runBlocking */
        val savedUser = runBlocking { DATASTORE_MISC_PREFS.obtainString(MISC_JOIN_USERNAME, randomUser) }
        val savedRoom = runBlocking { DATASTORE_MISC_PREFS.obtainString(MISC_JOIN_ROOMNAME, randomRoom) }
        val savedIP = runBlocking { DATASTORE_MISC_PREFS.obtainString(MISC_JOIN_SERVER_ADDRESS, "syncplay.pl") }
        val savedPort = runBlocking { DATASTORE_MISC_PREFS.obtainInt(MISC_JOIN_SERVER_PORT, 8997) }
        val savedPassword = runBlocking { DATASTORE_MISC_PREFS.obtainString(MISC_JOIN_SERVER_PW, "") }

        /** Applying night mode from the system */
        val nightmodepref = runBlocking { DATASTORE_MISC_PREFS.obtainBoolean(MISC_NIGHTMODE, true) }
        if (nightmodepref) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        /****** Composing UI using Jetpack Compose *******/
        setContent {
            val nightMode = DATASTORE_MISC_PREFS.ds().booleanFlow(MISC_NIGHTMODE, true).collectAsState(initial = nightmodepref)

            AppTheme(!nightMode.value) {
                window.statusBarColor = MaterialTheme.colorScheme.tertiaryContainer.toArgb()
                window.navigationBarColor = MaterialTheme.colorScheme.background.toArgb()

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
                                .wrapContentHeight()
                                .background(color = Color.Transparent /* Paletting.BG_DARK_1 */),
                            shape = RoundedCornerShape(topEnd = 0.dp, topStart = 0.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
                        ) {
                            ConstraintLayout(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                val (settingsbutton, syncplay, nightmode, settings) = createRefs()

                                /** Settings Button */
                                val settingState = remember { mutableStateOf(0) }

                                IconButton(
                                    modifier = Modifier.constrainAs(settingsbutton) {
                                        top.linkTo(parent.top)
                                        end.linkTo(parent.end)
                                    },
                                    onClick = {
                                        when (settingState.value) {
                                            0 -> settingState.value = 1
                                            1 -> settingState.value = 0
                                            else -> settingState.value = 1
                                        }

                                    }) {
                                    Box {
                                        val vector = when (settingState.value) {
                                            0 -> Icons.Filled.Settings
                                            1 -> Icons.Filled.Close
                                            else -> Icons.Filled.Redo
                                        }

                                        Icon(
                                            imageVector = vector,
                                            contentDescription = "",
                                            modifier = Modifier.size(31.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = vector,
                                            contentDescription = "",
                                            modifier = Modifier
                                                .size(30.dp)
                                                .gradientOverlay(),
                                        )
                                    }
                                }

                                /** Syncplay Header (logo + text) */
                                Row(modifier = Modifier
                                    .wrapContentWidth()
                                    .clickable(
                                        enabled = true,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = rememberRipple(
                                            bounded = false,
                                            color = Color(100, 100, 100, 200)
                                        )
                                    ) { aboutpopupState.value = true }
                                    .constrainAs(syncplay) {
                                        top.linkTo(settingsbutton.top)
                                        bottom.linkTo(settingsbutton.bottom)
                                        start.linkTo(parent.start)
                                        end.linkTo(parent.end)
                                    }) {
                                    Image(
                                        painter = painterResource(R.drawable.syncplay_logo_gradient), contentDescription = "",
                                        modifier = Modifier
                                            .height(32.dp)
                                            .aspectRatio(1f)
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
                                                fontFamily = FontFamily(Font(R.font.directive4bold)),
                                                fontSize = 24.sp,
                                            )
                                        )
                                        Text(
                                            modifier = Modifier.wrapContentWidth(),
                                            text = "Syncplay",
                                            style = TextStyle(
                                                brush = Brush.linearGradient(
                                                    colors = Paletting.SP_GRADIENT
                                                ),
                                                fontFamily = FontFamily(Font(R.font.directive4bold)),
                                                fontSize = 24.sp,
                                            )
                                        )
                                    }
                                }

                                /* Day/Night toggle button */
                                NightModeToggle(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .constrainAs(nightmode) {
                                            top.linkTo(settingsbutton.top)
                                            bottom.linkTo(settingsbutton.bottom)
                                            start.linkTo(parent.start, (4.dp))
                                        },
                                    state = nightMode
                                )

                                /* Settings */
                                androidx.compose.animation.AnimatedVisibility(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .constrainAs(settings) {
                                            top.linkTo(syncplay.bottom, 12.dp)
                                        },
                                    visible = settingState.value != 0,
                                    enter = expandIn(),
                                    exit = shrinkOut()
                                ) {
                                    SettingsUI.SettingsGrid(
                                        modifier = Modifier.fillMaxWidth(),
                                        settingcategories = globalSettings(),
                                        state = settingState,
                                        onCardClicked = {
                                            settingState.value = 2
                                        }
                                    )
                                }
                            }
                        }
                    },

                    /** The actual content of the log-in screen */

                    /** The actual content of the log-in screen */
                    content = { paddingValues ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceAround
                        ) {
                            /** Instead of consuming paddingValues, we create a spacer with that height */
                            /** Instead of consuming paddingValues, we create a spacer with that height */
                            Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))

                            /** higher-level variables which are needed for logging in */
                            /** higher-level variables which are needed for logging in */
                            val textUsername = remember { mutableStateOf(savedUser) }
                            val textRoomname = remember { mutableStateOf(savedRoom) }

                            val serverIsPublic = remember { mutableStateOf(true) }
                            val servers = listOf(
                                "syncplay.pl:8995",
                                "syncplay.pl:8996",
                                "syncplay.pl:8997",
                                "syncplay.pl:8998",
                                "syncplay.pl:8999",
                                getString(R.string.connect_enter_custom_server)
                            )
                            val selectedServer = remember { mutableStateOf("$savedIP:$savedPort") }

                            val serverAddress = remember { mutableStateOf(savedIP) }
                            val serverPort = remember { mutableStateOf(savedPort.toString()) }
                            val serverPassword = remember { mutableStateOf(savedPassword) }

                            /** Username */

                            Column(
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FlexibleFancyText(
                                    text = stringResource(R.string.connect_username_a),
                                    size = 20f,
                                    fillingColors = listOf(MaterialTheme.colorScheme.primary),
                                    font = Font(R.font.directive4bold),
                                    shadowColors = listOf(Color.Gray)
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Box(contentAlignment = Alignment.Center) {
                                    OutlinedTextField(
                                        modifier = Modifier.focusable(false),
                                        colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
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
                                        label = { Text(stringResource(R.string.connect_username_b)) },
                                        leadingIcon = { Icon(imageVector = Icons.Filled.PersonPin, "") },
                                        supportingText = { /* Text(stringResource(R.string.connect_username_c), fontSize = 10.sp) */ },
                                        keyboardActions = KeyboardActions(onDone = {
                                            focusManager.moveFocus(focusDirection = FocusDirection.Next)
                                        }),
                                        value = textUsername.value,
                                        onValueChange = { s ->
                                            textUsername.value = s

                                        })
                                }
                            }

                            /** Roomname */
                            Column(
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FlexibleFancyText(
                                    text = stringResource(R.string.connect_roomname_a),
                                    size = 20f,
                                    fillingColors = listOf(MaterialTheme.colorScheme.primary),
                                    font = Font(R.font.directive4bold),
                                    shadowColors = listOf(Color.Gray)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Box {
                                    OutlinedTextField(
                                        modifier = Modifier.focusable(false),
                                        colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
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
                                        label = { Text(stringResource(R.string.connect_roomname_b)) },
                                        leadingIcon = { Icon(imageVector = Icons.Filled.MeetingRoom, "") },
                                        supportingText = { /* Text(stringResource(R.string.connect_roomname_c), fontSize = 10.sp) */ },
                                        keyboardActions = KeyboardActions(onDone = {
                                            focusManager.moveFocus(focusDirection = FocusDirection.Next)
                                        }),
                                        value = textRoomname.value,
                                        onValueChange = { s -> textRoomname.value = s })
                                }
                            }

                            /** Server */
                            val expanded = remember { mutableStateOf(false) }

                            Column(
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FlexibleFancyText(
                                    text = stringResource(R.string.connect_server_a),
                                    size = 20f,
                                    fillingColors = listOf(MaterialTheme.colorScheme.primary),
                                    font = Font(R.font.directive4bold),
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
                                            colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
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
                                            value = selectedServer.value,
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
                                                    selectedServer.value = server
                                                    expanded.value = false

                                                    if (server != servers[5]) {
                                                        serverAddress.value = "syncplay.pl"
                                                        serverPort.value = selectedServer.value.substringAfter("syncplay.pl:")
                                                        serverIsPublic.value = true
                                                        serverPassword.value = ""
                                                    } else {
                                                        serverIsPublic.value = false
                                                        serverAddress.value = ""
                                                        serverPort.value = ""
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    TextField(
                                        modifier = Modifier.fillMaxWidth(0.5f),
                                        shape = RoundedCornerShape(16.dp),
                                        singleLine = true,
                                        value = serverAddress.value,
                                        colors = TextFieldDefaults.textFieldColors(
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent,
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                        ),
                                        onValueChange = { serverAddress.value = it },
                                        keyboardActions = KeyboardActions(onDone = {
                                            focusManager.moveFocus(FocusDirection.Next)
                                        }),
                                        textStyle = TextStyle(
                                            brush = Brush.linearGradient(
                                                colors = Paletting.SP_GRADIENT
                                            ),
                                            fontFamily = FontFamily(Font(R.font.inter)),
                                            fontSize = 16.sp,
                                        ),
                                        label = {
                                            Text("IP Address", color = Color.Gray)
                                        })


                                    TextField(
                                        modifier = Modifier.fillMaxWidth(0.5f),
                                        shape = RoundedCornerShape(16.dp),
                                        singleLine = true,
                                        value = serverPort.value,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        keyboardActions = KeyboardActions(onDone = {
                                            focusManager.moveFocus(FocusDirection.Next)
                                        }),
                                        colors = TextFieldDefaults.textFieldColors(
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent,
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                        ),
                                        onValueChange = { serverPort.value = it },
                                        textStyle = TextStyle(
                                            brush = Brush.linearGradient(
                                                colors = Paletting.SP_GRADIENT
                                            ),
                                            fontFamily = FontFamily(Font(R.font.inter)),
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
                                    enabled = !serverIsPublic.value,
                                    value = serverPassword.value,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.moveFocus(FocusDirection.Next)
                                    }),
                                    colors = TextFieldDefaults.textFieldColors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    ),
                                    onValueChange = { serverPassword.value = it },
                                    textStyle = TextStyle(
                                        brush = Brush.linearGradient(
                                            colors = Paletting.SP_GRADIENT
                                        ),
                                        fontFamily = FontFamily(Font(R.font.inter)),
                                        fontSize = 16.sp,
                                    ),
                                    label = {
                                        Text("Password (empty if undefined)", color = Color.Gray)
                                    })
                            }


                            /** Join Button */


                            /** Join Button */
                            Button(
                                border = BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(0.7f),
                                onClick = {
                                    /** Trimming whitespaces */
                                    /** Trimming whitespaces */
                                    textUsername.value = textUsername.value.trim()
                                    textRoomname.value = textRoomname.value.trim()
                                    serverAddress.value = serverAddress.value.trim()
                                    serverPort.value = serverPort.value.trim()
                                    serverPassword.value = serverPassword.value.trim()

                                    /** Checking whether username is empty */

                                    /** Checking whether username is empty */
                                    if (textUsername.value.isBlank()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(resources.getString(R.string.connect_username_empty_error))
                                        }
                                        return@Button
                                    }

                                    /** Taking the first 150 letters of the username if it's too long */

                                    /** Taking the first 150 letters of the username if it's too long */
                                    textUsername.value.let {
                                        if (it.length > 150) textUsername.value = it.substring(0, 149)
                                    }

                                    /** Taking only 35 letters from the roomname if it's too long */

                                    /** Taking only 35 letters from the roomname if it's too long */
                                    textRoomname.value.let {
                                        if (it.length > 35) textRoomname.value = it.substring(0, 34)
                                    }

                                    /** Checking whether roomname is empty */

                                    /** Checking whether roomname is empty */
                                    if (textRoomname.value.isBlank()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(resources.getString(R.string.connect_roomname_empty_error))
                                        }
                                        return@Button
                                    }

                                    /** Checking whether address is empty */

                                    /** Checking whether address is empty */
                                    if (serverAddress.value.isBlank()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(resources.getString(R.string.connect_address_empty_error))
                                        }
                                        return@Button
                                    }

                                    /** Checking whether port is empty */

                                    /** Checking whether port is empty */
                                    if (serverPort.value.isBlank()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(resources.getString(R.string.connect_port_empty_error))
                                        }
                                        return@Button
                                    }

                                    /** Checking whether port is a number */

                                    /** Checking whether port is a number */
                                    if (serverPort.value.toIntOrNull() == null) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(resources.getString(R.string.connect_port_empty_error))
                                        }
                                        return@Button
                                    }


                                    join(
                                        textUsername.value.replace("\\", "").trim(),
                                        textRoomname.value.replace("\\", "").trim(),
                                        serverAddress.value,
                                        serverPort.value.toInt(),
                                        serverPassword.value
                                    )
                                },
                            ) {
                                Icon(imageVector = Icons.Filled.Api, "")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.connect_button), fontSize = 18.sp)
                            }
                        }
                    }
                )

                AProposPopup(aboutpopupState)
            }
        }
    }

    private fun join(username: String, roomname: String, address: String, port: Int, password: String) {
        /** Checking, through DataStore, whether we need to save the info or not */
        lifecycleScope.launch(Dispatchers.IO) {
            val saveInfo = DATASTORE_MISC_PREFS.obtainBoolean(PREF_REMEMBER_INFO, true)

            if (saveInfo) {
                DATASTORE_MISC_PREFS.ds().writeString(MISC_JOIN_USERNAME, username)
                DATASTORE_MISC_PREFS.ds().writeString(MISC_JOIN_ROOMNAME, roomname)
                DATASTORE_MISC_PREFS.ds().writeString(MISC_JOIN_SERVER_ADDRESS, address)
                DATASTORE_MISC_PREFS.ds().writeInt(MISC_JOIN_SERVER_PORT, port)
                DATASTORE_MISC_PREFS.ds().writeString(MISC_JOIN_SERVER_PW, password)
            }
        }

        val intent = Intent(this, WatchActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        intent.putExtra("INFO_USERNAME", username)
        intent.putExtra("INFO_ROOMNAME", roomname)
        intent.putExtra("INFO_ADDRESS", if (address == "syncplay.pl") "151.80.32.178" else address)
        intent.putExtra("INFO_PORT", port)
        intent.putExtra("INFO_PASSWORD", password)
        intent.putExtra("SOLO_MODE", false)

        startActivity(intent)
    }

    fun soloMode() {
        val intent = Intent(this, WatchActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        intent.putExtra("SOLO_MODE", true)

        startActivity(intent)
    }
}