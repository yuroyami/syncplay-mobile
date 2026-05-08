package app.player.resolver

/**
 * Resolves "page URLs" (YouTube, SoundCloud, PeerTube, etc.) into direct streamable URLs.
 *
 * The PC desktop app gets this for free via mpv's `ytdl_hook.lua`, which shells out to a
 * locally installed `yt-dlp` binary. Mobile has no such binary on PATH, so each platform
 * provides its own pure-native extractor:
 *  - Android: NewPipe Extractor (com.github.TeamNewPipe:NewPipeExtractor) — JVM/Java, no Python.
 *  - iOS: XCDYouTubeKit — Objective-C, YouTube only, no Python.
 *
 * Each client resolves independently at retrieve time. The shared playlist still stores the
 * *original* page URL (e.g. `youtube.com/watch?v=…`) — direct stream URLs are typically
 * IP-pinned and time-limited, so they wouldn't be valid across clients anyway.
 */
interface MediaResolver {
    /**
     * Returns a resolved direct URL for the given page URL, or null if this resolver does not
     * handle the URL (unknown service, parse failure, network error, etc.). Callers should
     * fall back to the original URL when null is returned.
     */
    suspend fun resolve(url: String): ResolvedMedia?
}

/**
 * The platform's native [MediaResolver]. Initialized lazily on first use.
 *  - Android: NewPipe Extractor — YouTube, SoundCloud, PeerTube, Bandcamp, MediaCCC.
 *  - iOS: XCDYouTubeKit — YouTube only.
 */
expect val mediaResolver: MediaResolver

/**
 * Output of [MediaResolver.resolve] — a direct streamable URL plus best-effort metadata.
 *
 * @property directUrl A URL the player engine can hand to its native loader (HLS, MP4, DASH, …).
 * @property title Human-readable title, used to override the auto-derived filename in the OSD.
 * @property durationSec Duration in seconds, or null if the source is a livestream/unknown.
 */
data class ResolvedMedia(
    val directUrl: String,
    val title: String? = null,
    val durationSec: Double? = null,
)

/**
 * Quick "is this clearly a direct media file already" check. Returns true for URLs whose
 * path ends with a recognizable container extension — those go straight to the player and
 * skip the resolver. Anything else (page URLs, query-string-only URLs, unknown extensions)
 * is offered to the resolver.
 *
 * Kept conservative on purpose: false positives here mean an unnecessary resolver call
 * (the resolver returns null, we use the original URL — no harm done).
 */
fun urlLooksLikeDirectMedia(url: String): Boolean {
    val pathPart = url.substringBefore('?').substringBefore('#').lowercase()
    return DIRECT_MEDIA_EXTENSIONS.any { pathPart.endsWith(it) }
}

private val DIRECT_MEDIA_EXTENSIONS = listOf(
    ".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".flv", ".wmv",
    ".3gp", ".ts", ".mts", ".m2ts", ".mpg", ".mpeg", ".vob",
    ".m3u8", ".mpd", // HLS / DASH manifests
    ".mp3", ".m4a", ".ogg", ".oga", ".opus", ".flac", ".wav", ".aac",
)

/**
 * Pulls the 11-character video ID out of any common YouTube URL form:
 *  - youtube.com/watch?v=ID
 *  - youtu.be/ID
 *  - youtube.com/embed/ID
 *  - youtube.com/shorts/ID
 *  - m.youtube.com / music.youtube.com (subdomains)
 *
 * Returns null if the URL is not a recognizable YouTube link. Used by the iOS resolver,
 * since XCDYouTubeKit's API takes a raw video ID rather than a URL string.
 */
fun extractYoutubeId(url: String): String? = YOUTUBE_ID_REGEX.find(url)?.groupValues?.getOrNull(1)

private val YOUTUBE_ID_REGEX = Regex(
    pattern = """(?:youtube(?:-nocookie)?\.com/(?:watch\?(?:.*&)?v=|embed/|v/|shorts/|live/)|youtu\.be/)([\w-]{11})""",
    option = RegexOption.IGNORE_CASE,
)
