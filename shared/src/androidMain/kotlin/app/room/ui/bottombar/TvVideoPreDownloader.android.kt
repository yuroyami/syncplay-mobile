package app.room.ui.bottombar

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

@Composable
internal actual fun rememberTvVideoPreDownloader(
    onProgress: (Float?) -> Unit,
    onComplete: (String) -> Unit,
    onError: (String) -> Unit,
): ((String) -> Unit)? {
    val context = LocalContext.current
    if (!context.isTelevisionForPreDownload() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

    val scope = rememberCoroutineScope()
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnError by rememberUpdatedState(onError)

    return remember(context, scope) {
        { url ->
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        TvParallelDownloader.download(
                            context = context,
                            url = url,
                            onProgress = { progress ->
                                scope.launch { currentOnProgress(progress) }
                            },
                        )
                    }
                }.onSuccess(currentOnComplete)
                    .onFailure { error ->
                        currentOnError(error.message ?: "Download failed")
                    }
            }
        }
    }
}

private fun Context.isTelevisionForPreDownload(): Boolean {
    val uiModeIsTelevision = resources.configuration.uiMode and
        Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    return uiModeIsTelevision || packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

private object TvParallelDownloader {
    private const val PARALLEL_PARTS = 4
    private const val PARALLEL_MIN_BYTES = 4L * 1024L * 1024L
    private const val BUFFER_SIZE = 128 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Float?) -> Unit,
    ): String {
        val probe = probe(url)
        onProgress(if (probe.totalBytes != null) 0f else null)
        if (probe.supportsRanges &&
            probe.totalBytes != null &&
            probe.totalBytes >= PARALLEL_MIN_BYTES
        ) {
            runCatching {
                downloadParallelToMediaStore(context, url, probe, onProgress)
            }.getOrElse {
                if (it is CancellationException) throw it
                coroutineContext.ensureActive()
                onProgress(0f)
                downloadSequentialToMediaStore(context, url, probe, onProgress)
            }
        } else {
            downloadSequentialToMediaStore(context, url, probe, onProgress)
        }
        return probe.fileName
    }

    private fun probe(url: String): ProbeResult {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Server returned HTTP ${response.code}")
            }

            val totalBytes = response.header("Content-Range")
                ?.substringAfterLast('/')
                ?.toLongOrNull()
                ?: response.body.contentLength().takeIf { it > 0L && response.code == 200 }
            return ProbeResult(
                totalBytes = totalBytes,
                supportsRanges = response.code == 206 && totalBytes != null,
                fileName = response.downloadFileName(url),
                mimeType = response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.takeIf { it.isNotBlank() }
                    ?: "video/*",
            )
        }
    }

    private suspend fun downloadParallelToMediaStore(
        context: Context,
        url: String,
        probe: ProbeResult,
        onProgress: (Float?) -> Unit,
    ) {
        val totalBytes = requireNotNull(probe.totalBytes)
        writePendingVideo(context, probe.fileName, probe.mimeType) { uri ->
            val descriptor = context.contentResolver.openFileDescriptor(uri, "rw")
                ?: throw IOException("Could not open the downloaded video")
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                downloadParallel(url, totalBytes, output.channel, onProgress)
                output.channel.force(true)
            }
        }
    }

    private suspend fun downloadParallel(
        url: String,
        totalBytes: Long,
        channel: FileChannel,
        onProgress: (Float?) -> Unit,
    ) = coroutineScope {
        val downloadedBytes = AtomicLong(0L)
        val lastReportedPercent = AtomicInteger(-1)
        val reportProgress = {
            val percent = ((downloadedBytes.get() * 100L) / totalBytes).toInt().coerceIn(0, 100)
            while (true) {
                val previous = lastReportedPercent.get()
                if (percent <= previous) break
                if (lastReportedPercent.compareAndSet(previous, percent)) {
                    onProgress(percent / 100f)
                    break
                }
            }
        }

        splitDownloadRanges(totalBytes, PARALLEL_PARTS).map { range ->
            async(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=${range.first}-${range.last}")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code != 206) {
                        throw IOException("Server stopped supporting parallel range requests")
                    }
                    if (!contentRangeMatches(response.header("Content-Range"), range, totalBytes)) {
                        throw IOException("Server returned an unexpected download range")
                    }
                    val body = response.body
                    body.byteStream().use { input ->
                        val expectedBytes = range.last - range.first + 1L
                        var rangeBytes = 0L
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (rangeBytes + count > expectedBytes) {
                                throw IOException("Downloaded range had an unexpected size")
                            }

                            val bytes = ByteBuffer.wrap(buffer, 0, count)
                            var writePosition = range.first + rangeBytes
                            while (bytes.hasRemaining()) {
                                val written = channel.write(bytes, writePosition)
                                if (written <= 0) {
                                    throw IOException("Could not write the downloaded video")
                                }
                                writePosition += written
                            }
                            rangeBytes += count
                            downloadedBytes.addAndGet(count.toLong())
                            reportProgress()
                        }
                        if (rangeBytes != expectedBytes) {
                            throw IOException("Downloaded range had an unexpected size")
                        }
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun downloadSequentialToMediaStore(
        context: Context,
        url: String,
        probe: ProbeResult,
        onProgress: (Float?) -> Unit,
    ) {
        writePendingVideo(context, probe.fileName, probe.mimeType) { uri ->
            val output = context.contentResolver.openOutputStream(uri, "w")
                ?: throw IOException("Could not open the downloaded video")
            output.buffered().use { destination ->
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Server returned HTTP ${response.code}")
                    }
                    val totalBytes = response.body.contentLength()
                        .takeIf { it > 0L }
                        ?: probe.totalBytes
                    var downloadedBytes = 0L
                    var lastReportedPercent = -1

                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            destination.write(buffer, 0, count)
                            downloadedBytes += count
                            if (totalBytes != null) {
                                val percent = ((downloadedBytes * 100L) / totalBytes)
                                    .toInt()
                                    .coerceIn(0, 100)
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun writePendingVideo(
        context: Context,
        displayName: String,
        mimeType: String,
        write: suspend (Uri) -> Unit,
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Syncplay")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create the downloaded video")

        try {
            write(uri)

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun Response.downloadFileName(url: String): String {
        val fromDisposition = header("Content-Disposition")
            ?.let(::parseContentDispositionFileName)
        val fromUrl = Uri.parse(url).lastPathSegment
            ?.substringBefore('?')
            ?.takeIf { it.isNotBlank() }
        val rawName = fromDisposition ?: fromUrl ?: "syncplay-video-${System.currentTimeMillis()}.mp4"
        val cleanName = rawName.replace(Regex("""[\\/:*?"<>|]"""), "_").take(180)

        if (cleanName.substringAfterLast('.', "").isNotBlank()) return cleanName
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(header("Content-Type")?.substringBefore(';'))
        return if (extension == null) "$cleanName.mp4" else "$cleanName.$extension"
    }

    private fun parseContentDispositionFileName(header: String): String? {
        val encoded = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues
            ?.get(1)
        if (encoded != null) {
            return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
        }
        return Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues
            ?.get(1)
    }

    private data class ProbeResult(
        val totalBytes: Long?,
        val supportsRanges: Boolean,
        val fileName: String,
        val mimeType: String,
    )
}
