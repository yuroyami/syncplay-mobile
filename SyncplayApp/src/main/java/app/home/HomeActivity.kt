package app.home

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.R
import app.home.MySettings.mySettings
import app.home.settings.SettingsUI
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

class HomeActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        /** This will be called only on cold starts */
        super.onCreate(savedInstanceState)

        /** Customizing our MaterialTheme */
        window.statusBarColor = Color.Gray.toArgb()

        /****** Composing UI using Jetpack Compose *******/
        /** Decoding is heavy, we do it once and pass it to composables */
        val logo = BitmapFactory.decodeResource(resources, R.drawable.syncplay).asImageBitmap()

        setContent {
            /** Remembering stuff like scope for onClicks, snackBar host state for snackbars ... etc */
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }

            /** Using a Scaffold manages our top-level layout */
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .background(color = Color.Transparent /* Paletting.BG_DARK_1 */),
                        shape = RoundedCornerShape(topEnd = 0.dp, topStart = 0.dp, bottomEnd = 12.dp, bottomStart = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Gray),
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
                                        tint = Color.DarkGray
                                    )
                                    Icon(
                                        imageVector = vector,
                                        contentDescription = "",
                                        modifier = Modifier
                                            .size(30.dp)
                                            .graphicsLayer(alpha = 0.99f)
                                            .drawWithCache {
                                                onDrawWithContent {
                                                    drawContent()
                                                    drawRect(
                                                        brush = Brush.linearGradient(
                                                            colors = Paletting.SP_GRADIENT
                                                        ), blendMode = BlendMode.SrcAtop
                                                    )
                                                }
                                            },
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
                                    bitmap = logo, contentDescription = "",
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
                                    settingcategories = mySettings(),
                                    state = settingState,
                                    onCardClicked = {
                                        settingState.value = 2
                                    }
                                )
                            }
                        }
                    }
                },
                content = { paddingValues ->
                    paddingValues
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .background(brush = Brush.linearGradient(colors = Paletting.BG_Gradient_DARK))
                        //.padding(paddingValues = paddingValues) */
                        // .consumedWindowInsets(it))
                    ) {
                        Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
                    }
                }
            )

        }
    }
}