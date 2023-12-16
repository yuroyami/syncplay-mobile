package com.yuroyami.syncplay.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.writeBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.skia.Rect
import org.jetbrains.skia.skottie.Animation
import org.jetbrains.skia.sksg.InvalidationController
import kotlin.math.roundToInt

//@Composable
//actual fun InfiniteLottieAnimation(
//    modifier: Modifier,
//    json: String,
//    speed: Float,
//    isplaying: Boolean,
//    reverseOnEnd: Boolean,
//) {
//    if (json.isNotEmpty()) {
//        val animation = Animation.makeFromString(json)
//
//        val infiniteTransition = rememberInfiniteTransition()
//
//        val time by infiniteTransition.animateFloat(
//            initialValue = 0f,
//            targetValue = animation.duration,
//            animationSpec = infiniteRepeatable(
//                animation = tween((animation.duration * 1000).roundToInt(), easing = LinearEasing),
//                repeatMode = if (reverseOnEnd) RepeatMode.Reverse else RepeatMode.Restart
//            )
//        )
//
//        val invalidationController = remember { InvalidationController() }
//        Canvas(modifier) {
//            drawIntoCanvas {
//                if (isplaying) {
//                    animation.seekFrameTime(time, invalidationController)
//                } else {
//                    animation.seek(0f, invalidationController)
//                }
//
//                animation.render(
//                    canvas = it.nativeCanvas,
//                    dst = Rect.makeWH(size.width, size.height)
//                )
//            }
//        }
//    }
//}

@Composable
actual fun NightModeToggle(modifier: Modifier, state: State<Boolean>) {
    val scope = rememberCoroutineScope { Dispatchers.IO }

    /* The lottie composition to play */
    var s by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        s = daynightAsset()
    }

    s?.let {
        val animation = Animation.makeFromString(it)

        val animatable = Animatable(initialValue = if (!state.value) 0f else 0.52f)

        LaunchedEffect(state.value) {
            /* Applying the corresponding animation */
            animatable.snapTo(targetValue = if (!state.value) 0f else 0.52f)
            animatable.animateTo(
                targetValue = if (!state.value) 0.42f else 1F,
                animationSpec = tween(
                    durationMillis = (animation.duration * 500).roundToInt(),
                    easing = LinearEasing
                )
            )
        }

        val invalidationController = remember { InvalidationController() }
        IconButton(
            modifier = modifier,
            onClick = {
                scope.launch(Dispatchers.IO) {
                    DataStoreKeys.DATASTORE_MISC_PREFS.writeBoolean(DataStoreKeys.MISC_NIGHTMODE, !state.value)
                }
            }) {
            Canvas(modifier) {
                drawIntoCanvas { canvas ->
                    animation.seek(animatable.value, invalidationController)

                    animation.render(
                        canvas = canvas.nativeCanvas,
                        dst = Rect.makeWH(size.width, size.height)
                    )
                }
            }
        }
    }
}