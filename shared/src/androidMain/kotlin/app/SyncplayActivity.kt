package app

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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.home.InviteLink
import app.home.PendingJoin
import app.home.JoinConfig
import app.i18n.Localization
import app.player.Playback
import app.player.exo.ExoImpl
import app.preferences.Preferences.DISPLAY_LANG
import app.preferences.arePreferencesLoaded
import app.preferences.Preferences.SUBTITLE_SIZE
import app.preferences.value
import app.room.RoomViewmodel
import android.view.WindowManager
import app.utils.applyActivityUiProperties
import app.utils.bindWatchdog
import app.utils.isTelevision
import app.utils.changeLanguage
import app.utils.loggy
import app.utils.maskHiddenSystemBars
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
import android.graphics.Rect
import androidx.core.content.ContextCompat
import app.room.VideoBounds

/**
 * The only Activity of the Android app. All navigation happens inside Compose.
 */
class SyncplayActivity : ComponentActivity() {

    lateinit var globalViewmodel: SyncplayViewmodel

    val roomViewmodel: RoomViewmodel?
        get() = if (::globalViewmodel.isInitialized) globalViewmodel.roomWeakRef?.get() else null


    /**
     * Sets up the splash screen, the system bars, [platformCallback] and the Compose UI. Then it
     * handles a launcher shortcut or an invite link and asks for the notification permission.
     */
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash screen shows on cold starts only. It stays while the preferences load, so
        // no screen is drawn with defaults that are about to change.
        installSplashScreen().setKeepOnScreenCondition { !arePreferencesLoaded }

        /** Forwards the Activity lifecycle to the room's UI state. */
        bindWatchdog()

        super.onCreate(savedInstanceState)

        /** Install the crash handler early, so it catches everything after this point. */
        CrashHandler.install()

        /** Transparent system bars and an edge-to-edge layout. */
        applyActivityUiProperties()
        maskHiddenSystemBars()

