package app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.R
import app.utils.appName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_notification_text

class SyncplayMediaSessionService : Service() {

    /** Only for resolving the notification's own string; cancelled with the service. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        // Foreground first, then the localized line: startForeground has a deadline of its own and
        // must not wait on the resource loader.
        startForeground(NOTIFICATION_ID, buildNotification(text = null))
        scope.launch {
            val text = runCatching { getString(Res.string.room_notification_text) }.getOrNull() ?: return@launch
            runCatching {
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
        // Not sticky: a restart after process death would only resurrect a notification with no
        // room behind it.
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "${appName} Playback",
            NotificationManager.IMPORTANCE_LOW // LOW = no sound, no heads-up, just tray presence
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String?) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(appName)
        .apply { if (text != null) setContentText(text) }
        .setSilent(true)
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "syncplay_playback"
        const val NOTIFICATION_ID = 1
    }
}