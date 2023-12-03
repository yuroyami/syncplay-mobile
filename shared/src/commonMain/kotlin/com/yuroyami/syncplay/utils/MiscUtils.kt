package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp


expect fun getPlatform(): String

expect fun loggy(s: String?)

expect fun getDefaultEngine(): String

expect fun changeLanguage(lang: String, context: Any?)

expect fun generateTimestampMillis(): Long

expect fun timeStamper(seconds: Long): String

expect fun getFileName(uri: String, context: Any? = null): String?

expect fun pingIcmp(host: String, packet: Int): Int?

data class ScreenSizeInfo(val hPX: Int, val wPX: Int, val hDP: Dp, val wDP: Dp)

@Composable
expect fun getScreenSizeInfo(): ScreenSizeInfo


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

//
///** Returns a hexadecimal color code from a preference ColorInt
// *
// * TODO: Use Datastore **/
//fun getColorCode(key: String, context: Context): String {
//    @ColorInt val color =
//        PreferenceManager.getDefaultSharedPreferences(context).getInt(key, Color.BLACK)
//    return String.format("#%06X", 0xFFFFFF and color)
//}

//
///** Syncplay uses SHA256 hex-digested to hash file names and sizes **/
//fun sha256(str: String): ByteArray =
//    MessageDigest.getInstance("SHA-256").digest(str.toByteArray(UTF_8))
//
///** Hides the keyboard and loses message typing focus **/
//fun ComponentActivity.hideKb() {
//    lifecycleScope.launch(Dispatchers.Main) {
//        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
//    }
//}