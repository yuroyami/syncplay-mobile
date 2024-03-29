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
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.writeValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.jetbrains.skia.Rect
import org.jetbrains.skia.skottie.Animation
import org.jetbrains.skia.sksg.InvalidationController
import kotlin.math.roundToInt

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
                scope.launch { writeValue(DataStoreKeys.MISC_NIGHTMODE, !state.value) }
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