package app.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import SyncplayMobile.shared.BuildConfig
import app.R

/**
 * Android foreground service for keeping the Syncplay server alive when the app is backgrounded.
 *
 * Follows the same pattern as [app.player.SyncplayMediaSessionService] but with a separate
 * notification channel. The actual server logic runs in [ServerViewmodel]'s coroutine scope;
 * this service only provides the foreground notification that prevents Android from killing
 * the process.
 */
class SyncplayServerService : Service() {

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, 8999) ?: 8999
        val clients = intent?.getIntExtra(EXTRA_CLIENTS, 0) ?: 0

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(port, clients))
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "${BuildConfig.APP_NAME} Server",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Shows when a Syncplay server is running"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(port: Int, clients: Int) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("${BuildConfig.APP_NAME} Server")
        .setContentText("Running on port $port - $clients client(s)")
        .setSilent(true)
        .setOngoing(true)
        .build()

    companion object {
        const val CHANNEL_ID = "syncplay_server"
        const val NOTIFICATION_ID = 2
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_CLIENTS = "extra_clients"
    }
}
