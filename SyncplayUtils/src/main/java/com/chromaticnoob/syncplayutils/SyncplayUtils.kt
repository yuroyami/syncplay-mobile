package com.chromaticnoob.syncplayutils

import android.annotation.TargetApi
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.ColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import java.io.*
import java.sql.Timestamp


object SyncplayUtils {

    /** Usually, the following set of operations were pretty hard to refine to the best shape possible
     * In short, this class represents a bunch of functions that do a certain job, and their code is at
     * its best shape and state, which means, it's maintainable, clean, and requires no longer tweaking.
     * Why ? because they do what they're supposed to do. There is no absolute need to include 'em directly
     * in my activity classes. So if a function is fully working and can be separated from the main classes,
     * I just put it here. This is like a small codex for functions often hard to find on the internet. *
     */

    /** This function helps convert Uris to Paths. Not yet used.*/
    @JvmStatic
    fun alternativeUriToPath(context: Context, uri: Uri): String? {
        val contentResolver = context.contentResolver
        // Create file path inside app's data dir
        val filePath = "${context.applicationInfo.dataDir}${File.separator}temp_file"
        val file = File(filePath)
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val outputStream: OutputStream = FileOutputStream(file)
            val buf = ByteArray(1024)
            var len: Int
            while (inputStream.read(buf).also { len = it } > 0) outputStream.write(buf, 0, len)
            outputStream.close()
            inputStream.close()
        } catch (ignore: IOException) {
            return null
        }
        return file.absolutePath
    }

    /* Helps determine the size of a file, needing only its Uri and a context */
    @JvmStatic
    fun getRealSizeFromUri(context: Context, uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            val proj = arrayOf(MediaStore.Video.Media.SIZE)
            cursor = context.contentResolver.query(uri, proj, null, null, null)
            val columnindex: Int = cursor?.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)!!
            cursor.moveToFirst()
            cursor.getString(columnindex)
        } finally {
            cursor?.close()
        }
    }

    /** This function is used to print/format the timestamp used in determining video values
     * @param seconds Unix epoch timestamp in seconds
     ***/
    @JvmStatic
    fun timeStamper(seconds: Int): String {
        return if (seconds < 3600) {
            String.format("%02d:%02d", (seconds / 60) % 60, seconds % 60)
        } else {
            String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
        }
    }

    /** This is used to generate chat messages' timestamp ready-for-use strings **/
    @JvmStatic
    fun generateTimestamp(): String {
        var s = Timestamp(System.currentTimeMillis()).toString().trim()
        s = s.removeRange(19 until s.length).removeRange(0..10)
        return s
    }

    /**          Helps get the file name from an Uri, it's very useful when we're using              *
     * the intent ACTION_GET_CONTENT when picking a file. Usually it returns meaningless identifiers *
     *        such as "msf:6057", the following two functions help get the proper file name          */
    @JvmStatic
    fun Context.getFileName(uri: Uri): String? = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> getContentFileName(uri)
        else -> uri.path?.let(::File)?.name
    }

    @JvmStatic
    private fun Context.getContentFileName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            return@use cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                .let(cursor::getString)
        }
    }.getOrNull()


    /* This one converts screen resolution units. DP to PX (Pixel), not used yet */
    @JvmStatic
    fun convertUnit(dp: Float, contexT: Context?): Float {
        val resources = contexT?.resources
        val metrics = resources?.displayMetrics
        return dp * (metrics?.densityDpi?.toFloat()?.div(DisplayMetrics.DENSITY_DEFAULT)!!)
    }

    /** This function is used to calculate the ICMP Ping to a certain server or end host **/
    @JvmStatic
    fun pingIcmp(host: String, packet: Int): Double {
        var result = 0.13
        try {
            val pingprocess: Process? =
                Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 1 -s $packet $host")
            //Reading the Output with BufferedReader
            val bufferedReader = BufferedReader(InputStreamReader(pingprocess?.inputStream))
            //Parsing the result in a string variable.
            val logger: StringBuilder = StringBuilder()
            var line: String? = ""
            while (line != null) {
                line = bufferedReader.readLine()
                logger.append(line + "\n")
            }
            val pingoutput = logger.toString()

            //Now reading what we have in pingResult and storing it as an Int value.
            result = when {
                pingoutput.contains("100% packet loss") -> {
                    0.2
                }
                else -> {
                    ((pingoutput.substringAfter("time=").substringBefore(" ms").trim()
                        .toDouble()) / 1000.0)
                }
            }
        } catch (e: IOException) {

        }
        return result
    }

    /** Basically a convenience log function that will log stuff to error pool if it's a debug build **/
    @JvmStatic
    fun loggy(string: String) {
        if (BuildConfig.DEBUG) {
            Log.e("STUFF", string)
        }
    }


    /** Completely revised and working versions for "System UI" manipulators. **/
    @Suppress("DEPRECATION") //We know they're deprecated. Yet they work better than stupid modern functions.
    @JvmStatic
    fun hideSystemUI(activity: Activity, newTrick: Boolean) {
        val window = activity.window
        activity.runOnUiThread {
            if (newTrick) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                }
            } else {
                val decorView: View = window.decorView
                val uiOptions = decorView.systemUiVisibility
                var newUiOptions = uiOptions
                newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_LOW_PROFILE
                newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_FULLSCREEN
                newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE
                newUiOptions = newUiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                decorView.systemUiVisibility = newUiOptions
                View.OnSystemUiVisibilityChangeListener { newmode ->
                    if (newmode != newUiOptions) {
                        hideSystemUI(activity, false)
                    }
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

            }
        }
    }

    @Suppress("DEPRECATION")
    @JvmStatic
    fun showSystemUI(window: Window) {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_VISIBLE)
    }


    /** A function to control cutout mode **/
    @TargetApi(Build.VERSION_CODES.P)
    @JvmStatic
    fun cutoutMode(enable: Boolean, window: Window) {
        if (enable) {
            window.attributes?.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            window.attributes?.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }
    }

    /** Returns a hexadecimal color code from a preference ColorInt **/
    @JvmStatic
    fun getColorCode(key: String, context: Context): String {
        @ColorInt val color =
            PreferenceManager.getDefaultSharedPreferences(context).getInt(key, Color.BLACK)
        return String.format("#%06X", 0xFFFFFF and color)
    }

    /** This basically just changes the Status Bar color since android:statusBarColor isn't working
     * in themes.xml
     */
    @JvmStatic
    fun setStatusBarColor(@ColorInt color: Int, window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = color

    }

}