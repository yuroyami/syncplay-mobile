package com.yuroyami.syncplay

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
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
import com.yuroyami.syncplay.viewmodel.PlatformCallback
import com.yuroyami.syncplay.models.JoinInfo
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.BasePlayer.ENGINE
import com.yuroyami.syncplay.player.PlayerUtils.pausePlayback
import com.yuroyami.syncplay.player.PlayerUtils.playPlayback
import com.yuroyami.syncplay.player.exo.ExoPlayer
import com.yuroyami.syncplay.player.mpv.MpvPlayer
import com.yuroyami.syncplay.player.mpv.mpvRoomSettings
import com.yuroyami.syncplay.player.vlc.VlcPlayer
import com.yuroyami.syncplay.protocol.SpProtocolAndroid
import com.yuroyami.syncplay.protocol.SpProtocolKtor
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.screens.adam.AdamScreen
import com.yuroyami.syncplay.screens.room.GestureCallback
import com.yuroyami.syncplay.screens.room.RoomCallback
import com.yuroyami.syncplay.screens.room.gestureCallback
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.DataStoreKeys.MISC_NIGHTMODE
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_SIZE
import com.yuroyami.syncplay.settings.SettingObtainerCallback
import com.yuroyami.syncplay.settings.obtainerCallback
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueFlow
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.utils.UIUtils.cutoutMode
import com.yuroyami.syncplay.utils.UIUtils.hideSystemUI
import com.yuroyami.syncplay.utils.bindWatchdog
import com.yuroyami.syncplay.utils.changeLanguage
import com.yuroyami.syncplay.utils.defaultEngineAndroid
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.watchroom.SpViewModel
import com.yuroyami.syncplay.watchroom.homeCallback
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.prepareProtocol
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncplayActivity : ComponentActivity() {

    lateinit var audioManager: AudioManager

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() /* This will be called only on cold starts */

        if (BuildConfig.FLAVOR != "noLibs") defaultEngineAndroid = BasePlayer.ENGINE.ANDROID_MPV.name

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


        /** Checking whether this APK at this point supports multi-engine players */
        val engine = BasePlayer.ENGINE.valueOf(
            valueBlockingly(DataStoreKeys.MISC_PLAYER_ENGINE, defaultEngineAndroid)
        )

        when (engine) {
            ENGINE.ANDROID_EXOPLAYER -> {
                viewmodel?.player = ExoPlayer()
            }

            ENGINE.ANDROID_MPV -> {
                viewmodel?.player = MpvPlayer()
            }

            ENGINE.ANDROID_VLC -> {
                viewmodel?.player = VlcPlayer()
            }

            else -> {}
        }

        obtainerCallback = object : SettingObtainerCallback {
            override fun getMoreRoomSettings() = if (viewmodel?.player?.engine == ENGINE.ANDROID_MPV) mpvRoomSettings else listOf()
        }

        gestureCallback = object : GestureCallback {
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
        }

        viewmodel?.roomCallback = object : RoomCallback {
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

        /** Language change listener */
        homeCallback = object: PlatformCallback {
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

                val intent = Intent(this@SyncplayActivity, WatchActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }

            override fun onSaveConfigShortcut(joinInfo: JoinInfo) {

                val shortcutIntent = Intent(this@SyncplayActivity, SyncplayActivity::class.java)
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
                val shortcutIcon = IconCompat.createWithResource(this@SyncplayActivity, R.mipmap.ic_launcher)

                val shortcutInfo = ShortcutInfoCompat.Builder(this@SyncplayActivity, shortcutId)
                    .setShortLabel(shortcutLabel)
                    .setIcon(shortcutIcon)
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
            (viewmodel?.player as? ExoPlayer)?.retweakSubtitleAppearance(ccsize.toFloat())
        }
    }

    fun terminate() {
        if (!isSoloMode) {
            viewmodel?.p?.endConnection(true)
        }

        finish()
        viewmodel = null
    }

    /* Let's inform Jetpack Compose that we entered picture in picture, to adjust some UI settings */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewmodel?.pipMode?.value = isInPictureInPictureMode

        if (!isInPictureInPictureMode) {
            viewmodel?.player?.pause()
        }
    }

    private fun initiatePIPmode() {
        val isPipAllowed = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        if (!isPipAllowed) return

        viewmodel?.pipMode?.value = true
        //moveTaskToBack(true)
        updatePiPParams()
        enterPictureInPictureMode()
        viewmodel?.hudVisibilityState?.value = false
    }

    private fun updatePiPParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return

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
            setActions(if (viewmodel?.hasVideoG?.value == true) listOf(action) else listOf())
        }

        try {
            setPictureInPictureParams(params.build())
        } catch (_: IllegalArgumentException) {
        }
    }

    private val pipBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (it.action == "pip") {
                    val pausePlayValue = it.getIntExtra("pause_zero_play_one", -1)

                    if (pausePlayValue == 1) {
                        playPlayback()
                    } else {
                        pausePlayback()
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
        viewmodel?.player?.reapplyTrackChoices()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipBroadcastReceiver)
    }
}