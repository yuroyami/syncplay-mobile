package com.yuroyami.syncplay.utils

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.UUID
import androidx.media3.common.Player as Media3Player

fun Media3Player.buildAndroidMediaSession(ctx: Context): GlobalPlayerSession {
    return MediaSession
        .Builder(ctx, this)
        .setId("session_${UUID.randomUUID()}")
        .setCallback(mediaSessionCallback)
        .setId(ctx.packageName)
        .run {
            ctx.packageManager?.getLaunchIntentForPackage(ctx.packageName)?.let { sessionIntent ->
                setSessionActivity(
                    PendingIntent.getActivity(
                        /* context= */ ctx,
                        /* requestCode= */ 0,
                        sessionIntent,
                        FLAG_IMMUTABLE
                    )
                )
            } ?: this
        }
        .build()
}


val mediaSessionCallback: MediaSession.Callback
    get() = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val newMediaItems = mediaItems.map {
                it.buildUpon().setUri(it.mediaId).build()
            }.toMutableList()
            return Futures.immediateFuture(newMediaItems)
        }
    }