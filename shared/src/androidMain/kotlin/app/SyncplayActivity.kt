package app

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
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import app.home.HomeViewmodel
import app.home.JoinConfig
import app.player.Playback
import app.player.SyncplayMediaSessionService
import app.player.exo.ExoImpl
import app.preferences.Preferences.DISPLAY_LANG
import app.preferences.Preferences.SUBTITLE_SIZE
import app.preferences.value
import app.room.RoomViewmodel
import app.server.SyncplayServerService
import app.utils.applyActivityUiProperties
import app.utils.bindWatchdog
import app.utils.changeLanguage
import app.utils.loggy
import app.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Main Activity for the Syncplay Android application.
 *
 * This is a single-activity app - all navigation is handled within Compose.
 */
class SyncplayActivity : ComponentActivity() {

    lateinit var globalViewmodel: SyncplayViewmodel

    val homeViewmodel: HomeViewmodel?
        get() = if (::globalViewmodel.isInitialized) globalViewmodel.homeWeakRef?.get() else null

    val roomViewmodel: RoomViewmodel?
        get() = if (::globalViewmodel.isInitialized) globalViewmodel.roomWeakRef?.get() else null

    lateinit var media3Controller: MediaController

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

        /** Install crash handler early so it catches everything after this point */
        CrashHandler.install()

        // TEST CRASH — remove before release
//        lifecycleScope.launch {
//            kotlinx.coroutines.delay(500)
//            error("Test crash: CrashOverlay is working!")
//        }

        /** Adjusting the appearance of system window decor */
        /* Tweaking some window UI elements */
        applyActivityUiProperties()

        /** Binding common logic with platform logic */
        platformCallback = object : PlatformCallback {
            override fun mediaSessionInitialize() {
                startForegroundService(Intent(this@SyncplayActivity, SyncplayMediaSessionService::class.java))
            }

            override fun mediaSessionFinalize() {
                stopService(Intent(this@SyncplayActivity, SyncplayMediaSessionService::class.java))
            }

            override fun serverServiceStart(port: Int) {
                val intent = Intent(this@SyncplayActivity, SyncplayServerService::class.java).apply {
                    putExtra(SyncplayServerService.EXTRA_PORT, port)
                }
                startForegroundService(intent)
            }

            override fun serverServiceStop() {
                stopService(Intent(this@SyncplayActivity, SyncplayServerService::class.java))
            }

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

            override fun onScreenOrientationChanged(portrait: Boolean) {
                requestedOrientation = if (portrait)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            override fun performHapticFeedback() {
                val vibrator = getSystemService(Vibrator::class.java) ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }

            /**
             * Launches the "custom picker" — an `ACTION_GET_CONTENT` chooser that lets the user
             * pick which installed file manager / explorer to browse with. Routes the result
             * through [pendingSystemFilePickerCallback] back to the caller. Helpful for mpv to
             * play SMB-backed files that are hidden by FileKit's extension-based filter.
             */
            override fun launchSystemFilePicker(onResult: (String?) -> Unit) {
                pendingSystemFilePickerCallback = onResult
                runCatching {
                    systemFilePickerLauncher.launch("*/*")
                }.onFailure {
                    pendingSystemFilePickerCallback = null
                    onResult(null)
                }
            }
        }

        /****** Composing UI using Jetpack Compose *******/
        setContent {
            coil3.compose.setSingletonImageLoaderFactory { context ->
                coil3.ImageLoader.Builder(context)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) {
                            add(coil3.gif.AnimatedImageDecoder.Factory())
                        } else {
                            add(coil3.gif.GifDecoder.Factory())
                        }
                    }
                    .build()
            }

            LaunchedEffect(null) {
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
            }

            //MainUI
            Box {
                AdamScreen(
                    onGlobalViewmodel = {
                        globalViewmodel = it
                    }
                )

                CrashOverlay()
            }
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

