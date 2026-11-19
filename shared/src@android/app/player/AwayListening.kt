package app.player

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.room.HoldReason
import app.room.RoomViewmodel
import app.utils.contextObtainer
import app.utils.loggy

/**
 * What Android needs while a room plays its sound away from the screen (#125): the CPU and Wi-Fi
 * stay awake, the room holds the audio focus, and lost headphones stop the sound on this phone.
 *
 * Each of these only ever pauses this phone, through a hold. The room is never told: a call in
 * one pocket must not stop the shared screen. One room listens at a time, so this is one object.
 */
object AwayListening {

    private var viewmodel: RoomViewmodel? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var noisyRegistered = false
    private val main = Handler(Looper.getMainLooper())

    /** The headphones went away. The sound must not move to the speaker of a phone in a pocket. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) viewmodel?.uiState?.hold(HoldReason.Route)
        }
    }

    /**
     * A transient loss (a call, a voice assistant) holds until the focus comes back. A permanent
     * loss (another app plays) holds until the user returns to the room. The system ducks on its own.
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val ui = viewmodel?.uiState ?: return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> ui.hold(HoldReason.AudioFocus)
            AudioManager.AUDIOFOCUS_LOSS -> {
                ui.hold(HoldReason.AudioFocus)
                abandonFocus(runCatching { contextObtainer().applicationContext }.getOrNull())
            }
            AudioManager.AUDIOFOCUS_GAIN -> ui.release(HoldReason.AudioFocus)
        }
    }

    /** Starts ([active]) or ends the background sound of [room]. Safe to call twice. */
    fun update(context: Context, room: RoomViewmodel, active: Boolean) = main.post {
        val app = context.applicationContext
        if (active) start(app, room) else stop(app, room)
    }

    private fun start(context: Context, room: RoomViewmodel) {
        viewmodel = room
        acquireLocks(context)
        if (!noisyRegistered) {
            ContextCompat.registerReceiver(
                context, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyRegistered = true
        }
        if (focusRequest == null) requestFocus(context)
    }

    private fun stop(context: Context, room: RoomViewmodel) {
        if (viewmodel != null && viewmodel !== room) return
        releaseLocks()
        if (noisyRegistered) {
            runCatching { context.unregisterReceiver(noisyReceiver) }
            noisyRegistered = false
        }
        // A short loss keeps its request, so the system can hand the focus back while the room is away.
        val waitingForFocus = room.uiState.isAway && room.uiState.holds.has(HoldReason.AudioFocus)
        if (!waitingForFocus) {
            abandonFocus(context)
            viewmodel = null
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks(context: Context) {
        if (wakeLock == null) {
            wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "synkplay:listen-away")
                .apply { setReferenceCounted(false); acquire() }
        }
        if (wifiLock == null) {
            @Suppress("DEPRECATION") // The only Wi-Fi lock mode that applies with the screen off.
            wifiLock = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "synkplay:listen-away")
                .apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
    }

    private fun requestFocus(context: Context) {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener, main)
            .build()
        val result = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).requestAudioFocus(request)
        focusRequest = request
        // Refused, for example during a call: the sound would play into the call, so hold until it ends.
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            loggy("Audio focus refused while away: $result")
            viewmodel?.uiState?.hold(HoldReason.AudioFocus)
        }
    }

    private fun abandonFocus(context: Context?) {
        val request = focusRequest ?: return
        focusRequest = null
        (context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocusRequest(request)
    }
}
