package com.yuroyami.syncplay.utils

import android.content.ContentResolver
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yuroyami.syncplay.BuildConfig
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.BasePlayer.ENGINE
import com.yuroyami.syncplay.player.exo.ExoPlayer
import com.yuroyami.syncplay.player.mpv.MpvPlayer
import com.yuroyami.syncplay.player.vlc.VlcPlayer
import com.yuroyami.syncplay.protocol.SpProtocolAndroid
import com.yuroyami.syncplay.protocol.SpProtocolKtor
import com.yuroyami.syncplay.protocol.SyncplayProtocol
import com.yuroyami.syncplay.viewmodel.SyncplayViewmodel
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.math.roundToInt

@Composable
actual fun getSystemMaxVolume(): Int {
    val audioManager = LocalContext.current.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
}

actual val platform: PLATFORM = PLATFORM.Android


actual fun getDefaultEngine(): String = if (BuildConfig.FLAVOR != "noLibs") BasePlayer.ENGINE.ANDROID_EXOPLAYER.name else BasePlayer.ENGINE.ANDROID_MPV.name

actual fun SyncplayViewmodel.instantiatePlayer(engine: BasePlayer.ENGINE) = when (engine) {
    ENGINE.ANDROID_EXOPLAYER -> ExoPlayer(this)
    ENGINE.ANDROID_MPV -> MpvPlayer(this)
    ENGINE.ANDROID_VLC -> VlcPlayer(this)
    else -> null
}

actual fun SyncplayViewmodel.instantiateNetworkEngineProtocol(engine: SyncplayProtocol.NetworkEngine) = when (engine) {
    SyncplayProtocol.NetworkEngine.NETTY -> SpProtocolAndroid(this)
    else -> SpProtocolKtor(this)
}

actual fun generateTimestampMillis() = System.currentTimeMillis()

actual fun getFolderName(uri: String): String? {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        uri.toUri(),
        DocumentsContract.getTreeDocumentId(uri.toUri())
    )

    val context = contextObtainer.obtainAppContext()
    val d = DocumentFile.fromTreeUri(context, childrenUri)
    return d?.name
}

actual fun getFileName(uri: String): String? {
    val actualuri = uri.toUri()
    val context = contextObtainer.obtainAppContext()
    return when (actualuri.scheme) {
        ContentResolver.SCHEME_CONTENT -> context.getContentFileName(actualuri)
        else -> actualuri.path?.let(::File)?.name
    }
}

private fun Context.getContentFileName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        cursor.moveToFirst()
        return@use cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            .let(cursor::getString)
    }
}.getOrNull()


actual suspend fun pingIcmp(host: String, packet: Int): Int? {
    try {
        val pingprocess = Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 1 -s $packet $host") ?: return null
        val inputStream = BufferedReader(InputStreamReader(pingprocess.inputStream))
        val pingOutput = inputStream.use { it.readText() }

        return if (pingOutput.contains("100% packet loss")) {
            null
        } else {
            pingOutput.substringAfter("time=").substringBefore(" ms").trim()
                .toDouble().roundToInt()
        }
    } catch (e: Exception) {
        loggy(e.stackTraceToString(), 69)
        return null
    }
}

actual fun ClipEntry.getText(): String? {
    return this.clipData.getItemAt(0).text?.toString()
}