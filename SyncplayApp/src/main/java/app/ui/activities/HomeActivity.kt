package app.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
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
import app.datastore.DataStoreKeys.DATASTORE_MISC_PREFS
import app.datastore.DataStoreKeys.MISC_JOIN_ROOMNAME
import app.datastore.DataStoreKeys.MISC_JOIN_SERVER_ADDRESS
import app.datastore.DataStoreKeys.MISC_JOIN_SERVER_PORT
import app.datastore.DataStoreKeys.MISC_JOIN_SERVER_PW
import app.datastore.DataStoreKeys.MISC_JOIN_USERNAME
import app.datastore.DataStoreKeys.PREF_REMEMBER_INFO
import app.datastore.DataStoreUtils.ds
import app.datastore.DataStoreUtils.obtainBoolean
import app.datastore.DataStoreUtils.obtainInt
import app.datastore.DataStoreUtils.obtainString
import app.datastore.DataStoreUtils.writeInt
import app.datastore.DataStoreUtils.writeString
import app.datastore.MySettings.globalSettings
import app.settings.SettingsUI
import app.ui.Paletting
import app.ui.compose.ComposeUtils.FancyText
import app.ui.compose.ComposeUtils.syncplayGradient
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class HomeActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() /* This will be called only on cold starts */
        super.onCreate(savedInstanceState)

        /* TODO: Check orientation change, and enable fullscreen mode only in landscape */

        /** Customizing our MaterialTheme */
        val fgColor = Color.DarkGray
        val bgColor = Color.Gray
        window.statusBarColor = fgColor.toArgb()
        window.navigationBarColor = Paletting.BG_DARK_1.toArgb()

        /** Randomized names for new users */
        val randomUser = "New_User_" + (0..9999).random().toString()
        val randomRoom = "New_Room_" + (0..9999).random().toString()

        /** Getting saved info. We use DataStore so we need to obtain Flow value with runBlocking */
        val savedUser = runBlocking { DATASTORE_MISC_PREFS.obtainString(MISC_JOIN_USERNAME, randomUser) }
        val savedRoom = runBlocking { DATASTORE_MISC_PREFS.obtainString(MISC_JOIN_ROOMNAME, randomRoom) }
        val savedIP = runBlocking { DATASTORE_MISC_PREFS.obtainString(MISC_JOIN_SERVER_ADDRESS, "syncplay.pl") }
        val savedPort = runBlocking { DATASTORE_MISC_PREFS.obtainInt(MISC_JOIN_SERVER_PORT, 8997) }
        val savedPassword = runBlocking { DATASTORE_MISC_PREFS.obtainString(MISC_JOIN_SERVER_PW, "") }

        /****** Composing UI using Jetpack Compose *******/
        setContent {
            /** Remembering stuff like scope for onClicks, snackBar host state for snackbars ... etc */
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }

            /** Using a Scaffold manages our top-level layout */
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },

                /** The top bar contains a syncplay logo, text, nightmode toggle button, and a setting button + its screen */
                topBar = {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .background(color = Color.Transparent /* Paletting.BG_DARK_1 */),
                        shape = RoundedCornerShape(topEnd = 0.dp, topStart = 0.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = fgColor),
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
                                        tint = bgColor
                                    )
                                    Icon(
                                        imageVector = vector,
                                        contentDescription = "",
                                        modifier = Modifier
                                            .size(30.dp)
                                            .syncplayGradient(),
                                    )
                                }
                            }

                            /** Syncplay Header (logo + text) */
                            Row(modifier = Modifier
                                .wrapContentWidth()
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

                            /** Day/Night toggle button */
                            val composition = rememberLottieComposition(LottieCompositionSpec.Asset("daynight_toggle.json"))
                            val clipSpec = remember { mutableStateOf(LottieClipSpec.Progress(0.49f, 0.49f)) }

                            val progress = animateLottieCompositionAsState(
                                clipSpec = clipSpec.value,
                                composition = composition.value,
                                isPlaying = true
                            )

                            IconButton(
                                modifier = Modifier
                                    .size(62.dp)
                                    .constrainAs(nightmode) {
                                        top.linkTo(settingsbutton.top)
                                        bottom.linkTo(settingsbutton.bottom)
                                        start.linkTo(parent.start, (4.dp))
                                    },
                                onClick = {
                                    if (clipSpec.value == LottieClipSpec.Progress(0.49f, 0.49f)) {
                                        clipSpec.value = LottieClipSpec.Progress(0f, 0.49f)
                                    } else {
                                        clipSpec.value = LottieClipSpec.Progress(0.49f, 1f)
                                    }
                                }) {
                                LottieAnimation(
                                    composition = composition.value,
                                    progress = { progress.progress },
                                )
                            }

                            /** Settings */
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
                content = { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .background(color = Paletting.BG_DARK_1),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceAround
                    ) {
                        /** Instead of consuming paddingValues, we create a spacer with that height */
                        Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))

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
                            FancyText(
                                string = stringResource(R.string.connect_username_a),
                                size = 20f,
                                solid = bgColor,
                                font = Font(R.font.directive4bold)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(contentAlignment = Alignment.Center) {
                                OutlinedTextField(
                                    colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = fgColor),
                                    singleLine = true,
                                    readOnly = true,
                                    value = "",
                                    label = { Text(" ") },
                                    supportingText = { Text("") },
                                    onValueChange = { s: String -> },
                                )

                                OutlinedTextField(
                                    modifier = Modifier.syncplayGradient(),
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.connect_username_b)) },
                                    leadingIcon = { Icon(imageVector = Icons.Filled.PersonPin, "") },
                                    supportingText = { Text(stringResource(R.string.connect_username_c), fontSize = 10.sp) },
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
                            FancyText(
                                string = stringResource(R.string.connect_roomname_a),
                                size = 20f,
                                solid = bgColor,
                                font = Font(R.font.directive4bold)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Box {
                                OutlinedTextField(
                                    colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = fgColor),
                                    singleLine = true,
                                    readOnly = true,
                                    value = "",
                                    label = { Text(" ") },
                                    supportingText = { Text("") },
                                    onValueChange = { s: String -> },
                                )

                                OutlinedTextField(
                                    modifier = Modifier.syncplayGradient(),
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.connect_roomname_b)) },
                                    leadingIcon = { Icon(imageVector = Icons.Filled.MeetingRoom, "") },
                                    supportingText = { Text(stringResource(R.string.connect_roomname_c), fontSize = 10.sp) },
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
                            FancyText(
                                string = stringResource(R.string.connect_server_a),
                                size = 20f,
                                solid = bgColor,
                                font = Font(R.font.directive4bold)
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
                                        colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = fgColor),
                                        singleLine = true,
                                        readOnly = true,
                                        value = "",
                                        supportingText = { Text("") },
                                        onValueChange = { s: String -> },
                                    )
                                    OutlinedTextField(
                                        modifier = Modifier
                                            .menuAnchor()
                                            .syncplayGradient(),
                                        singleLine = true,
                                        readOnly = true,
                                        value = selectedServer.value,
                                        supportingText = { Text(stringResource(R.string.connect_server_c), fontSize = 9.sp) },
                                        onValueChange = { s: String -> },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) }
                                    )
                                }
                                ExposedDropdownMenu(
                                    modifier = Modifier.background(color = fgColor),
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
                                    modifier = Modifier.fillMaxWidth(0.65f),
                                    singleLine = true,
                                    value = serverAddress.value,
                                    colors = TextFieldDefaults.textFieldColors(containerColor = Color.DarkGray),
                                    onValueChange = { serverAddress.value = it },
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
                                    modifier = Modifier.fillMaxWidth(0.8f),
                                    singleLine = true,
                                    value = serverPort.value,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = TextFieldDefaults.textFieldColors(containerColor = Color.DarkGray),
                                    onValueChange = { serverPort.value = it.toString() },
                                    textStyle = TextStyle(
                                        brush = Brush.linearGradient(
                                            colors = Paletting.SP_GRADIENT
                                        ),
                                        fontFamily = FontFamily(Font(R.font.inter)),
                                        fontSize = 16.sp,
                                    ),
                                    label = {
                                        Text("Port", color = Color.Gray)
                                    })
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            TextField(
                                modifier = Modifier.fillMaxWidth(0.8f),
                                singleLine = true,
                                enabled = !serverIsPublic.value,
                                value = serverPassword.value,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = TextFieldDefaults.textFieldColors(containerColor = Color.DarkGray),
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
                        Button(
                            border = BorderStroke(width = 2.dp, color = MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth(0.7f),
                            onClick = {
                                /** Trimming whitespaces */
                                textUsername.value = textUsername.value.trim()
                                textRoomname.value = textRoomname.value.trim()
                                serverAddress.value = serverAddress.value.trim()
                                serverPort.value = serverPort.value.trim()
                                serverPassword.value = serverPassword.value.trim()

                                /** Checking whether username is empty */
                                if (textUsername.value.isBlank()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(resources.getString(R.string.connect_username_empty_error))
                                    }
                                    return@Button
                                }

                                /** Taking the first 150 letters of the username if it's too long */
                                textUsername.value.let {
                                    if (it.length > 150) textUsername.value = it.substring(0, 149)
                                }

                                /** Taking only 35 letters from the roomname if it's too long */
                                textRoomname.value.let {
                                    if (it.length > 35) textRoomname.value = it.substring(0, 34)
                                }

                                /** Checking whether roomname is empty */
                                if (textRoomname.value.isBlank()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(resources.getString(R.string.connect_roomname_empty_error))
                                    }
                                    return@Button
                                }

                                /** Checking whether address is empty */
                                if (serverAddress.value.isBlank()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(resources.getString(R.string.connect_address_empty_error))
                                    }
                                    return@Button
                                }

                                /** Checking whether port is empty */
                                if (serverPort.value.isBlank()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(resources.getString(R.string.connect_port_empty_error))
                                    }
                                    return@Button
                                }

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
                            Box(modifier = Modifier.background(brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT)))
                            Icon(imageVector = Icons.Filled.Api, "")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.connect_button), fontSize = 18.sp)
                        }
                    }
                }
            )
        }
    }

    fun join(username: String, roomname: String, address: String, port: Int, password: String) {
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
        startActivity(intent)
    }
}