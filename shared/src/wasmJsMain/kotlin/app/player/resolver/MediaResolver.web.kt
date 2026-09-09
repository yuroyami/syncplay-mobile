package app.player.resolver

/**
 * Resolves nothing, for now.
 *
 * NewPipe is a JVM library and YouTubeKit is Swift, so neither reaches the browser. Extracting a
 * stream URL from a page in the browser also runs into the origin policy: the request would have
 * to be same-origin or CORS-approved, and video sites are neither. The likely answer here is not
 * a resolver at all but the site's own embed, which is a player, not a URL.
 *
 * Returning null is the documented "I do not handle this", so callers use the original URL.
 */
private object WebMediaResolver : MediaResolver {
    override suspend fun resolve(url: String): ResolvedMedia? = null
}

actual val mediaResolver: MediaResolver = WebMediaResolver
