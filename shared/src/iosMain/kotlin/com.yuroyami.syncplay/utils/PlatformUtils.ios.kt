package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry
import com.yuroyami.syncplay.SyncplayViewmodel
import com.yuroyami.syncplay.managers.NetworkManager
import com.yuroyami.syncplay.managers.network.KtorNetworkManager
import com.yuroyami.syncplay.managers.player.ApplePlayerEngine
import com.yuroyami.syncplay.managers.player.PlayerEngine
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import kotlin.math.roundToLong

actual fun SyncplayViewmodel.instantiateNetworkManager(engine: NetworkManager.NetworkEngine) = when (engine) {
    NetworkManager.NetworkEngine.SWIFTNIO -> instantiateSwiftNioNetworkManager!!.invoke()
    else -> KtorNetworkManager(this)
}

@Composable
actual fun getSystemMaxVolume(): Int {
    // iOS doesn't expose a step count; choose a fallback value.
    return 16
}

actual val platform: PLATFORM = PLATFORM.IOS

actual fun generateTimestampMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000.0).roundToLong()
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

actual val availablePlatformPlayerEngines: List<PlayerEngine> = listOf(ApplePlayerEngine.AVPlayer, ApplePlayerEngine.VLC)