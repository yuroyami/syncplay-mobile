package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import co.touchlab.kermit.Logger
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.viewmodel.PlatformCallback
import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel


lateinit var platformCallback: PlatformCallback

@Composable
expect fun getSystemMaxVolume(): Int

/** Retrieving which platform the native code is running on */
enum class PLATFORM { Android, IOS, }
expect val platform: PLATFORM

/** logging functionality (Uses println on iOS, and Log.e on Android) */
fun loggy(s: String?, checkpoint: Int = 0) = Logger.e(s.toString())

/** Gets the default video playback engine on each platform (mpv on Android, AVPlayer on iOS) */
expect fun getDefaultEngine(): String
expect fun SyncplayViewmodel.instantiatePlayer(engine: BasePlayer.ENGINE): BasePlayer?

expect fun SyncplayViewmodel.instantiateNetworkEngineProtocol(engine: SyncplayProtocol.NetworkEngine): SyncplayProtocol

/** Generates the system's current Epoch millis */
expect fun generateTimestampMillis(): Long

/** Converts seconds into a readable hh:mm:ss format */
fun timeStamper(seconds: Number): String {
    val secs = seconds.toLong()
    return if (secs < 3600) {
        "${(secs / 60) % 60}:${(secs % 60).toString().padStart(2, '0')}".padStart(5, '0')
    } else {
        "${secs / 3600}:${((secs / 60) % 60).toString().padStart(2, '0')}:${(secs % 60).toString().padStart(2, '0')}".padStart(8, '0')
    }
}

/** Gets the filename based on its URI, needs context on Android */
expect fun getFileName(uri: String): String?

/** Gets the folder name based on its URI, needs context on Android */
expect fun getFolderName(uri: String): String?

/** Pings a host once and retrieves the round trip time (RTT) */
expect suspend fun pingIcmp(host: String, packet: Int): Int?

/** Gets the screen size info */
data class ScreenSizeInfo(val heightPx: Int, val widthPx: Int) {
    @Composable
    fun heightDp(): Dp = with(LocalDensity.current) { heightPx.toDp() }

    @Composable
    fun widthDp(): Dp = with(LocalDensity.current) { widthPx.toDp() }
}
expect fun ClipEntry.getText(): String?