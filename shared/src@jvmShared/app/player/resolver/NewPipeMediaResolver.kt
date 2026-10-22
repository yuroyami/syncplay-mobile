package app.player.resolver

import app.utils.loggy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.TimeUnit
import okhttp3.Request as OkRequest
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse

/** The NewPipe media resolver that Android and desktop share. A media resolver turns a page URL
 *  (such as a YouTube link) into a direct stream URL. This one is pure JVM, with no Python and no
 *  native binaries.
 *
 *  It supports YouTube, SoundCloud, PeerTube, Bandcamp and MediaCCC. NewPipe's
 *  `StreamInfo.getInfo(url)` detects the service from the URL. If no service handles the URL (for
 *  example a Twitch URL), it throws and [resolve] returns null, so the caller can fall back to
 *  the original URL. */
internal object NewPipeMediaResolver : MediaResolver {

    @Volatile private var initialized = false
    private val initLock = Any()

    override suspend fun resolve(url: String): ResolvedMedia? = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit()
            val info = StreamInfo.getInfo(url)
            val direct = pickDirectUrl(info) ?: return@runCatching null
            ResolvedMedia(
                directUrl = direct,
                title = info.name,
                durationSec = info.duration.takeIf { it > 0L }?.toDouble(),
            )
        }.onFailure {
            loggy("MediaResolver(NewPipe): no resolution for $url — ${it.message}")
        }.getOrNull()
    }

    private fun ensureInit() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            NewPipe.init(
                NewPipeOkHttpDownloader,
                Localization("en", "US"),
                ContentCountry("US"),
            )
            initialized = true
        }
    }

    private fun pickDirectUrl(info: StreamInfo): String? {
        // Live streams use the HLS manifest. Every engine on Android and desktop plays HLS.
        if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) {
            info.hlsUrl?.takeIf { it.isNotBlank() }?.let { return it }
        }
        // Combined audio and video streams, so the engine gets one muxed source. Video-only
        // streams plus a separate audio stream are skipped on purpose, because they need muxing.
        info.videoStreams.orEmpty().bestPick()?.content?.let { return it }
        // SoundCloud and Bandcamp have no video streams, so fall back to the best audio stream.
        // Without this fallback their links resolve to nothing, and the raw page URL goes to the
        // player.
        return info.audioStreams.orEmpty().maxByOrNull { it.averageBitrate }?.content
    }

    private fun List<VideoStream>.bestPick(): VideoStream? {
        if (isEmpty()) return null
        val mp4 = filter { it.format?.suffix == "mp4" }
        return mp4.filter { it.heightPx() <= 720 }.maxByOrNull { it.heightPx() }
            ?: mp4.maxByOrNull { it.heightPx() }
            ?: maxByOrNull { it.heightPx() }
    }

    @Suppress("DEPRECATION") // The VideoStream.resolution field is deprecated for getResolution(),
    // but Kotlin's property syntax binds to the field. The suppression keeps the call readable.
    private fun VideoStream.heightPx(): Int =
        resolution?.substringBefore('p')?.toIntOrNull() ?: 0
}

/** Bridges NewPipe's [Downloader] to OkHttp. OkHttp is already on the Android and desktop
 *  classpath through ktor-client-okhttp, so no extra dependency is needed. */
private object NewPipeOkHttpDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: NpRequest): NpResponse {
        val builder = OkRequest.Builder().url(request.url())
        request.headers().forEach { (key, values) ->
            values.forEach { v -> builder.addHeader(key, v) }
        }
        val body = request.dataToSend()?.toRequestBody(null)
        builder.method(request.httpMethod(), body)

        val response = client.newCall(builder.build()).execute()
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha required", request.url())
        }
        return NpResponse(
            response.code,
            response.message,
            response.headers.toMultimap(),
            response.body.string(),
            response.request.url.toString(),
        )
    }
}

actual val mediaResolver: MediaResolver = NewPipeMediaResolver
