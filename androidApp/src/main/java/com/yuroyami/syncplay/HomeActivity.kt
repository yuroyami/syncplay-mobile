package com.yuroyami.syncplay

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_GLOBAL_SETTINGS
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_MISC_PREFS
import com.yuroyami.syncplay.datastore.DataStoreKeys.MISC_NIGHTMODE
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_DISPLAY_LANG
import com.yuroyami.syncplay.datastore.languageCallback
import com.yuroyami.syncplay.datastore.obtainBoolean
import com.yuroyami.syncplay.datastore.obtainString
import com.yuroyami.syncplay.home.HomeConfig
import com.yuroyami.syncplay.home.HomeScreen
import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.utils.JoinCallback
import com.yuroyami.syncplay.utils.LanguageChange
import com.yuroyami.syncplay.utils.changeLanguage
import com.yuroyami.syncplay.utils.joinCallback
import com.yuroyami.syncplay.watchroom.prepareProtocol
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.StringDesc
import kotlinx.coroutines.runBlocking

class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() /* This will be called only on cold starts */

        /** Adjusting the appearance of system window decor */
        /* Tweaking some window UI elements */
        window.attributes = window.attributes.apply {
            @Suppress("DEPRECATION")
            flags = flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS.inv()
        }
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        /** Applying saved language */
        val lang = runBlocking { DATASTORE_GLOBAL_SETTINGS.obtainString(PREF_DISPLAY_LANG, "en") }
        changeLanguage(lang, this)

        super.onCreate(savedInstanceState)

        /** Applying night mode from the system */
        val nightmodepref = runBlocking { DATASTORE_MISC_PREFS.obtainBoolean(MISC_NIGHTMODE, true) }
        if (nightmodepref) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        }

        /** Getting saved config */
        val config = HomeConfig()

        /****** Composing UI using Jetpack Compose *******/
        setContent {
            HomeScreen(savedConfig = config) /* Shared Compose multiplatform composable */
        }

        /** Language change listener */
        languageCallback = object: LanguageChange {
            override fun onLanguageChanged(newLang: String) {
                recreate()

                Toast.makeText(
                    this@HomeActivity,
                    StringDesc.Resource(MR.strings.setting_display_language_toast).toString(this@HomeActivity),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        joinCallback = object: JoinCallback {
            override fun onJoin(joinInfo: JoinInfo) {

                prepareProtocol(joinInfo.get())

                val intent = Intent(this@HomeActivity, WatchActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent)

            }
        }

        /** Maybe there is a shortcut intent */
        if (intent?.getBooleanExtra("quickLaunch", false) == true) {
            intent.apply {
                val info = JoinInfo(
                    username = getStringExtra("name") ?: "",
                    roomname = getStringExtra("room") ?: "",
                    address = getStringExtra("serverip") ?: "",
                    port = getIntExtra("serverport", 80),
                    password = getStringExtra("serverpw") ?: ""
                )
                joinCallback?.onJoin(info)
            }
        }
    }

    //FIXME
    fun soloMode() {
        val intent = Intent(this, WatchActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        intent.putExtra("SOLO_MODE", true)

        startActivity(intent)
    }
}