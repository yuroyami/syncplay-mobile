package app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import androidx.core.app.NotificationCompat
import SyncplayMobile.shared.BuildConfig
import app.R

class SyncplayMediaSessionService : Service() {

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "${BuildConfig.APP_NAME} Playback",
            NotificationManager.IMPORTANCE_LOW // LOW = no sound, no heads-up, just tray presence
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(BuildConfig.APP_NAME)
        .setContentText("Room active")
        .setSilent(true)
        .setOngoing(true)
        .build()

    companion object {
        const val CHANNEL_ID = "syncplay_playback"
        const val NOTIFICATION_ID = 1
    }
}