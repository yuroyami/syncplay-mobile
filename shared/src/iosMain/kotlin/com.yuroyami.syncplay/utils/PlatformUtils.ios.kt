package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ClipEntry
import cocoapods.SPLPing.SPLPing
import cocoapods.SPLPing.SPLPingConfiguration
import com.yuroyami.syncplay.delegato
import com.yuroyami.syncplay.managers.network.KtorNetworkManager
import com.yuroyami.syncplay.managers.network.NetworkManager
import com.yuroyami.syncplay.managers.network.instantiateSwiftNioNetworkManager
import com.yuroyami.syncplay.managers.player.ApplePlayerEngine
import com.yuroyami.syncplay.managers.player.PlayerEngine
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import kotlin.math.roundToInt
import kotlin.math.roundToLong

actual val platform: PLATFORM = PLATFORM.IOS

actual val availablePlatformPlayerEngines: List<PlayerEngine> = listOf(ApplePlayerEngine.AVPlayer, ApplePlayerEngine.VLC)

actual fun RoomViewmodel.instantiateNetworkManager(engine: NetworkManager.NetworkEngine) = when (engine) {
    NetworkManager.NetworkEngine.SWIFTNIO -> instantiateSwiftNioNetworkManager!!(this)
    else -> KtorNetworkManager(this)
}

@Composable
actual fun getSystemMaxVolume(): Int {
    // iOS doesn't expose a step count; choose a fallback value.
    return 16
}


actual fun generateTimestampMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000.0).roundToLong()
}

actual fun getFileName(uri: String): String? {
    return NSURL.fileURLWithPath(uri).lastPathComponent
}

actual fun getFolderName(uri: String): String? {
    return NSURL.fileURLWithPath(uri).lastPathComponent
}

actual fun getFileSize(uri: String): Long? {
    val fileAttributes = NSFileManager.defaultManager.attributesOfItemAtPath(uri, null)
    val fileSize = fileAttributes?.get(NSFileSize) as? NSNumber ?: return null
    return fileSize.longValue
}

actual suspend fun pingIcmp(host: String, packet: Int): Int? {
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
}

actual fun ClipEntry.getText(): String? {
    return this.getPlainText()
}

@Composable
actual fun HideSystemBars() {
    LaunchedEffect(null) {
        delegato.myOrientationMask = UIInterfaceOrientationMaskLandscape
        UIApplication.sharedApplication.connectedScenes.firstOrNull()?.let {
            (it as? UIWindowScene)?.apply {
                requestGeometryUpdateWithPreferences(
                    geometryPreferences = UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = UIInterfaceOrientationMaskLandscape),
                    errorHandler = null
                )
            }
        }
    }
}

@Composable
actual fun ShowSystemBars() {
    LaunchedEffect(null) {
        delegato.myOrientationMask = UIInterfaceOrientationMaskAll
        UIApplication.sharedApplication.connectedScenes.firstOrNull()?.let {
            (it as? UIWindowScene)?.apply {
                requestGeometryUpdateWithPreferences(
                    geometryPreferences = UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = UIInterfaceOrientationMaskAll),
                    errorHandler = null
                )
            }
        }
    }
}

