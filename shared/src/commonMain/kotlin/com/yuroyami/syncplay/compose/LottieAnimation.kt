package com.yuroyami.syncplay.compose

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
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.writeValue
import io.github.alexzhirkevich.compottie.LottieAnimation
import io.github.alexzhirkevich.compottie.LottieClipSpec
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieAnimatable
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

//@Composable
//expect fun InfiniteLottieAnimation(
//    modifier: Modifier,
//    json: String,
//    speed: Float = 1f,
//    isplaying: Boolean = true,
//    reverseOnEnd: Boolean = false
//)

@Composable
fun NightModeToggler(modifier: Modifier, state: State<Boolean>) {
    val scope = rememberCoroutineScope { Dispatchers.IO }

    /* The lottie composition to play */
    var s by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        s = daynightAsset()
    }

    s?.let {
        val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(it))
        val anim = rememberLottieAnimatable() /* The animatable that accompanies the composition */

        val night2day = LottieClipSpec.Progress(0f, 0.4f)
        val day2night = LottieClipSpec.Progress(0.52f, 1f)

        var onInit by remember { mutableStateOf(false) }

        LaunchedEffect(state.value) {
            if (!onInit) {
                onInit = true
                anim.snapTo(composition = composition, progress = if (state.value) 0f else 0.42f)
            } else {
                /* Applying the corresponding animation */
                anim.animate(
                    composition = composition,
                    speed = 1.25f,
                    clipSpec = if (!state.value) night2day else day2night
                )
            }
        }

        IconButton(
            modifier = modifier,
            onClick = {
                scope.launch { writeValue(DataStoreKeys.MISC_NIGHTMODE, !state.value) }
            }) {
            LottieAnimation(
                composition = composition,
                progress = { anim.progress },
            )
        }
    }
}