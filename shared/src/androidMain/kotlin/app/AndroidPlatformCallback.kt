package app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.home.HomeViewmodel
import app.player.SyncplayMediaSessionService
import app.server.SyncplayServerService
import app.home.JoinConfig
import app.utils.appName
import app.utils.contextObtainer
import app.utils.loggy
import java.lang.ref.WeakReference

/**
 * The Android side of [PlatformCallback], holding the Activity weakly.
 *
 * `platformCallback` is a process-wide reference, and the Activity is recreated on a theme,
 * locale or font-size change (those are not in its configChanges list). A strong reference kept
 * the dead Activity alive for the life of the process and, worse, kept sending it work: a
 * brightness change or a file picker would target a window that is gone. Every call resolves the
 * live Activity or quietly does nothing.
 */
internal class AndroidPlatformCallback(
    private val ref: WeakReference<SyncplayActivity>,
) : PlatformCallback {

    private val activity: SyncplayActivity? get() = ref.get()

    /** Services and system settings belong to the process, not to one window. */
    private val appContext: Context get() = contextObtainer().applicationContext

    private val audioManager by lazy { appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun mediaSessionInitialize() {
        val a = activity ?: return
        a.startForegroundService(Intent(a, SyncplayMediaSessionService::class.java))
    }

    override fun mediaSessionFinalize() {
        val a = activity ?: return
        a.stopService(Intent(a, SyncplayMediaSessionService::class.java))
    }

    override fun serverServiceStart(port: Int) {
        val a = activity ?: return
        a.startForegroundService(
            Intent(a, SyncplayServerService::class.java).apply {
                putExtra(SyncplayServerService.EXTRA_PORT, port)
            }
        )
    }

    override fun serverServiceStop() {
        val a = activity ?: return
        a.stopService(Intent(a, SyncplayServerService::class.java))
    }

    override fun serverClientsChanged(port: Int, clients: Int) {
        val a = activity ?: return
        // The same start intent updates the running notification's client count.
        val intent = Intent(a, SyncplayServerService::class.java).apply {
            putExtra(SyncplayServerService.EXTRA_PORT, port)
            putExtra(SyncplayServerService.EXTRA_CLIENTS, clients)
        }
        runCatching { a.startForegroundService(intent) }
    }

    /** Recreates the activity to apply the new language. */
    override fun onLanguageChanged(newLang: String) {
        val a = activity ?: return
        a.runOnUiThread { a.recreate() }
    }

    /**
     * Creates a pinned home screen shortcut and dynamic shortcut for quick room access, with the
     * room configuration in the intent extras.
     */
    override fun HomeViewmodel.onSaveConfigShortcut(joinInfo: JoinConfig) {
        val a = activity ?: return
        val name = joinInfo.user.trim()
        val room = joinInfo.room.trim()
        val ip = joinInfo.ip.trim()
        val shortcutIntent = Intent(a, SyncplayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            action = Intent.ACTION_MAIN
            putExtra("quickLaunch", true)
            putExtra("name", name)
            putExtra("room", room)
            putExtra("serverip", ip)
            putExtra("serverport", joinInfo.port)
            putExtra("serverpw", joinInfo.pw)
        }

        // Built from exactly what the extras carry: the launch path recomputes this id from the
        // incoming extras and refuses anything that matches no shortcut of ours.
        val shortcutId = "$name$room$ip${joinInfo.port}"
        val shortcutInfo = ShortcutInfoCompat.Builder(a, shortcutId)
            .setShortLabel(joinInfo.room)
            // The app module owns the launcher icon, so it is taken from the manifest rather than
            // from this module's own resources, which held a shadowed second copy.
            .setIcon(IconCompat.createWithResource(a, a.applicationInfo.icon))
            .setIntent(shortcutIntent)
            .build()

        ShortcutManagerCompat.addDynamicShortcuts(a, listOf(shortcutInfo))

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(a)) {
            ShortcutManagerCompat.requestPinShortcut(a, shortcutInfo, null)
        }
    }

    /** Removes all dynamic shortcuts created for room configurations. */
    override fun onEraseConfigShortcuts() {
        val a = activity ?: return
        ShortcutManagerCompat.removeAllDynamicShortcuts(a)
        // Pinned copies live on the launcher and keep the room password; they can only
        // be disabled, which is what stops them from ever joining again.
        val pinned = ShortcutManagerCompat.getShortcuts(a, ShortcutManagerCompat.FLAG_MATCH_PINNED)
        if (pinned.isNotEmpty()) {
            ShortcutManagerCompat.disableShortcuts(a, pinned.map { it.id }, null)
        }
    }

    override fun deviceVolumeSteps(): Int =
        if (audioManager.isVolumeFixed) 0 else audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    override fun getDeviceVolume(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun setDeviceVolume(step: Int) {
        if (!audioManager.isVolumeFixed) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
    }

    override fun getMaxBrightness() = 1f

    /**
     * The current screen brightness, from this window's override when it has one and from system
     * settings otherwise. Automatic mode reports an unreliable raw value, so it answers the middle.
     */
    override fun getCurrentBrightness(): Float {
        val a = activity ?: return 0.5f
        val brightness = a.window.attributes.screenBrightness
        if (brightness != -1f) return brightness

        val automatic = Settings.System.getInt(
            a.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

        return if (automatic) 0.5f
        else Settings.System.getInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128).toFloat() / 255
    }

    /** @param v Brightness value between 0.0 (darkest) and 1.0 (brightest). */
    override fun changeCurrentBrightness(v: Float) {
        val a = activity ?: return
        loggy("Brightness: $v")
        a.window.attributes = a.window.attributes.apply { screenBrightness = v.coerceIn(0f, 1f) }
    }

    /** Updates Picture-in-Picture controls when playback state changes. */
    override fun onPlayback(paused: Boolean) {
        activity?.updatePiPParams()
    }

    override fun onPictureInPicture(enable: Boolean) {
        if (enable) activity?.initiatePIPmode()
    }

    override fun performHapticFeedback() {
        val vibrator = appContext.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun copyText(text: String) {
        appContext.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(appName, text))
    }

    override fun shareText(text: String) {
        val a = activity ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        a.startActivity(Intent.createChooser(send, null))
    }

    /**
     * Opens the system chooser, which lets the user browse with any installed file manager. This
     * is what reaches files FileKit's extension filter hides, such as SMB-backed media for mpv.
     */
    override fun launchSystemFilePicker(onResult: (String?) -> Unit) {
        val a = activity ?: return onResult(null)
        a.pendingSystemFilePickerCallback = onResult
        runCatching {
            a.systemFilePickerLauncher.launch("*/*")
        }.onFailure {
            a.pendingSystemFilePickerCallback = null
            onResult(null)
        }
    }
}
