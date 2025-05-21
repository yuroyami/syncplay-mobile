package com.yuroyami.syncplay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
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
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C.STREAM_TYPE_MUSIC
import com.yuroyami.syncplay.screens.adam.AdamScreen
import com.yuroyami.syncplay.screens.home.JoinConfig
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.DataStoreKeys.MISC_NIGHTMODE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_SIZE
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueFlow
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.utils.UIUtils.cutoutMode
import com.yuroyami.syncplay.utils.UIUtils.hideSystemUI
import com.yuroyami.syncplay.utils.bindWatchdog
import com.yuroyami.syncplay.utils.changeLanguage
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.platformCallback
import com.yuroyami.syncplay.viewmodel.PlatformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncplayActivity : ComponentActivity() {

    lateinit var audioManager: AudioManager

    //TODO NEED REFERENCE TO VIEWMODEL

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() /* This will be called only on cold starts */

        /** Communicates the lifecycle with our common code */
        bindWatchdog()

        super.onCreate(savedInstanceState)

        /** Adjusting the appearance of system window decor */
        /* Tweaking some window UI elements */
        window.attributes = window.attributes.apply {
            flags = flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS.inv()
        }
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        /** Telling Android that it should keep the screen on, use cut-out mode and go full-screen */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI(false)
        cutoutMode(true)

        /** Binding common logic with platform logic */
        platformCallback = object: PlatformCallback {
            override fun onLanguageChanged(newLang: String) {
                runOnUiThread {
                    recreate()
                }
            }

            override fun onSaveConfigShortcut(joinConfig: JoinConfig) {
                val shortcutIntent = Intent(this@SyncplayActivity, SyncplayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    action = Intent.ACTION_MAIN
                    putExtra("quickLaunch", true)
                    putExtra("name", joinConfig.user.trim())
                    putExtra("room", joinConfig.room.trim())
                    putExtra("serverip", joinConfig.ip.trim())
                    putExtra("serverport", joinConfig.port)
                    putExtra("serverpw", joinConfig.pw)
                }


                val shortcutId = "${joinConfig.user}${joinConfig.room}${joinConfig.ip}${joinConfig.port}"
                val shortcutInfo = ShortcutInfoCompat.Builder(this@SyncplayActivity, shortcutId)
                    .setShortLabel(joinConfig.room)
                    .setIcon(IconCompat.createWithResource(this@SyncplayActivity, R.mipmap.ic_launcher))
                    .setIntent(shortcutIntent)
                    .build()

                ShortcutManagerCompat.addDynamicShortcuts(this@SyncplayActivity, listOf(shortcutInfo))

                if (ShortcutManagerCompat.isRequestPinShortcutSupported(this@SyncplayActivity)) {
                    ShortcutManagerCompat.requestPinShortcut(this@SyncplayActivity, shortcutInfo, null)
                }
            }

            override fun onEraseConfigShortcuts() {
                ShortcutManagerCompat.removeAllDynamicShortcuts(this@SyncplayActivity)
            }

            //TODO override fun getMoreRoomSettings() = if (viewmodel?.player?.engine == ENGINE.ANDROID_MPV) mpvRoomSettings else listOf()

            override fun getMaxVolume() = audioManager.getStreamMaxVolume(STREAM_TYPE_MUSIC)
            override fun getCurrentVolume() = audioManager.getStreamVolume(STREAM_TYPE_MUSIC)
            override fun changeCurrentVolume(v: Int) {
                if (!audioManager.isVolumeFixed) {
                    audioManager.setStreamVolume(STREAM_TYPE_MUSIC, v, 0)
                }
            }

            override fun getMaxBrightness() = 1f
            override fun getCurrentBrightness(): Float {
                val brightness = window.attributes.screenBrightness

                //Check if we already have a brightness
                val brightnesstemp = if (brightness != -1f)
                    brightness
                else {
                    //Check if the device is in auto mode
                    if (Settings.System.getInt(
                            contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    ) {
                        //cannot retrieve a value -> 0.5
                        0.5f
                    } else Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128).toFloat() / 255
                }

                return brightnesstemp
            }

            override fun changeCurrentBrightness(v: Float) {
                loggy("Brightness: $v", 0)
                val attrs = window.attributes
                attrs.screenBrightness = v.coerceIn(0f, 1f)
                window.attributes = attrs
            }

            override fun onLeave() {
                /* TODO val intent = Intent(this@WatchActivity, HomeActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(intent)
                terminate() */
            }

            override fun onPlayback(paused: Boolean) {
                updatePiPParams()
            }

            override fun onPictureInPicture(enable: Boolean) {
                if (enable) {
                    initiatePIPmode()
                }
            }
        }

        /****** Composing UI using Jetpack Compose *******/
        setContent {
            val nightMode by valueFlow(MISC_NIGHTMODE, false).collectAsState(initial = false)

            LaunchedEffect(null, nightMode) {
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !nightMode
            }

            //MainUI
            AdamScreen()
        }

        /** Maybe there is a shortcut intent */
        if (intent?.getBooleanExtra("quickLaunch", false) == true) {
            intent.apply {
                val info = JoinConfig(
                    user = getStringExtra("name") ?: "",
                    room = getStringExtra("room") ?: "",
                    ip = getStringExtra("serverip") ?: "",
                    port = getIntExtra("serverport", 80),
                    pw = getStringExtra("serverpw") ?: ""
                )

                //TODO LAUNCH SHORTCUT
            }
        }

        Thread.setDefaultUncaughtExceptionHandler { t, t2 ->
            loggy(t2.stackTraceToString())
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        /** Applying saved language */
        val lang = valueBlockingly(DataStoreKeys.PREF_DISPLAY_LANG, "en")
        super.attachBaseContext(newBase!!.changeLanguage(lang))
    }


    /** the onStart() follows the onCreate(), it means all the UI is ready
     * It precedes any activity results. onCreate -> onStart -> ActivityResults -> onResume */
    override fun onStart() {
        super.onStart()

        /* Loading subtitle appearance */
        lifecycleScope.launch(Dispatchers.Main) {
            val ccsize = valueSuspendingly(PREF_INROOM_PLAYER_SUBTITLE_SIZE, 16)
            //TODO  (viewmodel?.player as? ExoPlayer)?.retweakSubtitleAppearance(ccsize.toFloat())
        }
    }

    fun terminate() {
        //TODO if (!isSoloMode) {
            //TODO viewmodel?.p?.endConnection(true)
        //TODO }

        finish()
        //TODO viewmodel = null
    }

    /* Let's inform Jetpack Compose that we entered picture in picture, to adjust some UI settings */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        //TODO viewmodel?.pipMode?.value = isInPictureInPictureMode

        if (!isInPictureInPictureMode) {
            //TODO viewmodel?.player?.pause()
        }
    }

    private fun initiatePIPmode() {
        val isPipAllowed = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        if (!isPipAllowed) return

        //TODO viewmodel?.pipMode?.value = true
        //moveTaskToBack(true)
        updatePiPParams()
        enterPictureInPictureMode()
        //TODO viewmodel?.hudVisibilityState?.value = false
    }

    private fun updatePiPParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return

        /*
        val intent = Intent("pip")
        val pendingIntent = PendingIntent.getBroadcast(
            this, 6969, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )

        val action = if (viewmodel?.player?.isPlaying() == true) {
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_pause),
                "Play", "", pendingIntent
            )
        } else {
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_play),
                "Pause", "", pendingIntent
            )
        }

        val params = with(PictureInPictureParams.Builder()) {
            //TODO setActions(if (viewmodel?.hasVideoG?.value == true) listOf(action) else listOf())
        }

        try {
            //TODO setPictureInPictureParams(params.build())
        } catch (_: IllegalArgumentException) {
        }

         */
    }

    private val pipBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (it.action == "pip") {
                    val pausePlayValue = it.getIntExtra("pause_zero_play_one", -1)

                    if (pausePlayValue == 1) {
                        //TODO playPlayback()
                    } else {
                        //TODO pausePlayback()
                    }
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("pip")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipBroadcastReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(pipBroadcastReceiver, filter)
        }


        hideSystemUI(false)

        /** Applying track choices again so the player doesn't forget about track choices **/
        //TODO viewmodel?.player?.reapplyTrackChoices()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipBroadcastReceiver)
    }
}