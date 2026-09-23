package app.player.resolver

/**
 * The web media resolver, which resolves nothing. A media resolver turns a page URL (such as a
 * YouTube link) into a direct stream URL.
 *
 * NewPipe is a JVM library and YouTubeKit is Swift, so neither runs in the browser. Extracting a
 * stream URL from a page in the browser also hits the origin policy: the request must be
 * same-origin or CORS-approved, and video sites are neither. A site's own embed (a player, not a
 * URL) fits the browser better than a resolver.
 *
 * Returning null is the documented "I do not handle this", so callers use the original URL.
 */
private object WebMediaResolver : MediaResolver {
    override suspend fun resolve(url: String): ResolvedMedia? = null
}

actual val mediaResolver: MediaResolver = WebMediaResolver