                lifecycleScope.launch {
                    homeViewmodel?.joinRoom(config)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Applies the saved language before the base context is attached.
     *
     * This ensures the correct locale is used when inflating resources.
     */
    override fun attachBaseContext(newBase: Context?) {
        /** Applying saved language (fall back to default if DataStore isn't ready yet) */
        val lang = runCatching { DISPLAY_LANG.value() }.getOrDefault(DISPLAY_LANG.default as String)
        super.attachBaseContext(newBase!!.changeLanguage(lang))
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Reapply locale after orientation changes
        val lang = DISPLAY_LANG.value()
        val locale = Locale.Builder().setLanguage(lang).build()
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
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
            val ccsize = SUBTITLE_SIZE.value()
            (roomViewmodel?.player as? ExoImpl)?.retweakSubtitleAppearance(ccsize.toFloat())
        }
    }

    /**
     * Handles Picture-in-Picture mode state changes.
     *
     * Updates UI state when entering/exiting PiP mode.
     *
     * @param isInPictureInPictureMode Whether PiP mode is active
     * @param newConfig The new configuration after the PiP change
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        roomViewmodel?.uiState?.hasEnteredPipMode?.value = isInPictureInPictureMode
    }

    /**
     * Enters Picture-in-Picture mode if supported (Android 8.0+).
     *
     * Updates PiP parameters and enters PiP, hiding the HUD controls.
     */
    private fun initiatePIPmode() {
        roomViewmodel?.uiState?.hasEnteredPipMode?.value = true

        lifecycleScope.launch {
            val playing = roomViewmodel?.player?.isPlaying() == true
            val params = buildPiPParams(playing)
            runCatching {
                enterPictureInPictureMode(params)
            }
            roomViewmodel?.uiState?.visibleHUD?.value = false
        }
    }

    /**
     * Builds PiP parameters with play/pause remote action.
     *
     * Creates a PendingIntent that carries the intended action (0=pause, 1=play)
     * so the broadcast receiver knows what to do.
     */
    private fun buildPiPParams(isPlaying: Boolean = false): PictureInPictureParams {

        // When playing → show pause button (action=0 means pause)
        // When paused  → show play button  (action=1 means play)
        val actionValue = if (isPlaying) 0 else 1
        val intent = Intent("pip").putExtra("pause_zero_play_one", actionValue)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 6969 + actionValue, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )

        val action = RemoteAction(
            Icon.createWithResource(
                this,
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            ),
            if (isPlaying) "Pause" else "Play",
            if (isPlaying) "Pause playback" else "Resume playback",
            pendingIntent
        )

