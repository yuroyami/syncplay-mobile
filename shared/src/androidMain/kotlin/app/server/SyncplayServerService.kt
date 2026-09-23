package app.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.R
import app.i18n.Localization
import app.i18n.serverNotificationText
import app.utils.appName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * An Android foreground service that keeps the built-in Syncplay server (which lets this device
 * host rooms) alive while the app is in the background.
 *
 * The server itself runs in [ServerHostSession]'s process-lifetime coroutine scope. This service
 * only shows the foreground notification that stops Android from killing the process.
 */
class SyncplayServerService : Service() {

    /** Only for loading the notification's localized strings. Cancelled with the service. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, 8999) ?: 8999
        val clients = intent?.getIntExtra(EXTRA_CLIENTS, 0) ?: 0

        createNotificationChannel()
        // Go foreground first, with a title the app already has, and add the localized line when
        // the resource loader answers. The startForeground deadline is a few seconds, so it must
        // not wait on anything.
        startForeground(NOTIFICATION_ID, buildNotification(text = null))
        scope.launch {
            val text = runCatching {
                Localization.strings.serverNotificationText(port, clients)
            }.getOrNull() ?: return@launch
            runCatching {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
        // NOT sticky: the server lives in ServerHostSession's memory in this process. If the
        // process dies, a sticky restart would only bring back a notification with no server
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
            val described = runCatching { Localization.strings.serverNotificationChannelDescription }.getOrNull()
            title = runCatching { Localization.strings.serverNotificationTitle(appName) }.getOrNull() ?: title
            if (described != null) {
                channel.description = described
                runCatching { getSystemService(NotificationManager::class.java).createNotificationChannel(channel) }
            }
        }
    }

    /**
     * The notification title: the app name plus the word for a server. It comes from the resource
     * loader like the rest of the text, and the plain app name stands in until the loader answers.
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
