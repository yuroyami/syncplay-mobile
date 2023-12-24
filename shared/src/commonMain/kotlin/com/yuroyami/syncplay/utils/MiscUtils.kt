package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp


expect fun getPlatform(): String

expect fun loggy(s: String?, checkpoint: Int)

expect fun getDefaultEngine(): String

expect fun generateTimestampMillis(): Long

expect fun timeStamper(seconds: Long): String

expect fun getFileName(uri: String, context: Any? = null): String?

expect fun pingIcmp(host: String, packet: Int): Int?

data class ScreenSizeInfo(val hPX: Int, val wPX: Int, val hDP: Dp, val wDP: Dp)

@Composable
expect fun getScreenSizeInfo(): ScreenSizeInfo

expect fun String.format(vararg keys: String): String

expect fun getSystemLanguageCode(): String

//expect fun instantiateProtocol(): SyncplayProtocol

//@Suppress("DEPRECATION")
//fun ComponentActivity.showSystemUI(useDeprecated: Boolean) {
//    if (useDeprecated) {
//        window.decorView.systemUiVisibility =
//            (View.SYSTEM_UI_FLAG_VISIBLE or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
//        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
//        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
//    } else {
//        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
//    }
//
//}
//

///** Hides the keyboard and loses message typing focus **/
//fun ComponentActivity.hideKb() {
//    lifecycleScope.launch(Dispatchers.Main) {
//        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
//    }
//}