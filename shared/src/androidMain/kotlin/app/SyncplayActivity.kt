package app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.media.AudioManager
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
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
import app.home.HomeViewmodel
import app.home.InviteLink
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
import app.utils.maskTransientBarAnimations
import app.utils.platformCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import java.lang.ref.WeakReference

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

        /** Tweaking window UI decor (transparent system bars, edge-to-edge) */
        applyActivityUiProperties()
        maskTransientBarAnimations()

        /** Binding common logic with platform logic. Held weakly: the callback outlives this
         * Activity, which is recreated on a theme, locale or font-size change. */
        platformCallback = AndroidPlatformCallback(WeakReference(this))

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

            /* Status bar icon color follows the theme: a light theme gets dark icons and the
             * reverse. The old hardcoded `false` left white-on-white icons on Daylight. */
            var composedViewmodel by remember { mutableStateOf<SyncplayViewmodel?>(null) }
            val activeTheme = composedViewmodel?.currentTheme?.collectAsState()?.value
            LaunchedEffect(activeTheme?.isDark) {
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
                    activeTheme?.isDark == false
            }

            //MainUI
            Box {
                AdamScreen(
                    onGlobalViewmodel = {
                        globalViewmodel = it
                        composedViewmodel = it
                    }
                )

                CrashOverlay()
            }
        }

        /** A shortcut, or an invite link someone tapped */
        handleLaunchIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Applies the saved language before the base context is attached.
     *
     * This ensures the correct locale is used when inflating resources.
     */
    /** A link that arrives while the app is already running reaches the same handler. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    /**
     * Joins from a launcher shortcut or an invite link. Both are outside input: the room name and
     * the addresses go through the same caps and trimming the join form applies.
     */
    private fun handleLaunchIntent(intent: Intent?) {
        intent ?: return
        val config = when {
            intent.getBooleanExtra("quickLaunch", false) -> quickLaunchConfig(intent) ?: return
            intent.action == Intent.ACTION_VIEW -> intent.dataString?.let { InviteLink.parse(it) } ?: return
            else -> return
        }
        lifecycleScope.launch {
            homeViewmodel?.joinRoom(config)
        }
    }

    /**
     * The join behind a launcher shortcut.
     *
     * This activity is exported, so any installed app can send these extras and ask us to join a
     * server of its choosing. They are only honoured when a shortcut we ourselves saved carries
     * exactly that configuration: shortcut ids are per-package, so nobody else can plant one. The
     * fields then go through the same caps as an invite link.
     */
    private fun quickLaunchConfig(intent: Intent): JoinConfig? {
        val name = intent.getStringExtra("name") ?: ""
        val room = intent.getStringExtra("room") ?: ""
        val ip = intent.getStringExtra("serverip") ?: ""
        val port = intent.getIntExtra("serverport", JoinConfig().port)

        val ours = runCatching {
            ShortcutManagerCompat.getShortcuts(
                this,
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_PINNED,
            ).any { it.id == "$name$room$ip$port" }
        }.getOrDefault(false)

        if (!ours) {
            loggy("Ignored a quick-launch intent that matches no shortcut of ours")
            return null
        }
        return InviteLink.sanitize(
            JoinConfig(user = name, room = room, ip = ip, port = port, pw = intent.getStringExtra("serverpw") ?: "")
        )
    }

    override fun attachBaseContext(newBase: Context?) {
        /** Applying the saved language; blank means the device's own, so nothing is forced. */
        val lang = runCatching { DISPLAY_LANG.value() }.getOrDefault(DISPLAY_LANG.default)
        super.attachBaseContext(if (lang.isBlank()) newBase else newBase!!.changeLanguage(lang))
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Reapply a chosen locale after orientation changes; a blank choice follows the device.
        // TODO: migrate to AppCompatDelegate.setApplicationLocales for per-app language on Android 13+.
        val lang = DISPLAY_LANG.value()
        if (lang.isBlank()) return
        val locale = Locale.Builder().setLanguage(lang).build()
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
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
    internal fun initiatePIPmode() {
        roomViewmodel?.uiState?.hasEnteredPipMode?.value = true

        val params = buildPiPParams(roomViewmodel?.playerManager?.isNowPlaying?.value == true)
        runCatching {
            enterPictureInPictureMode(params)
        }
        roomViewmodel?.uiState?.visibleHUD?.value = false
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
        // Explicit and package-bound: the receiver is not exported, so no other app can press it.
        val intent = Intent(PIP_ACTION).setPackage(packageName).putExtra("pause_zero_play_one", actionValue)
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

        val hasVideo = roomViewmodel?.hasVideo?.value == true
        val builder = PictureInPictureParams.Builder()
            .setActions(if (hasVideo) listOf(action) else listOf())
        if (hasVideo) {
            // A video-shaped window instead of the system's square default.
            builder.setAspectRatio(android.util.Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Home while playing keeps the picture on screen, the way video apps do.
                builder.setAutoEnterEnabled(isPlaying)
            }
        }
        return builder.build()
    }

    /**
     * Updates the PiP parameters on the activity to reflect current playback state. Reads the
     * engine's reported state, never a live probe (rule 5 of the ledger).
     */
    internal fun updatePiPParams() {
        val playing = roomViewmodel?.playerManager?.isNowPlaying?.value == true
        runCatching {
            setPictureInPictureParams(buildPiPParams(playing))
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
                if (intnt.action == PIP_ACTION) {
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
        val filter = IntentFilter(PIP_ACTION)
        // Not exported: only our own PendingIntent (explicit, package-bound) may pause the room.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
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
                    if (vm.playerManager.hasVideo.value) {
                        // The app's own intent, not a live engine probe: mid-buffer a probe says
                        // "not playing" and the key would unpause a room that just paused.
                        vm.dispatcher.controlPlayback(
                            if (vm.protocol.expectedPlaying) Playback.PAUSE else Playback.PLAY, true
                        )
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
                        vm.dispatcher.controlPlayback(
                            if (vm.protocol.expectedPlaying) Playback.PAUSE else Playback.PLAY, true
                        )
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

    private companion object {
        const val PIP_ACTION = "app.syncplay.PIP_PLAYBACK"
    }

    /* A refusal is respected quietly: the old toast fired on every cold start, in English, and
     * promised playback controls the notification does not carry. */
    private var notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    /**
     * Callback indirection for the "custom picker" (chooser-of-file-managers) flow. The
     * PlatformCallback override stores the caller's `onResult` here, then launches
     * [systemFilePickerLauncher]. When the picker returns, the result handler invokes and
     * clears this callback.
     */
    internal var pendingSystemFilePickerCallback: ((String?) -> Unit)? = null

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
    internal val systemFilePickerLauncher = registerForActivityResult(
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