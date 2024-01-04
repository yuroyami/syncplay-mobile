package com.yuroyami.syncplay

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
import androidx.media3.common.C.STREAM_TYPE_MUSIC
import com.yuroyami.syncplay.compose.popups.wentForFilePick
import com.yuroyami.syncplay.player.BasePlayer.ENGINE
import com.yuroyami.syncplay.player.PlayerUtils.getEngineForString
import com.yuroyami.syncplay.player.PlayerUtils.pausePlayback
import com.yuroyami.syncplay.player.PlayerUtils.playPlayback
import com.yuroyami.syncplay.player.exo.ExoPlayer
import com.yuroyami.syncplay.player.mpv.MpvPlayer
import com.yuroyami.syncplay.settings.DataStoreKeys
import com.yuroyami.syncplay.settings.DataStoreKeys.PREF_INROOM_PIP
import com.yuroyami.syncplay.settings.obtainBoolean
import com.yuroyami.syncplay.settings.obtainString
import com.yuroyami.syncplay.utils.UIUtils.cutoutMode
import com.yuroyami.syncplay.utils.UIUtils.hideSystemUI
import com.yuroyami.syncplay.utils.changeLanguage
import com.yuroyami.syncplay.utils.defaultEngineAndroid
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.watchroom.GestureCallback
import com.yuroyami.syncplay.watchroom.RoomCallback
import com.yuroyami.syncplay.watchroom.RoomUI
import com.yuroyami.syncplay.watchroom.gestureCallback
import com.yuroyami.syncplay.watchroom.hasVideoG
import com.yuroyami.syncplay.watchroom.hudVisibilityState
import com.yuroyami.syncplay.watchroom.p
import com.yuroyami.syncplay.watchroom.pipMode
import com.yuroyami.syncplay.watchroom.player
import com.yuroyami.syncplay.watchroom.roomCallback
import kotlinx.coroutines.runBlocking

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
        val engine = getEngineForString(
            runBlocking { obtainString(DataStoreKeys.MISC_PLAYER_ENGINE, defaultEngineAndroid) }
        )

        when (engine) {
            ENGINE.ANDROID_EXOPLAYER -> player = ExoPlayer()
            ENGINE.ANDROID_MPV -> player = MpvPlayer()
            else -> {}
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
                window.attributes.screenBrightness = v.coerceIn(0f, 1f)
            }
        }

        roomCallback = object : RoomCallback {
            override fun onLeave() {
                terminate()
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
//
//    override fun onDestroy() {
//        super.onDestroy()
//        unregisterReceiver(pipBroadcastReceiver)
//    }
//
//    /** the onStart() follows the onCreate(), it means all the UI is ready
//     * It precedes any activity results. onCreate -> onStart -> ActivityResults -> onResume */
//    override fun onStart() {
//        super.onStart()
//
//
//        /** Loading subtitle appearance */
//        lifecycleScope.launch(Dispatchers.Main) {
//            val ccsize = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.obtainInt(PREF_INROOM_PLAYER_SUBTITLE_SIZE, 16)
//            retweakSubtitleAppearance(ccsize.toFloat())
//        }
//    }
//

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        terminate()
        //super.onBackPressed()
    }

    fun terminate() {
        p.endConnection(true)

        (player as? ExoPlayer)?.exoplayer?.release()
        (player as? MpvPlayer)?.removeObserver()

        finish()
    }

    /** Let's inform Jetpack Compose that we entered picture in picture, to adjust some UI settings */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode.value = isInPictureInPictureMode
    }

    /** If user leaves the app by any standard means, then we initiate picture-in-picture mode */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (!wentForFilePick) {
            initiatePIPmode()
        }
    }

    private fun initiatePIPmode() {
        val isPipAllowed = runBlocking {obtainBoolean(PREF_INROOM_PIP, true) }
                && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        if (!isPipAllowed) return

        moveTaskToBack(true)
        updatePiPParams()
        enterPictureInPictureMode()
        hudVisibilityState.value = false
    }

    private fun updatePiPParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return

        val intent = Intent(pipACTION)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 6969, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)

        val action = if (player?.isPlaying() == true) {
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_pause),
                "Play", "", pendingIntent)
        } else {
            RemoteAction(Icon.createWithResource(this, R.drawable.ic_play),
                "Pause", "", pendingIntent)
        }

        val params = with(PictureInPictureParams.Builder()) {
            setActions(if (hasVideoG.value) listOf(action) else listOf())
        }

        try {
            setPictureInPictureParams(params.build())
        } catch (_: IllegalArgumentException) { }
    }

    private val pipBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                if (it.action == pipACTION) {
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

    val pipACTION = "action_pip_pause_play"

    /** Applying the locale language preference */
    override fun attachBaseContext(newBase: Context?) {
        /** Applying saved language */
        val lang = runBlocking { obtainString(DataStoreKeys.PREF_DISPLAY_LANG, "en") }
        super.attachBaseContext(newBase!!.changeLanguage(lang))
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(pipACTION)
        registerReceiver(pipBroadcastReceiver, filter)

        hideSystemUI(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipBroadcastReceiver)
    }
}