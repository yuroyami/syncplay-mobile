package com.yuroyami.syncplay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys.PREF_INROOM_PLAYER_SUBTITLE_SIZE
import com.yuroyami.syncplay.managers.datastore.valueBlockingly
import com.yuroyami.syncplay.managers.datastore.valueSuspendingly
import com.yuroyami.syncplay.models.JoinConfig
import com.yuroyami.syncplay.ui.screens.adam.AdamScreen
import com.yuroyami.syncplay.utils.bindWatchdog
import com.yuroyami.syncplay.utils.changeLanguage
import com.yuroyami.syncplay.utils.cutoutMode
import com.yuroyami.syncplay.utils.hideSystemUI
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.platformCallback
import com.yuroyami.syncplay.utils.showSystemUI
import com.yuroyami.syncplay.viewmodels.HomeViewmodel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main Activity for the Syncplay Android application.
 *
 * This is a single-activity app - all navigation is handled within Compose.
 */
class SyncplayActivity : ComponentActivity() {
    /**
     * Called when the activity is first created.
     *
     * Performs initialization including:
     * - Installing splash screen
     * - Configuring transparent system bars and edge-to-edge layout
     * - Setting up platform callback implementation
     * - Launching Compose UI
     * - Processing shortcut intents
     */
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

        /** Telling Android that it should keep the screen on */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        /** Binding common logic with platform logic */
        platformCallback = object : PlatformCallback {
            /**
             * Recreates the activity to apply the new language.
             */
            override fun onLanguageChanged(newLang: String) {
                runOnUiThread {
                    recreate()
                }
            }

            /**
             * Creates a pinned home screen shortcut and dynamic shortcut for quick room access.
             *
             * Encodes room configuration in the intent extras for deep linking.
             */
            override fun HomeViewmodel.onSaveConfigShortcut(joinInfo: JoinConfig) {
                val shortcutIntent = Intent(this@SyncplayActivity, SyncplayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    action = Intent.ACTION_MAIN
                    putExtra("quickLaunch", true)
                    putExtra("name", joinInfo.user.trim())
                    putExtra("room", joinInfo.room.trim())
                    putExtra("serverip", joinInfo.ip.trim())
                    putExtra("serverport", joinInfo.port)
                    putExtra("serverpw", joinInfo.pw)
                }


                val shortcutId = "${joinInfo.user}${joinInfo.room}${joinInfo.ip}${joinInfo.port}"
                val shortcutInfo = ShortcutInfoCompat.Builder(this@SyncplayActivity, shortcutId)
                    .setShortLabel(joinInfo.room)
                    .setIcon(IconCompat.createWithResource(this@SyncplayActivity, R.mipmap.ic_launcher))
                    .setIntent(shortcutIntent)
                    .build()

                ShortcutManagerCompat.addDynamicShortcuts(this@SyncplayActivity, listOf(shortcutInfo))

                if (ShortcutManagerCompat.isRequestPinShortcutSupported(this@SyncplayActivity)) {
                    ShortcutManagerCompat.requestPinShortcut(this@SyncplayActivity, shortcutInfo, null)
                }
            }

            /**
             * Removes all dynamic shortcuts created for room configurations.
             */
            override fun onEraseConfigShortcuts() {
                ShortcutManagerCompat.removeAllDynamicShortcuts(this@SyncplayActivity)
            }

            /**
             * Gets the maximum brightness value (1.0 on Android).
             */
            override fun getMaxBrightness() = 1f

            /**
             * Gets the current screen brightness from window attributes or system settings.
             *
             * Handles both manual and automatic brightness modes.
             */
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

            /**
             * Sets the screen brightness level for this window.
             *
             * @param v Brightness value between 0.0 (darkest) and 1.0 (brightest)
             */
            override fun changeCurrentBrightness(v: Float) {
                loggy("Brightness: $v")
                val attrs = window.attributes
                attrs.screenBrightness = v.coerceIn(0f, 1f)
                window.attributes = attrs
            }

            /**
             * Handles orientation and UI changes when entering or leaving a room.
             *
             * - **ENTER**: Locks to landscape, hides system UI, enables cutout mode
             * - **LEAVE**: Restores all orientations, shows system UI, disables cutout mode
             */
            override fun onRoomEnterOrLeave(event: PlatformCallback.RoomEvent) {
                when (event) {
                    PlatformCallback.RoomEvent.ENTER -> {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        hideSystemUI()
                        cutoutMode(true)
                    }

                    PlatformCallback.RoomEvent.LEAVE -> {
                        cutoutMode(false)
                        showSystemUI()
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER

                        //Workaround to force Compose to retain its window insets
                        WindowCompat.setDecorFitsSystemWindows(window, true)
                        WindowCompat.setDecorFitsSystemWindows(window, false)
                    }
                }
            }

            /**
             * Updates Picture-in-Picture controls when playback state changes.
             */
            override fun onPlayback(paused: Boolean) {
                updatePiPParams()
            }

            /**
             * Initiates Picture-in-Picture mode when requested.
             */
            override fun onPictureInPicture(enable: Boolean) {
                if (enable) {
                    initiatePIPmode()
                }
            }
        }

