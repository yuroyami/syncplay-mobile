package com.yuroyami.syncplay.utils


expect fun getPlatform(): String

/** Language changer at runtime */
expect fun changeLanguage(lang: String, context: Any?)

expect fun generateTimestampMillis(): Long

expect fun timeStamper(seconds: Long): String

expect fun getFileName(uri: String, context: Any? = null): String?

expect fun pingIcmp(host: String, packet: Int): Int?

//val gb = 2L * 1024L * 1024L * 1024L
//
///** Helps determine the size of a file in bytes, needing only its Uri and a context */
//fun getRealSizeFromUri(context: Context, uri: Uri): Long? {
//    val df = DocumentFile.fromSingleUri(context, uri) ?: return null
//    return df.length()
//    /*
//    val size1 = df.length()
//    return if (size1 >= gb) {
//        var size = 0L //Size incremental variable
//        context.contentResolver.openInputStream(uri)?.use { inputStream ->
//            BufferedInputStream(inputStream).use { bufferedInputStream ->
//                val buffer = ByteArray(100 * 1024 * 1024) // 8 KB buffer size (can be adjusted as per your requirements)
//                var bytesRead: Int
//
//                while (bufferedInputStream.read(buffer).also { bytesRead = it } != -1) {
//                    size += bytesRead
//                }
//            }
//        }
//        size
//    } else size1 */
//}
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