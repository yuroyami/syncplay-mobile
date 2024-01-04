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
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.writeBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Shows an infinitely repeating Bodymovin [json] animation with the specified [speed].
 *
 *  On the Android side, it uses Lottie-compose
 *  On the iOS side, it uses JetBrains Skiko (Skottie)
 */
//@Composable
//actual fun InfiniteLottieAnimation(
//    modifier: Modifier,
//    json: String,
//    speed: Float,
//    isplaying: Boolean,
//    reverseOnEnd: Boolean
//) {
//    val comp by rememberLottieComposition(spec = LottieCompositionSpec.JsonString(json))
//    LottieAnimation(
//        composition = comp,
//        isPlaying = isplaying,
//        restartOnPlay = true,
//        speed = speed,
//        iterations = Int.MAX_VALUE,
//        reverseOnRepeat = reverseOnEnd,
//        modifier = modifier
//    )
//}

/** Toggle button responsible for switching the day/night (dark/light) mode.
 *
 * @param nightModeState Defines the initial state of the button (you should pass it,
 * like you're telling the composable which mode is enabled initially).
 */
@Composable
actual fun NightModeToggle(modifier: Modifier, state: State<Boolean>) {
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
                scope.launch { writeBoolean(DataStoreKeys.MISC_NIGHTMODE, !state.value) }
            }) {
            LottieAnimation(
                composition = composition,
                progress = { anim.progress },
            )
        }
    }
}