        /****** Composing UI using Jetpack Compose *******/
        setContent {
            LaunchedEffect(null) {
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
            }

            //MainUI
            AdamScreen()
        }

        /** Maybe there is a shortcut intent */
        if (intent?.getBooleanExtra("quickLaunch", false) == true) {
            intent.apply {
                val config = JoinConfig(
                    user = getStringExtra("name") ?: "",
                    room = getStringExtra("room") ?: "",
                    ip = getStringExtra("serverip") ?: "",
                    port = getIntExtra("serverport", 80),
                    pw = getStringExtra("serverpw") ?: ""
                )

                //todo homeViewmodel?.joinRoom(config)
            }
        }

        Thread.setDefaultUncaughtExceptionHandler { t, t2 ->
            loggy(t2.stackTraceToString())
            throw t2
        }
    }

    /**
     * Applies the saved language before the base context is attached.
     *
     * This ensures the correct locale is used when inflating resources.
     */
    override fun attachBaseContext(newBase: Context?) {
        /** Applying saved language */
        val lang = valueBlockingly(DataStoreKeys.PREF_DISPLAY_LANG, "en")
        super.attachBaseContext(newBase!!.changeLanguage(lang))
    }


    /**
     * Called when the activity is becoming visible to the user.
     *
     * Loads subtitle appearance settings for the player.
     * Follows onCreate() and precedes activity results and onResume().
     */
    override fun onStart() {
        super.onStart()

        /* Loading subtitle appearance */
        lifecycleScope.launch(Dispatchers.Main) {
            val ccsize = valueSuspendingly(PREF_INROOM_PLAYER_SUBTITLE_SIZE, 16)
            //TODO  (viewmodel?.player as? ExoPlayer)?.retweakSubtitleAppearance(ccsize.toFloat())
        }
    }

    /**
     * Terminates the activity and cleans up resources.
     *
     * TODO: Properly end network connections before finishing.
     */
    fun terminate() {
        //TODO if (!isSoloMode) {
        //TODO viewmodel?.p?.endConnection(true)
        //TODO }

        finish()
        //TODO viewmodel = null
    }

    /**
     * Handles Picture-in-Picture mode state changes.
     *
     * Updates UI state when entering/exiting PiP mode.
     *
     * @param isInPictureInPictureMode Whether PiP mode is active
     * @param newConfig The new configuration after the PiP change
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        //TODO viewmodel?.pipMode?.value = isInPictureInPictureMode

        if (!isInPictureInPictureMode) {
            //TODO viewmodel?.player?.pause()
        }
    }

    /**
     * Enters Picture-in-Picture mode if supported (Android 8.0+).
     *
     * Updates PiP parameters and enters PiP, hiding the HUD controls.
     */
    @Suppress("DEPRECATION")
    private fun initiatePIPmode() {
        val isPipAllowed = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        if (!isPipAllowed) return

        //TODO viewmodel?.pipMode?.value = true
        //moveTaskToBack(true)
        updatePiPParams()
        enterPictureInPictureMode()
        //TODO viewmodel?.hudVisibilityState?.value = false
    }

    /**
     * Updates Picture-in-Picture parameters including control actions.
     *
     * Creates play/pause remote actions based on current playback state.
     * Requires Android 8.0+.
     *
     * TODO: Implement PiP action buttons for play/pause control.
     */
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

    /**
     * Broadcast receiver for handling Picture-in-Picture control actions.
     *
     * Listens for "pip" action broadcasts and controls playback accordingly.
     */
    private val pipBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { intnt ->
                if (intnt.action == "pip") {
                    val pausePlayValue = intnt.getIntExtra("pause_zero_play_one", -1)

                    //TODO CHECK IF IN ROOM IN THE FIRST PLACE
                    if (pausePlayValue == 1) {
                        //todo roomViewmodel?.actionManager?.playPlayback()
                    } else {
                        //todo roomViewmodel?.actionManager?.pausePlayback()
                    }
                }
            }
        }
    }

    /**
     * Called when the activity comes to the foreground.
     *
     * Registers the PiP broadcast receiver and reapplies player track choices.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("pip")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipBroadcastReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(pipBroadcastReceiver, filter)
        }


        //TODO hideSystemUI(false)

        /** Applying track choices again so the player doesn't forget about track choices **/
        //TODO viewmodel?.player?.reapplyTrackChoices()
    }

    /**
     * Called when the activity is being destroyed.
     *
     * Unregisters the PiP broadcast receiver to prevent memory leaks.
     */
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipBroadcastReceiver)
    }
}