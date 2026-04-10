package app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ClipEntry
import app.delegato
import app.player.PlayerEngine
import app.player.avplayer.AVPlayerEngine
import app.player.mpv.MpvKitEngine
import app.player.mpv.instantiateMpvKitPlayer
import app.player.vlc.VlcKitEngine
import app.preferences.Preferences.NETWORK_ENGINE
import app.preferences.value
import app.protocol.network.KtorNetworkManager
import app.protocol.network.NetworkManager
import app.protocol.network.instantiateSwiftNioNetworkManager
import app.room.RoomViewmodel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeData
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import kotlin.math.roundToLong
import kotlinx.cinterop.toKString
import platform.ifaddrs.getDeviceLocalIp
import kotlin.native.ref.WeakReference

actual val platform: Platform = Platform.IOS

actual val availablePlatformPlayerEngines: List<PlayerEngine> = buildList {
    add(AVPlayerEngine)
    add(VlcKitEngine)
}

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
        delegato.myOrientationMask = UIInterfaceOrientationMaskPortrait
        UIApplication.sharedApplication.connectedScenes.firstOrNull()?.let {
            (it as? UIWindowScene)?.apply {
                requestGeometryUpdateWithPreferences(
                    geometryPreferences = UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = UIInterfaceOrientationMaskPortrait),
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

actual fun getDeviceIpAddress(): String? {
    return try {
        getDeviceLocalIp()?.toKString()
    } catch (_: Exception) {
        null
    }
}

actual fun getLogDirectoryPath(): String? {
    return try {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val docDir = paths.firstOrNull() as? String ?: return null
        val logDir = "$docDir/logs"
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(logDir)) {
            fm.createDirectoryAtPath(logDir, withIntermediateDirectories = true, attributes = null, error = null)
        }
        logDir
    } catch (_: Exception) {
        null
    }
}

actual fun appendToFile(path: String, content: String) {
    try {
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(path)) {
            fm.createFileAtPath(path, contents = null, attributes = null)
        }
        val handle = NSFileHandle.fileHandleForWritingAtPath(path) ?: return
        handle.seekToEndOfFile()
        val nsString = NSString.create(string = content)
        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return
        handle.writeData(data)
        handle.closeFile()
    } catch (_: Exception) { }
}

actual fun listFiles(directoryPath: String): List<String> {
    return try {
        val fm = NSFileManager.defaultManager
        (fm.contentsOfDirectoryAtPath(directoryPath, error = null) as? List<*>)
            ?.filterIsInstance<String>() ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

actual fun deleteFile(path: String) {
    try {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    } catch (_: Exception) { }
}

actual fun consumePendingShortcut(): app.home.JoinConfig? {
    return app.pendingShortcutJoinConfig.value?.also {
        app.pendingShortcutJoinConfig.value = null
    }
}