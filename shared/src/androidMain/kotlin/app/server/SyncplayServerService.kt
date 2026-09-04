package app.server

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
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.server_notification_channel_description
import syncplaymobile.shared.generated.resources.server_notification_title
import syncplaymobile.shared.generated.resources.server_notification_text

/**
 * Android foreground service for keeping the Syncplay server alive when the app is backgrounded.
 *
 * Follows the same pattern as [app.player.SyncplayMediaSessionService] but with a separate
 * notification channel. The actual server logic runs in [ServerHostSession]'s process-lifetime
 * coroutine scope; this service only provides the foreground notification that prevents
 * Android from killing the process.
 */
class SyncplayServerService : Service() {

    /** Only for resolving the notification's own strings; cancelled with the service. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, 8999) ?: 8999
        val clients = intent?.getIntExtra(EXTRA_CLIENTS, 0) ?: 0

        createNotificationChannel()
        // Foreground first with a title the app already knows, then the localized line as soon as
        // the resource loader answers: the deadline for startForeground is measured in seconds and
        // must not wait on anything.
        startForeground(NOTIFICATION_ID, buildNotification(text = null))
        scope.launch {
            val text = runCatching {
                getPluralString(Res.plurals.server_notification_text, clients, port, clients)
            }.getOrNull() ?: return@launch
            runCatching {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
        // NOT sticky: the server itself lives in ServerHostSession's in-process memory. If the
        // process dies, a sticky restart would only resurrect a notification with no server
        // behind it.
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            title,
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        scope.launch {
            val described = runCatching { getString(Res.string.server_notification_channel_description) }.getOrNull()
            title = runCatching { getString(Res.string.server_notification_title, appName) }.getOrNull() ?: title
            if (described != null) {
                channel.description = described
                runCatching { getSystemService(NotificationManager::class.java).createNotificationChannel(channel) }
            }
        }
    }

    /**
     * The app's own name plus the word for what this is. Resolved through the loader like the rest
     * of the text, with the plain app name standing in until it answers.
     */
    private var title: String = appName

    private fun buildNotification(text: String?) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .apply { if (text != null) setContentText(text) }
        .setSilent(true)
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "syncplay_server"
        const val NOTIFICATION_ID = 2
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_CLIENTS = "extra_clients"
    }
}
