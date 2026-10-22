package app.player.resolver

/**
 * Resolves page URLs (YouTube, SoundCloud, PeerTube and so on) into direct stream URLs.
 *
 * The Syncplay PC client gets this from mpv's `ytdl_hook.lua`, which runs a locally installed
 * `yt-dlp` binary. This app has no such binary, so each platform brings its own native extractor,
 * with no Python:
 *  - Android and desktop: NewPipe Extractor (com.github.TeamNewPipe:NewPipeExtractor), on the JVM.
 *  - iOS: YouTubeKit, in Swift, for YouTube only.
 *
 * Each client resolves the URL itself, at load time. The shared playlist keeps the original page
 * URL (for example `youtube.com/watch?v=…`). Direct stream URLs are usually tied to one IP address
 * and expire, so they would not work for the other clients.
 */
interface MediaResolver {
    /**
     * Returns the direct URL for the given page URL, or null when this resolver cannot handle the
     * URL (unknown service, parse failure, network error and so on). On null, the caller uses the
     * original URL.
     */
    suspend fun resolve(url: String): ResolvedMedia?
}

/**
 * The platform's native [MediaResolver], created on first use.
 *  - Android and desktop: NewPipe Extractor, for YouTube, SoundCloud, PeerTube, Bandcamp and
 *    MediaCCC.
 *  - iOS: YouTubeKit, for YouTube only.
 *  - Web: a resolver that resolves nothing yet.
 */
expect val mediaResolver: MediaResolver

/**
 * The result of [MediaResolver.resolve]: a direct stream URL, plus metadata when available.
 *
 * @property directUrl A URL that the engine can pass to its native loader (HLS, MP4, DASH and
 *   so on).
 * @property title A readable title. It replaces the file name taken from the URL.
 * @property durationSec The duration in seconds, or null for a live stream or an unknown length.
 */
data class ResolvedMedia(
    val directUrl: String,
    val title: String? = null,
    val durationSec: Double? = null,
)

/**
 * A quick check for a URL that is clearly a direct media file already. Returns true when the URL
 * path ends with a known container extension. Such a URL goes straight to the player and skips
 * the resolver. Any other URL (a page URL, a query-only URL, an unknown extension) goes to the
 * resolver.
 *
 * The check is strict on purpose. A missed direct URL only costs an unneeded resolver call: the
 * resolver returns null, and the caller uses the original URL.
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
 *  - youtube.com/embed/ID, /v/ID, /shorts/ID and /live/ID
 *  - youtube-nocookie.com, and subdomains such as m.youtube.com and music.youtube.com
 *
 * Returns null when the URL is not a recognizable YouTube link. The add-media card uses it to
 * tell whether the resolver can handle a pasted link.
 */
fun extractYtId(url: String): String? = YT_ID_REGEX.find(url)?.groupValues?.getOrNull(1)

private val YT_ID_REGEX = Regex(
    pattern = """(?:youtube(?:-nocookie)?\.com/(?:watch\?(?:.*&)?v=|embed/|v/|shorts/|live/)|youtu\.be/)([\w-]{11})""",
    option = RegexOption.IGNORE_CASE,
)
