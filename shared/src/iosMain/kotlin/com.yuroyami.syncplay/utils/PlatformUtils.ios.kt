package com.yuroyami.syncplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.BasePlayer.ENGINE
import com.yuroyami.syncplay.player.vlc.VlcPlayer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.cstr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.currentLocale
import platform.Foundation.timeIntervalSince1970
import kotlin.math.roundToInt
import kotlin.math.roundToLong


actual fun instantiatePlayer(engine: BasePlayer.ENGINE) = when (engine) {
    ENGINE.IOS_AVPLAYER -> AvPlayer()
    ENGINE.IOS_VLC -> VlcPlayer()
    else -> null
}


@Composable
actual fun getSystemMaxVolume(): Int {
    // iOS doesn't expose a step count; choose a fallback value.
    return 16
}

actual val platform: PLATFORM = PLATFORM.IOS

actual fun getDefaultEngine(): String = BasePlayer.ENGINE.IOS_VLC.name

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

@OptIn(BetaInteropApi::class)
actual fun String.format(vararg args: String): String {
    // This ugly work around is because varargs can't be passed to Objective-C...
    // NSString format works with NSObjects via %@, we should change standard format to %@
    //val objcFormat = this@format.replace(Regex("%((?:\\.|\\d|\\$)*)[abcdefs]"), "%$1@")
    val f = this@format //.replace("%[\\d|.]*[sdf]|%".toRegex(), "@")
    @Suppress("MagicNumber")
    return when (args.size) {
        0 -> this //NSString.stringWithFormat(objcFormat)
        1 -> NSString.create(f, locale = NSLocale.currentLocale, args[0].cstr).toString()
        2 -> NSString.create(f, locale = NSLocale.currentLocale, args[0].cstr, args[1].cstr).toString()
        3 -> NSString.create(f,locale = NSLocale.currentLocale, args[0].cstr, args[1].cstr, args[2].cstr).toString()
        4 -> NSString.create(f, locale = null, args[0].cstr, args[1].cstr, args[2].cstr, args[3].cstr).toString()
        else -> NSString.create(f, locale = null, args[0].cstr, args[1].cstr, args[2].cstr, args[3].cstr, args[4].cstr).toString()
    }
}

actual fun ClipEntry.getText(): String? {
    return this.getPlainText()
}