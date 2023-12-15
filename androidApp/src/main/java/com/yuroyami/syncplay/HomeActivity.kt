package com.yuroyami.syncplay

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_GLOBAL_SETTINGS
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_MISC_PREFS
import com.yuroyami.syncplay.datastore.DataStoreKeys.MISC_NIGHTMODE
import com.yuroyami.syncplay.datastore.DataStoreKeys.PREF_DISPLAY_LANG
import com.yuroyami.syncplay.datastore.booleanFlow
import com.yuroyami.syncplay.datastore.ds
import com.yuroyami.syncplay.datastore.languageCallback
import com.yuroyami.syncplay.datastore.obtainString
import com.yuroyami.syncplay.home.HomeConfig
import com.yuroyami.syncplay.home.HomeScreen
import com.yuroyami.syncplay.locale.Localization.stringResource
import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.utils.JoinCallback
import com.yuroyami.syncplay.utils.LanguageChange
import com.yuroyami.syncplay.utils.changeLanguage
import com.yuroyami.syncplay.utils.defaultEngineAndroid
import com.yuroyami.syncplay.utils.joinCallback
import com.yuroyami.syncplay.watchroom.prepareProtocol
import kotlinx.coroutines.runBlocking

class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() /* This will be called only on cold starts */

        defaultEngineAndroid =  if (BuildConfig.FLAVOR == "noLibs") "exo" else "mpv"

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

        /** Getting saved config */
        val config = HomeConfig()

        /****** Composing UI using Jetpack Compose *******/
        setContent {
            val nightMode by DATASTORE_MISC_PREFS.ds().booleanFlow(MISC_NIGHTMODE, false).collectAsState(initial = false)

            LaunchedEffect(nightMode) {
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !nightMode
            }

            HomeScreen(config = config) /* Shared Compose multiplatform composable */
        }

        /** Language change listener */
        languageCallback = object: LanguageChange {
            override fun onLanguageChanged(newLang: String) {
                runOnUiThread {
                    recreate()

                    Toast.makeText(
                        this@HomeActivity,
                        stringResource("setting_display_language_toast"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        joinCallback = object: JoinCallback {
            override fun onJoin(joinInfo: JoinInfo) {
                prepareProtocol(joinInfo.get())

                val intent = Intent(this@HomeActivity, WatchActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent)
            }

            override fun onSaveConfigShortcut(joinInfo: JoinInfo) {

                val shortcutIntent = Intent(this@HomeActivity, HomeActivity::class.java)
                shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                shortcutIntent.action = Intent.ACTION_MAIN
                shortcutIntent.putExtra("quickLaunch", true)
                shortcutIntent.putExtra("name", joinInfo.username.trim())
                shortcutIntent.putExtra("room", joinInfo.roomname.trim())
                shortcutIntent.putExtra("serverip", joinInfo.address.trim())
                shortcutIntent.putExtra("serverport", joinInfo.port)
                shortcutIntent.putExtra("serverpw", joinInfo.password)

                val shortcutId = "${joinInfo.username}${joinInfo.roomname}${joinInfo.address}${joinInfo.port}"
                val shortcutLabel = joinInfo.roomname
                val shortcutIcon = IconCompat.createWithResource(this@HomeActivity, R.mipmap.ic_launcher)

                val shortcutInfo = ShortcutInfoCompat.Builder(this@HomeActivity, shortcutId)
                    .setShortLabel(shortcutLabel)
                    .setIcon(shortcutIcon)
                    .setIntent(shortcutIntent)
                    .build()

                ShortcutManagerCompat.addDynamicShortcuts(this@HomeActivity, listOf(shortcutInfo))
                ShortcutManagerCompat.requestPinShortcut(this@HomeActivity, shortcutInfo, null)
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