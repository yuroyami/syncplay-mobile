package com.yuroyami.syncplay.utils


expect fun getPlatform(): String

/** Language changer at runtime */
expect fun changeLanguage(lang: String, context: Any?)

expect fun generateTimestampMillis(): Long

expect fun timeStamper(seconds: Long): String

///** This function is used to print/format the timestamp used in determining video values
// * @param seconds Unix epoch timestamp in seconds
// ***/
//fun timeStamper(seconds: Long): String {
//    return if (seconds < 3600) {
//        String.format("%02d:%02d", (seconds / 60) % 60, seconds % 60)
//    } else {
//        String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
//    }
//}
//
///** This is used to generate chat messages' timestamp ready-for-use strings **/
//fun generateTimestamp(): String {
//    var s = Timestamp(System.currentTimeMillis()).toString().trim()
//    s = s.removeRange(19 until s.length).removeRange(0..10)
//    return s
//}
//
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
//
//fun Context.getFileName(uri: Uri): String? = when (uri.scheme) {
//    ContentResolver.SCHEME_CONTENT -> getContentFileName(uri)
//    else -> uri.path?.let(::File)?.name
//}
//
//private fun Context.getContentFileName(uri: Uri): String? = runCatching {
//    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
//        cursor.moveToFirst()
//        return@use cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
//            .let(cursor::getString)
//    }
//}.getOrNull()
//
///** This function is used to calculate the ICMP Ping to a certain server or end host.
// * This is a long blocking call, therefore it should be executed on a background IO thread. **/
//fun pingIcmp(host: String, packet: Int): Double {
//    var result = 0.13
//    try {
//        val pingprocess: Process? =
//            Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 1 -s $packet $host")
//        //Reading the Output with BufferedReader
//        val bufferedReader = BufferedReader(InputStreamReader(pingprocess?.inputStream))
//        //Parsing the result in a string variable.
//        val logger: StringBuilder = StringBuilder()
//        var line: String? = ""
//        while (line != null) {
//            line = bufferedReader.readLine()
//            logger.append(line + "\n")
//        }
//        val pingoutput = logger.toString()
//
//        //Now reading what we have in pingResult and storing it as an Int value.
//        result = when {
//            pingoutput.contains("100% packet loss") -> {
//                0.2
//            }
//
//            else -> {
//                ((pingoutput.substringAfter("time=").substringBefore(" ms").trim()
//                    .toDouble()) / 1000.0)
//            }
//        }
//    } catch (e: IOException) {
//        e.printStackTrace()
//    }
//    return result
//}
//
///** Convenience log function **/
//fun loggy(string: String) {
//    Log.e("Syncplay", string)
//}
//

//
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
///** This basically just changes the Status Bar color */
//fun setStatusBarColor(@ColorInt color: Int, window: Window) {
//    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
//    window.statusBarColor = color
//}
//

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
//
///** Convenience method to save code space */
//fun Context.toasty(string: String) {
//    Handler(Looper.getMainLooper()).post {
//        Toast.makeText(this@toasty, string, Toast.LENGTH_SHORT).show()
//    }
//}