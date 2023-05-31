package app.utils

import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.sql.Timestamp
import java.util.Locale


object MiscUtils {

    /** Language changer at runtime */
    fun ComponentActivity.changeLanguage(lang: String, appCompatWay: Boolean, recreateActivity: Boolean, showToast: Boolean) {
        if (appCompatWay) {
            val localesList: LocaleListCompat = LocaleListCompat.forLanguageTags(lang)
            AppCompatDelegate.setApplicationLocales(localesList)
        } else {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration()
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        }

        if (recreateActivity) recreate();

        if (showToast) Toast.makeText(this, String.format(resources.getString(R.string.setting_display_language_toast), ""), Toast.LENGTH_SHORT).show()
    }

    /** Functions to grab a localized string from resources, format it according to arguments **/
    fun Context.string(id: Int, vararg stuff: String): String {
        return String.format(resources.getString(id), *stuff)
    }

    /** This function is used to print/format the timestamp used in determining video values
     * @param seconds Unix epoch timestamp in seconds
     ***/
    fun timeStamper(seconds: Long): String {
        return if (seconds < 3600) {
            String.format("%02d:%02d", (seconds / 60) % 60, seconds % 60)
        } else {
            String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
        }
    }

    /** This is used to generate chat messages' timestamp ready-for-use strings **/
    fun generateTimestamp(): String {
        var s = Timestamp(System.currentTimeMillis()).toString().trim()
        s = s.removeRange(19 until s.length).removeRange(0..10)
        return s
    }

    /** Method responsible for getting the real file name on ALL Android APIs.
     * This is especially useful when you're using Intent.ACTION_OPEN_DOCUMENT
     * or Intent.ACTION_GET_CONTENT.
     *
     * Usually, they return useless file indexers (such as msf:4285) but with the help of a context
     * we can get the real file name using this.
     */

    /** Helps determine the size of a file in bytes, needing only its Uri and a context */
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

    fun Context.getFileName(uri: Uri): String? = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> getContentFileName(uri)
        else -> uri.path?.let(::File)?.name
    }

    private fun Context.getContentFileName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            return@use cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                .let(cursor::getString)
        }
    }.getOrNull()

    /** This function is used to calculate the ICMP Ping to a certain server or end host.
     * This is a long blocking call, therefore it should be executed on a background IO thread. **/
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
            e.printStackTrace()
        }
        return result
    }

    /** Convenience log function **/
    fun loggy(string: String) {
        Log.e("Syncplay", string)
    }

    /** Completely revised and working versions for "System UI" manipulators. **/
    @Suppress("DEPRECATION")
    fun ComponentActivity.hideSystemUI(useDeprecated: Boolean) {
        runOnUiThread {
            if (!useDeprecated) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
                        hideSystemUI(false)
                    }
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

            }
        }
    }

    @Suppress("DEPRECATION")
    fun ComponentActivity.showSystemUI(useDeprecated: Boolean) {
        if (useDeprecated) {
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_VISIBLE or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        } else {
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }

    }

    /** A function to control cutout mode (expanding window content beyond notch (front camera) **/
    @TargetApi(Build.VERSION_CODES.P)
    fun ComponentActivity.cutoutMode(enable: Boolean) {
        if (enable) {
            window.attributes?.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            window.attributes?.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }
    }

    /** Returns a hexadecimal color code from a preference ColorInt
     *
     * TODO: Use Datastore **/
    fun getColorCode(key: String, context: Context): String {
        @ColorInt val color =
            PreferenceManager.getDefaultSharedPreferences(context).getInt(key, Color.BLACK)
        return String.format("#%06X", 0xFFFFFF and color)
    }

    /** This basically just changes the Status Bar color */
    fun setStatusBarColor(@ColorInt color: Int, window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = color
    }

    /** Syncplay servers accept passwords in the form of MD5 hashes digested in hexadecimal **/
    fun md5(str: String): ByteArray =
        MessageDigest.getInstance("MD5").digest(str.toByteArray(UTF_8))

    /** Hex Digester for hashers **/
    fun ByteArray.toHex() = joinToString(separator = "") { byte -> "%02x".format(byte) }

    /** Syncplay uses SHA256 hex-digested to hash file names and sizes **/
    fun sha256(str: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(str.toByteArray(UTF_8))

    /** Hides the keyboard and loses message typing focus **/
    fun ComponentActivity.hideKb() {
        lifecycleScope.launch(Dispatchers.Main) {
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
    }

    /** Convenience method to save code space */
    fun Context.toasty(string: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@toasty, string, Toast.LENGTH_SHORT).show()
        }
    }

}