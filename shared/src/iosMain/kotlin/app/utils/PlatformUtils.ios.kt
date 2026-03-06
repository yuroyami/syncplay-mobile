package app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ClipEntry
import app.delegato
import app.protocol.network.instantiateSwiftNioNetworkManager
import app.player.avplayer.AVPlayerEngine
import app.player.vlc.VlcKitEngine
import app.player.PlayerEngine
import app.preferences.Preferences.NETWORK_ENGINE
import app.preferences.value
import app.protocol.network.KtorNetworkManager
import app.protocol.network.NetworkManager
import app.room.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
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
import kotlin.math.roundToLong
import kotlin.native.ref.WeakReference

actual val platform: PLATFORM = PLATFORM.IOS

actual val availablePlatformPlayerEngines: List<PlayerEngine> = listOf(AVPlayerEngine, VlcKitEngine)

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