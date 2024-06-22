package com.yuroyami.syncplay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
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
import com.yuroyami.syncplay.home.HomeCallback
import com.yuroyami.syncplay.home.HomeConfig
import com.yuroyami.syncplay.home.HomeScreen
import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.protocol.SpProtocolAndroid
import com.yuroyami.syncplay.protocol.SpProtocolKtor
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.DataStoreKeys.MISC_NIGHTMODE
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueFlow
import com.yuroyami.syncplay.utils.changeLanguage
import com.yuroyami.syncplay.utils.defaultEngineAndroid
import com.yuroyami.syncplay.watchroom.SpViewModel
import com.yuroyami.syncplay.watchroom.homeCallback
import com.yuroyami.syncplay.watchroom.prepareProtocol
import com.yuroyami.syncplay.watchroom.viewmodel

class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() /* This will be called only on cold starts */

        if (BuildConfig.FLAVOR != "noLibs") defaultEngineAndroid = BasePlayer.ENGINE.ANDROID_MPV.name

        super.onCreate(savedInstanceState)

        /** Adjusting the appearance of system window decor */
        /* Tweaking some window UI elements */
        window.attributes = window.attributes.apply {
            @Suppress("DEPRECATION")
            flags = flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS.inv()
        }
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        /** Getting saved config */
        val config = HomeConfig()

        /****** Composing UI using Jetpack Compose *******/
        setContent {
            val nightMode by valueFlow(MISC_NIGHTMODE, false).collectAsState(initial = false)

            LaunchedEffect(null, nightMode) {
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !nightMode
            }

            HomeScreen(config) /* Shared Compose multiplatform composable */
        }

        /** Language change listener */
        homeCallback = object: HomeCallback {
            override fun onLanguageChanged(newLang: String) {
                runOnUiThread {
                    recreate()
                }
            }

            override fun onJoin(joinInfo: JoinInfo?) {
                viewmodel = SpViewModel()

                joinInfo?.let {
                    val networkEngine = SyncplayProtocol.getPreferredEngine()
                    viewmodel!!.p = if (networkEngine == SyncplayProtocol.NetworkEngine.KTOR) {
                        SpProtocolKtor()
                    } else {
                        SpProtocolAndroid()
                    }

                    prepareProtocol(it)
                }

                val intent = Intent(this@HomeActivity, WatchActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }

            override fun onSaveConfigShortcut(joinInfo: JoinInfo) {

                val shortcutIntent = Intent(this@HomeActivity, HomeActivity::class.java)
                shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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

                if (ShortcutManagerCompat.isRequestPinShortcutSupported(this@HomeActivity)) {
                    ShortcutManagerCompat.requestPinShortcut(this@HomeActivity, shortcutInfo, null)
                }
            }

            override fun onEraseConfigShortcuts() {
                ShortcutManagerCompat.removeAllDynamicShortcuts(this@HomeActivity)
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
                homeCallback?.onJoin(info.get())
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        /** Applying saved language */
        val lang = valueBlockingly(DataStoreKeys.PREF_DISPLAY_LANG, "en")
        super.attachBaseContext(newBase!!.changeLanguage(lang))
    }
}