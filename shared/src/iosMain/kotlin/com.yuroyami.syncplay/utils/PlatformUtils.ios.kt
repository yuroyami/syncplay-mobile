package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry
import com.yuroyami.syncplay.player.BasePlayer.ENGINE
import com.yuroyami.syncplay.player.avplayer.AvPlayer
import com.yuroyami.syncplay.player.vlc.VlcPlayer
import com.yuroyami.syncplay.protocol.SpProtocolKtor
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import kotlin.math.roundToLong

actual fun SyncplayViewmodel.instantiatePlayer(engine: ENGINE) = when (engine) {
    ENGINE.IOS_AVPLAYER -> AvPlayer(this)
    ENGINE.IOS_VLC -> VlcPlayer(this)
    else -> null
}

actual fun SyncplayViewmodel.instantiateNetworkEngineProtocol(engine: SyncplayProtocol.NetworkEngine) = when (engine) {
    SyncplayProtocol.NetworkEngine.SWIFTNIO -> instantiateSyncplayProtocolSwiftNIO!!.invoke()
    else -> SpProtocolKtor(this)
}

@Composable
actual fun getSystemMaxVolume(): Int {
    // iOS doesn't expose a step count; choose a fallback value.
    return 16
}

actual val platform: PLATFORM = PLATFORM.IOS

actual fun getDefaultEngine(): String = ENGINE.IOS_VLC.name

actual fun generateTimestampMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000).roundToLong()
}

actual fun getFileName(uri: String): String? {
    return NSURL.fileURLWithPath(uri).lastPathComponent
}

actual fun getFolderName(uri: String): String? {
    return NSURL.fileURLWithPath(uri).lastPathComponent
}

actual suspend fun pingIcmp(host: String, packet: Int): Int? {
    return 69
    /* TODO
    val future = CompletableDeferred<Int>()
    SPLPing.pingOnce(
        host = host,
        configuration = SPLPingConfiguration(
            pingInterval = 1000.0, timeoutInterval = 1000.0, timeToLive = 1000L, payloadSize = packet.toULong()
        )
    ) {
        it?.let { response ->
            future.complete(
                (response.duration * 1000.0).roundToInt()
            )
        }
    }
    return withTimeoutOrNull(1000) { future.await() }

     */
}

actual fun ClipEntry.getText(): String? {
    return this.getPlainText()
}