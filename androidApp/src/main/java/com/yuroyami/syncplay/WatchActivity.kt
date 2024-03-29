package com.yuroyami.syncplay

//import com.yuroyami.syncplay.settings.SettingObtainerCallback
//import com.yuroyami.syncplay.settings.obtainerCallback
import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C.STREAM_TYPE_MUSIC
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.BasePlayer.ENGINE
import com.yuroyami.syncplay.player.PlayerUtils.pausePlayback
import com.yuroyami.syncplay.player.PlayerUtils.playPlayback
import com.yuroyami.syncplay.player.exo.ExoPlayer
import com.yuroyami.syncplay.player.mpv.MpvPlayer
import com.yuroyami.syncplay.player.mpv.mpvRoomSettings
import com.yuroyami.syncplay.player.vlc.VlcPlayer
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PIP
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_SIZE
import com.yuroyami.syncplay.settings.SettingObtainerCallback
import com.yuroyami.syncplay.settings.obtainerCallback
import com.yuroyami.syncplay.settings.settingBoolean
import com.yuroyami.syncplay.settings.valueBlockingly
import com.yuroyami.syncplay.settings.valueSuspendingly
import com.yuroyami.syncplay.utils.UIUtils.cutoutMode
import com.yuroyami.syncplay.utils.UIUtils.hideSystemUI
import com.yuroyami.syncplay.utils.changeLanguage
import com.yuroyami.syncplay.utils.defaultEngineAndroid
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.watchroom.GestureCallback
import com.yuroyami.syncplay.watchroom.RoomCallback
import com.yuroyami.syncplay.watchroom.RoomUI
import com.yuroyami.syncplay.watchroom.gestureCallback
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("deprecation")
class WatchActivity : ComponentActivity() {

    private var dirPickResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val dirUri = result.data?.data ?: return@registerForActivityResult
                contentResolver.takePersistableUriPermission(
                    dirUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }

    lateinit var audioManager: AudioManager

    /** Now, onto overriding lifecycle methods */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        /** Telling Android that it should keep the screen on, use cut-out mode and go full-screen */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI(false)
        cutoutMode(true)

        /* Volume controls should adjust the music volume while in the app */

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
                terminate()
                viewmodel = null
            }

            override fun onPlayback(paused: Boolean) {
                updatePiPParams()
            }

            override fun onPictureInPicture(enable: Boolean) {

            }

        }

        /** Setting content view, making everything visible */
        setContent {
            RoomUI()
        }
    }


    //    /** Last checkpoint after executing activityresults. This means the activity is fully ready. */
//    @SuppressLint("UnspecifiedRegisterReceiverFlag")
//    override fun onResume() {
//        super.onResume()
//        val filter = IntentFilter(ACTION_PIP_PAUSE_PLAY)
//        registerReceiver(pipBroadcastReceiver, filter)
//
//        hideSystemUI(false)
//
//        /** Applying track choices again so the player doesn't forget about track choices **/
//        reapplyTrackChoices()
//    }


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


    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        terminate()
        //super.onBackPressed()
    }

    fun terminate() {
        if (!isSoloMode) {
            viewmodel?.p?.endConnection(true)
        }

        (viewmodel?.player as? ExoPlayer)?.exoplayer?.release()
        (viewmodel?.player as? MpvPlayer)?.removeObserver()

        finish()
    }

    /** Let's inform Jetpack Compose that we entered picture in picture, to adjust some UI settings */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewmodel?.pipMode?.value = isInPictureInPictureMode
    }

    /** If user leaves the app by any standard means, then we initiate picture-in-picture mode */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (viewmodel?.wentForFilePick != true) {
            initiatePIPmode()
        }
    }

    private fun initiatePIPmode() {
        val isPipAllowed = PREF_INROOM_PIP.settingBoolean() && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        if (!isPipAllowed) return

        moveTaskToBack(true)
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

    /** Applying the locale language preference */
    override fun attachBaseContext(newBase: Context?) {
        /** Applying saved language */
        val lang = valueBlockingly(DataStoreKeys.PREF_DISPLAY_LANG, "en")
        super.attachBaseContext(newBase!!.changeLanguage(lang))
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("pip")
        registerReceiver(pipBroadcastReceiver, filter)

        hideSystemUI(false)

        /** Applying track choices again so the player doesn't forget about track choices **/
        viewmodel?.player?.reapplyTrackChoices()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipBroadcastReceiver)
    }
}