package com.yuroyami.syncplay

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.yuroyami.syncplay.datastore.DataStoreKeys
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_GLOBAL_SETTINGS
import com.yuroyami.syncplay.datastore.DataStoreKeys.DATASTORE_MISC_PREFS
import com.yuroyami.syncplay.datastore.obtainString
import com.yuroyami.syncplay.player.ENGINE
import com.yuroyami.syncplay.player.PlayerUtils.getEngineForString
import com.yuroyami.syncplay.player.exo.ExoPlayer
import com.yuroyami.syncplay.player.mpv.MpvPlayer
import com.yuroyami.syncplay.utils.UIUtils.cutoutMode
import com.yuroyami.syncplay.utils.UIUtils.hideSystemUI
import com.yuroyami.syncplay.utils.changeLanguage
import com.yuroyami.syncplay.utils.defaultEngineAndroid
import com.yuroyami.syncplay.watchroom.PickerCallback
import com.yuroyami.syncplay.watchroom.RoomUI
import com.yuroyami.syncplay.watchroom.pickFuture
import com.yuroyami.syncplay.watchroom.pickerCallback
import com.yuroyami.syncplay.watchroom.player
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

    private var videoPickResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val video = result.data?.data ?: return@registerForActivityResult

                pickFuture?.complete(video.toString()) //Tell our commonMain shared module that we picked a video
            }
        }

    /** Now, onto overriding lifecycle methods */
    override fun onCreate(sis: Bundle?) {
        /** Applying saved language */
        val lang = runBlocking { DATASTORE_GLOBAL_SETTINGS.obtainString(DataStoreKeys.PREF_DISPLAY_LANG, "en") }
        changeLanguage(lang = lang, context = this)

        super.onCreate(sis)

        /** Telling Android that it should keep the screen on, use cut-out mode and go full-screen */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI(false)
        cutoutMode(true)

        /** Checking whether this APK at this point supports multi-engine players */
        val engine = getEngineForString(
            runBlocking { DATASTORE_MISC_PREFS.obtainString(DataStoreKeys.MISC_PLAYER_ENGINE, defaultEngineAndroid) }
        )

        when (engine) {
            ENGINE.ANDROID_EXOPLAYER -> player = ExoPlayer()
            ENGINE.ANDROID_MPV -> player = MpvPlayer()
            else -> {}
        }

        pickerCallback = object: PickerCallback {
            override fun goPickVideo() {
                val intent = Intent()
                intent.action = Intent.ACTION_OPEN_DOCUMENT
                intent.type = "video/*"
                videoPickResult.launch(intent)
            }

            override fun goPickFolder() {
                val intent = Intent()
                intent.action = Intent.ACTION_OPEN_DOCUMENT_TREE
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                dirPickResult.launch(intent)
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
//    override fun onBackPressed() {
//        //super.onBackPressed()
//        terminate()
//    }
//
//    fun terminate() {
//        p.setBroadcaster(null)
//        p.channel?.close()
//        if (player?.ismpvInit == true) {
//            MPVLib.removeObserver(player?.observer)
//            MPVLib.destroy()
//        }
//        finish()
//    }
//
//    /** Let's inform Jetpack Compose that we entered picture in picture, to adjust some UI settings */
//    @RequiresApi(Build.VERSION_CODES.O)
//    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
//        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
//        pipMode.value = isInPictureInPictureMode
//    }
//
//    /** If user leaves the app by any standard means, then we initiate picture-in-picture mode */
//    override fun onUserLeaveHint() {
//        super.onUserLeaveHint()
//
//        if (!wentForFilePick) {
//            initiatePIPmode()
//        }
//    }
//
//    fun initiatePIPmode() {
//        val isPipAllowed = runBlocking {
//            DATASTORE_INROOM_PREFERENCES.obtainBoolean(PREF_INROOM_PIP, true)
//        } && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
//
//        if (!isPipAllowed) return
//
//        moveTaskToBack(true)
//
//        updatePiPParams()
//
//        enterPictureInPictureMode()
//        hudVisibilityState.value = false
//    }
//
//    fun updatePiPParams() {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
//            return
//
//        val intent = Intent(ACTION_PIP_PAUSE_PLAY)
//        val pendingIntent = PendingIntent.getBroadcast(
//            this, 6969, intent,
//            PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
//
//        val action = if (player?.isInPlayState() == true) {
//            RemoteAction(Icon.createWithResource(this, R.drawable.ic_pause),
//                "Play", "", pendingIntent)
//        } else {
//            RemoteAction(Icon.createWithResource(this, R.drawable.ic_play),
//                "Pause", "", pendingIntent)
//        }
//
//        val params = with(PictureInPictureParams.Builder()) {
//            setActions(if (hasVideoG.value) listOf(action) else listOf())
//        }
//
//        try {
//            setPictureInPictureParams(params.build())
//        } catch (_: IllegalArgumentException) { }
//    }
//
//    private val pipBroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent?) {
//            intent?.let {
//                if (it.action == ACTION_PIP_PAUSE_PLAY) {
//                    val pausePlayValue = it.getIntExtra("pause_zero_play_one", -1)
//
//                    if (pausePlayValue == 1) {
//                        playPlayback()
//                    } else {
//                        pausePlayback()
//                    }
//                }
//            }
//        }
//    }
//
//    val ACTION_PIP_PAUSE_PLAY = "action_pip_pause_play"
}