        return PictureInPictureParams.Builder()
            .setActions(if (roomViewmodel?.hasVideo?.value == true) listOf(action) else listOf())
            .build()
    }

    /**
     * Updates the PiP parameters on the activity to reflect current playback state.
     */
    private fun updatePiPParams() {
        lifecycleScope.launch {
            val playing = roomViewmodel?.player?.isPlaying() == true
            runCatching {
                setPictureInPictureParams(buildPiPParams(playing))
            }
        }
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

                    if (pausePlayValue == 1) {
                        roomViewmodel?.dispatcher?.controlPlayback(Playback.PLAY, true)
                    } else if (pausePlayValue == 0) {
                        roomViewmodel?.dispatcher?.controlPlayback(Playback.PAUSE, true)
                    }

                    // Refresh the PiP action button to reflect new state
                    updatePiPParams()
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

        /** Applying track choices again so the player doesn't forget about track choices **/
        lifecycleScope.launch {
            roomViewmodel?.player?.reapplyTrackChoices()
        }
    }

    /**
     * Handles D-pad and media button key events for Android TV / Google TV.
     *
     * Media buttons always control playback. When a video is loaded and the HUD is hidden,
     * D-pad keys control playback (left/right = seek, center = play/pause) and reveal the HUD.
     * When the HUD is visible, D-pad events pass through to Compose for focus navigation.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val vm = roomViewmodel

        // Media buttons: always handle when in room
        if (vm != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    val playing = vm.playerManager.hasVideo.value
                    if (playing) {
                        lifecycleScope.launch {
                            val isPlaying = vm.player.isPlaying() == true
                            vm.dispatcher.controlPlayback(
                                if (isPlaying) Playback.PAUSE else Playback.PLAY, true
                            )
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    vm.dispatcher.controlPlayback(Playback.PLAY, true)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    vm.dispatcher.controlPlayback(Playback.PAUSE, true)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    vm.dispatcher.seekFrwrd()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    vm.dispatcher.seekBckwd()
                    return true
                }
            }

            // D-pad: only intercept when HUD is hidden and video is loaded
            val hasVideo = vm.playerManager.hasVideo.value
            val hudVisible = vm.uiState.visibleHUD.value

            if (hasVideo && !hudVisible) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        lifecycleScope.launch {
                            val isPlaying = vm.player.isPlaying() == true
                            vm.dispatcher.controlPlayback(
                                if (isPlaying) Playback.PAUSE else Playback.PLAY, true
                            )
                        }
                        vm.uiState.visibleHUD.value = true
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        vm.dispatcher.seekBckwd()
                        vm.uiState.visibleHUD.value = true
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        vm.dispatcher.seekFrwrd()
                        vm.uiState.visibleHUD.value = true
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        vm.uiState.visibleHUD.value = true
                        return true
                    }
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(pipBroadcastReceiver) }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private var notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                applicationContext,
                "Notification permission is required to show playback controls outside the app.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Callback indirection for the "custom picker" (chooser-of-file-managers) flow. The
     * PlatformCallback override stores the caller's `onResult` here, then launches
     * [systemFilePickerLauncher]. When the picker returns, the result handler invokes and
     * clears this callback.
     */
    private var pendingSystemFilePickerCallback: ((String?) -> Unit)? = null

    /**
     * "Custom picker" launcher — fires `ACTION_GET_CONTENT` wrapped in [Intent.createChooser] so
     * the user is presented with a selector of every installed file manager / explorer / cloud
     * app that registered as a content source (FX, MiXplorer, Solid Explorer, Drive, Dropbox,
     * LocalSend, etc.), in addition to the system Documents UI.
     *
     * This complements FileKit's default launcher (which goes straight to the SAF Documents UI
     * via `ACTION_OPEN_DOCUMENT` with an extension-derived MIME filter). Two reasons to offer it:
     *
     *  1. **SMB / cloud DocumentsProviders**: some providers report files with opaque MIME types
     *     (`application/octet-stream`) that FileKit's extension filter hides; routing through a
     *     third-party file manager bypasses that filter.
     *  2. **User preference**: some users keep their media indexed in a specific file manager
     *     and want to browse there directly.
     *
     * Note: `ACTION_GET_CONTENT` URIs are **not persistable** (unlike `ACTION_OPEN_DOCUMENT`
     * results), so we skip [android.content.ContentResolver.takePersistableUriPermission]. The
     * returned URI is readable for the activity's lifetime, which is sufficient for immediate
     * playback; it may become invalid on process restart (acceptable trade-off — the FileKit
     * path is already the recommended choice for persistent playlist entries).
     */
    private val systemFilePickerLauncher = registerForActivityResult(
        object : ActivityResultContract<String, Uri?>() {
            override fun createIntent(context: Context, input: String): Intent {
                val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = input
                }
                // Passing null title lets the system pick a sensible default ("Open with …").
                return Intent.createChooser(pick, null)
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
                if (resultCode != Activity.RESULT_OK) return null
                return intent?.data
            }
        }
    ) { uri ->
        val callback = pendingSystemFilePickerCallback
        pendingSystemFilePickerCallback = null
        callback?.invoke(uri?.toString())
    }

}