        /* On a TV, the keyboard opens only when the D-pad center key asks for it. Otherwise
         * Android opens it whenever the window regains focus on a text field, such as when a
         * dialog closes. */
        if (isTelevision()) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }

        /** Connects the shared code to this platform. The callback holds this Activity weakly:
         * the callback outlives the Activity, which is recreated on a theme, locale or font-size
         * change. */
        platformCallback = AndroidPlatformCallback(WeakReference(this))

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

            /* The status bar icon color follows the theme: a light theme gets dark icons, and a
             * dark theme gets light icons. A fixed value gives white-on-white icons on a light
             * theme such as Daylight. */
            var composedViewmodel by remember { mutableStateOf<SyncplayViewmodel?>(null) }
            val activeTheme = composedViewmodel?.currentTheme?.collectAsState()?.value
            LaunchedEffect(activeTheme?.isDark) {
                WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
                    activeTheme?.isDark == false
            }

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

        // Load the localized labels for the two picture-in-picture actions once.
        lifecycleScope.launch {
            runCatching {
                pipPauseLabel = Localization.strings.roomPause
                pipPlayLabel = Localization.strings.roomPlay
            }
        }

        /** A launcher shortcut, or an invite link that someone tapped. */
        handleLaunchIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** A shortcut or link that arrives while the app is running goes to the same handler. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    /**
     * Joins from a launcher shortcut or an invite link. Both are outside input, so the room name
     * and the addresses get the same caps and trimming as the join form.
     */
    private fun handleLaunchIntent(intent: Intent?) {
        intent ?: return
        val config = when {
            intent.getBooleanExtra("quickLaunch", false) -> quickLaunchConfig(intent) ?: return
            intent.action == Intent.ACTION_VIEW -> intent.dataString?.let { InviteLink.parse(it) } ?: return
            else -> return
        }
        // Home may not exist yet at a cold start, so the join waits for it there.
        PendingJoin.post(config)
    }

    /**
     * Returns the join behind a launcher shortcut, or null when no saved shortcut matches.
     *
     * This activity is exported, so any installed app can send these extras and ask the app to
     * join a server of its choosing. The extras count only when a shortcut that this app saved
     * has exactly that configuration. Shortcut ids are per package, so no other app can plant
     * one. The fields then get the same caps as an invite link.
     */
    private fun quickLaunchConfig(intent: Intent): JoinConfig? {
        val name = intent.getStringExtra("name") ?: ""
        val room = intent.getStringExtra("room") ?: ""
        val ip = intent.getStringExtra("serverip") ?: ""
        val port = intent.getIntExtra("serverport", JoinConfig().port)

        /* Trust the saved shortcut, not the intent. The shortcut must still exist and be
         * enabled, and the join fields come from the shortcut. The caller supplied the id for
         * the lookup, so fields from the caller would make the check useless. Erasing shortcuts
         * leaves the pinned ones on the launcher, greyed out, and the isEnabled check stops a
         * tap on one of them from joining. */
        val saved = runCatching {
            ShortcutManagerCompat.getShortcuts(
                this,
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_PINNED,
            ).firstOrNull { it.id == "$name$room$ip$port" && it.isEnabled }
        }.getOrNull()

        if (saved == null) {
            loggy("Ignored a quick-launch intent that matches no enabled shortcut of ours")
            return null
        }
        val extras = saved.intent.extras ?: return null
        return InviteLink.sanitize(
            JoinConfig(
                user = extras.getString("name") ?: "",
                room = extras.getString("room") ?: "",
                ip = extras.getString("serverip") ?: "",
                port = extras.getInt("serverport", JoinConfig().port),
                pw = extras.getString("serverpw") ?: "",
            )
        )
    }

    override fun attachBaseContext(newBase: Context?) {
        /** Apply the saved language before the base context attaches, so resources load in that
         * locale. A blank choice means the device's own language, so nothing is forced. */
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

    /** Registers the picture-in-picture receiver and reapplies the subtitle size to ExoPlayer. */
    override fun onStart() {
        super.onStart()

        /* Register here, not in onResume. Entering picture-in-picture pauses the activity but
         * keeps it started, so an onResume/onPause pair would remove the receiver just when the
         * PiP window's own play and pause buttons start sending to it.
         *
         * Not exported on every API level, not only on 13 and up. Below Tiramisu, the
         * two-argument call registers an exported receiver, so any app on the device could
         * broadcast the action and pause the room. ContextCompat applies the flag there too. */
        ContextCompat.registerReceiver(
            this,
            pipBroadcastReceiver,
            IntentFilter(PIP_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        /* Reapply the saved subtitle size. */
        lifecycleScope.launch(Dispatchers.Main) {
            val room = roomViewmodel ?: return@launch
            if (!room.playerManager.isPlayerReady.value) return@launch
            val ccsize = SUBTITLE_SIZE.value()
            (room.player as? ExoImpl)?.retweakSubtitleAppearance(ccsize.toFloat())
        }
    }

    /** Copies the picture-in-picture state into the room's UI state. */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        roomViewmodel?.uiState?.hasEnteredPipMode?.value = isInPictureInPictureMode
    }

    /**
     * Enters picture-in-picture (PiP) with the current play or pause action, and hides the HUD
     * (the on-screen controls).
     *
     * The system can refuse: the device has no PiP, or the person turned it off for this app. The
     * room then stays as it was, with its controls on screen.
     */
    internal fun initiatePIPmode() {
        val ui = roomViewmodel?.uiState
        // Set first, because the lifecycle calls that follow the request read it.
        ui?.hasEnteredPipMode?.value = true

        val params = buildPiPParams(roomViewmodel?.playerManager?.isNowPlaying?.value == true)
        val entered = runCatching { enterPictureInPictureMode(params) }.getOrDefault(false)
        if (!entered) {
            ui?.hasEnteredPipMode?.value = false
            return
        }
        ui?.visibleHUD?.value = false
    }

    /** Localized labels for the PiP actions, set in onCreate. English stands in until then. */
    private var pipPauseLabel: String = "Pause"
    private var pipPlayLabel: String = "Play"

    /**
     * Builds the PiP parameters with one play or pause action. Its PendingIntent carries the
     * action (0 = pause, 1 = play), so [pipBroadcastReceiver] knows what to do.
     */
    private fun buildPiPParams(isPlaying: Boolean = false): PictureInPictureParams {

        // While playing, show a pause button (action 0). While paused, show play (action 1).
        val actionValue = if (isPlaying) 0 else 1
        // An explicit intent bound to this package. The receiver is not exported, so no other
        // app can trigger it.
        val intent = Intent(PIP_ACTION).setPackage(packageName).putExtra("pause_zero_play_one", actionValue)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 6969 + actionValue, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )

        // The labels are the room's localized words for the same two actions, so the window does
        // not switch to English when it shrinks.
        val label = if (isPlaying) pipPauseLabel else pipPlayLabel
        val action = RemoteAction(
            Icon.createWithResource(
                this,
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            ),
            label,
            label,
            pendingIntent
        )

        val hasVideo = roomViewmodel?.hasVideo?.value == true
        val builder = PictureInPictureParams.Builder()
            .setActions(if (hasVideo) listOf(action) else listOf())
        if (hasVideo) {
            // A fixed 16:9 window, so the shape does not depend on the system default.
            builder.setAspectRatio(android.util.Rational(16, 9))
            // The video's own rectangle, so the PiP window grows out of the video when it enters
            // and leaves. The room updates VideoBounds when it lays out the video layer.
            if (VideoBounds.known) {
                builder.setSourceRectHint(Rect(VideoBounds.left, VideoBounds.top, VideoBounds.right, VideoBounds.bottom))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Pressing Home while playing enters PiP, like other video apps.
                builder.setAutoEnterEnabled(isPlaying)
            }
        }
        return builder.build()
    }

    /**
     * Updates the PiP parameters to match the current playback state. It reads the engine's
     * reported state, never a live probe.
     */
    internal fun updatePiPParams() {
        val playing = roomViewmodel?.playerManager?.isNowPlaying?.value == true
        runCatching {
            setPictureInPictureParams(buildPiPParams(playing))
        }
    }

    /** Receives the PiP window's play and pause buttons ([PIP_ACTION]) and controls playback. */
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

                    // Swap the PiP button between play and pause.
                    updatePiPParams()
                }
            }
        }
    }

    /** Reapplies the track choices in the foreground, so the player does not forget them. */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val room = roomViewmodel ?: return@launch
            if (room.playerManager.isPlayerReady.value) room.player.reapplyTrackChoices()
        }
    }

    /**
     * Handles D-pad and media keys for Android TV and Google TV.
     *
     * In a room on a television, the remote's media keys control playback. On a phone or a tablet
     * the same keys come from a headset, and a headset must never change the room, so they do
     * nothing. When a video is loaded, the HUD is hidden and the screen is not locked, the D-pad
     * controls playback (left and right seek, center plays or pauses) and shows the HUD. Other
     * key events go to Compose for focus navigation.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val vm = roomViewmodel

        if (vm != null) {
            // A headset sends these keys too. Only a television remote may use them.
            if (!isTelevision() && keyCode in HEADSET_KEYS) return true
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (vm.playerManager.hasVideo.value) {
                        // The app's own intent, not a live engine probe. While buffering, a probe
                        // says "not playing", so the key would send play instead of pause.
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

            /* D-pad: only when a video is loaded, the HUD is hidden and the screen is not
             * locked. A locked screen answers only its unlock key, so every key goes to the
             * view that shows that key. */
            val hasVideo = vm.playerManager.hasVideo.value
            val hudVisible = vm.uiState.visibleHUD.value
            val locked = vm.uiState.tabLock.value

            if (hasVideo && !hudVisible && !locked) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        vm.dispatcher.controlPlayback(
                            if (vm.protocol.expectedPlaying) Playback.PAUSE else Playback.PLAY, true
                        )
                        vm.uiState.showHud()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        vm.dispatcher.seekBckwd()
                        vm.uiState.showHud()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        vm.dispatcher.seekFrwrd()
                        vm.uiState.showHud()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        vm.uiState.showHud()
                        return true
                    }
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(pipBroadcastReceiver) }
    }

    private companion object {
        const val PIP_ACTION = "app.syncplay.PIP_PLAYBACK"
    }

    /* A refusal is accepted quietly. A toast here would show on every cold start, because the
     * request runs again at each start while the permission is missing. */
    private var notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    /**
     * The caller's result callback for the system file picker. launchSystemFilePicker stores
     * `onResult` here, then launches [systemFilePickerLauncher]. When the picker returns, the
     * result handler clears this callback and calls it.
     */
    internal var pendingSystemFilePickerCallback: ((String?) -> Unit)? = null

    /**
     * The system file picker: `ACTION_GET_CONTENT` inside [Intent.createChooser]. The chooser
     * lists every installed app that registered as a content source (file managers and cloud
     * apps such as FX, MiXplorer, Solid Explorer, Drive, Dropbox or LocalSend), next to the
     * system Documents UI.
     *
     * FileKit's default picker opens the Documents UI directly (`ACTION_OPEN_DOCUMENT`), with a
     * MIME filter built from file extensions. This picker adds two things:
     *
     *  1. **SMB and cloud providers**: some report files as `application/octet-stream`, which
     *     FileKit's extension filter hides. A third-party file manager does not apply that filter.
     *  2. **User preference**: some users keep their media indexed in one file manager and want
     *     to browse there.
     *
     * An `ACTION_GET_CONTENT` grant is usually not persistable, unlike an `ACTION_OPEN_DOCUMENT`
     * one. The result handler asks for a lasting grant anyway and continues when the provider
     * refuses. The URI stays readable for this session, which is enough for immediate playback.
     * Use the FileKit picker for playlist entries that must survive a restart.
     */
    internal val systemFilePickerLauncher = registerForActivityResult(
        object : ActivityResultContract<String, Uri?>() {
            override fun createIntent(context: Context, input: String): Intent {
                val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = input
                }
                // A null title lets the system use its default title.
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
        // A GET_CONTENT grant usually cannot be kept, but some providers allow it. With those, a
        // playlist entry still opens after a restart. A provider that refuses throws, and the
        // grant still works for this session.
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        callback?.invoke(uri?.toString())
    }

}

/** The media keys that a headset can send. Outside a television they reach nothing in a room. */
private val HEADSET_KEYS = setOf(
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    KeyEvent.KEYCODE_MEDIA_PLAY,
    KeyEvent.KEYCODE_MEDIA_PAUSE,
    KeyEvent.KEYCODE_MEDIA_STOP,
    KeyEvent.KEYCODE_MEDIA_NEXT,
    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    KeyEvent.KEYCODE_MEDIA_REWIND,
    KeyEvent.KEYCODE_HEADSETHOOK,
)
