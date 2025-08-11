package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import co.touchlab.kermit.Logger
import com.yuroyami.syncplay.player.PlayerEngine
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.viewmodel.PlatformCallback
import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel


lateinit var platformCallback: PlatformCallback

@Composable
expect fun getSystemMaxVolume(): Int

/** Retrieving which platform the native code is running on */
enum class PLATFORM { Android, IOS, }
expect val platform: PLATFORM

expect val availablePlatformPlayerEngines: List<PlayerEngine>

/** logging functionality (Uses println on iOS, and Log.e on Android) */
fun loggy(s: Any?) {
    Logger.e(
        if (s is Exception) {
            s.stackTraceToString()
        } else {
            s.toString()
        }
    )

}

expect fun SyncplayViewmodel.instantiateNetworkEngineProtocol(engine: SyncplayProtocol.NetworkEngine): SyncplayProtocol

/** Generates the system's current Epoch millis */
expect fun generateTimestampMillis(): Long

/** Converts seconds into a readable hh:mm:ss format */
fun timeStamper(milliseconds: Number): String {
    val secs = (milliseconds.toLong() / 1000L)
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

expect fun ClipEntry.getText(): String?

@Composable
expect fun HideSystemBars()