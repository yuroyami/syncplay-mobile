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

/** Android resolver — pure JVM, no Python, no native binaries.
 *
 *  Supports YouTube, SoundCloud, PeerTube, Bandcamp and MediaCCC out of the box. NewPipe's
 *  `StreamInfo.getInfo(url)` auto-detects the service from the URL; if no service handles it
 *  (e.g. a Twitch URL), it throws and we return null so callers can fall back to the original
 *  URL unchanged. */
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
        // Livestreams: HLS manifest. ExoPlayer/MPV/VLC all parse HLS natively.
        if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) {
            info.hlsUrl?.takeIf { it.isNotBlank() }?.let { return it }
        }
        // Combined audio+video streams — non-DASH-aware players (ExoPlayer w/o DASH module on
        // some flavors, AVPlayer, VLC) need a single muxed source. videoOnlyStreams + audio are
        // intentionally skipped to avoid having to mux.
        return info.videoStreams.orEmpty().bestPick()?.content
    }

    private fun List<VideoStream>.bestPick(): VideoStream? {
        if (isEmpty()) return null
        val mp4 = filter { it.format?.suffix == "mp4" }
        return mp4.filter { it.heightPx() <= 720 }.maxByOrNull { it.heightPx() }
            ?: mp4.maxByOrNull { it.heightPx() }
            ?: maxByOrNull { it.heightPx() }
    }

    @Suppress("DEPRECATION") // VideoStream.resolution field is deprecated in favor of getResolution(),
    // but Kotlin's property syntax binds to the field. Suppress to keep call site readable.
    private fun VideoStream.heightPx(): Int =
        resolution?.substringBefore('p')?.toIntOrNull() ?: 0
}

/** Bridges NewPipe's [Downloader] to OkHttp. OkHttp is already on the Android classpath via
 *  ktor-client-okhttp, so no extra dependency is needed. */
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
