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
import com.yuroyami.syncplay.managers.player.AppleVideoEngine
import com.yuroyami.syncplay.managers.player.VideoEngine
import com.yuroyami.syncplay.managers.preferences.Preferences.NETWORK_ENGINE
import com.yuroyami.syncplay.managers.preferences.value
import com.yuroyami.syncplay.viewmodels.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
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
import kotlin.native.ref.WeakReference

actual val platform: PLATFORM = PLATFORM.IOS

actual val availablePlatformVideoEngines: List<VideoEngine> = listOf(AppleVideoEngine.AVPlayer, AppleVideoEngine.VLC)

actual typealias GlobalPlayerSession = String

actual fun RoomViewmodel.instantiateNetworkManager(): NetworkManager {
    val preferredEngine = NETWORK_ENGINE.value()
    return when (preferredEngine) {
        "swiftnio" -> instantiateSwiftNioNetworkManager!!(this)
        else -> KtorNetworkManager(this)
    }
}

actual fun generateTimestampMillis(): Long {
    return (NSDate().timeIntervalSince1970 * 1000.0).roundToLong()
}

actual fun getFileName(uri: PlatformFile): String? {
    return uri.nsUrl.lastPathComponent
}

actual fun getFolderName(uri: String): String? {
    return NSURL.fileURLWithPath(uri).lastPathComponent
}

actual fun getFileSize(uri: PlatformFile): Long? {
    return uri.nsUrl.accessSecurely {
        val fileManager = NSFileManager.defaultManager
        val fileAttributes = fileManager.attributesOfItemAtPath(uri.path, null) ?: return null

        val fileSize = fileAttributes[NSFileSize] as? NSNumber
        val fileSizeFallback = fileAttributes["NSFileSize"] as? NSNumber

        fileSize?.longLongValue ?: fileSizeFallback?.longLongValue ?: fileSize?.longValue ?: fileSizeFallback?.longValue
    }
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

actual typealias WeakRef<T> = WeakReference<T>
actual fun <T : Any> createWeakRef(obj: T): WeakRef<T> {
    return WeakReference(obj)
}
actual fun <T : Any> WeakRef<T>?.get(): T? = this?.get()