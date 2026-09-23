package app.player.resolver

import app.utils.loggy
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** The iOS media resolver, built on YouTubeKit (Swift) through [YouTubeKitBridge]. A media
 *  resolver turns a page URL into a direct stream URL.
 *
 *  It handles YouTube only. Other URLs reach the player unresolved, which works for direct
 *  media files and fails for other page URLs that the player cannot open.
 *
 *  If the bridge factory was never registered (for example, in unit tests without the iosApp
 *  target), [resolve] logs this and returns null, and the caller uses the original URL. */
internal object YouTubeKitMediaResolver : MediaResolver {

    private val bridge: YouTubeKitBridge? by lazy { instantiateYouTubeKitBridge?.invoke() }

    override suspend fun resolve(url: String): ResolvedMedia? {
        val b = bridge ?: run {
            loggy("MediaResolver(YouTubeKit): bridge not registered — skipping")
            return null
        }
        return suspendCancellableCoroutine { cont ->
            b.resolve(url) { directUrl, title, durationSec ->
                if (!cont.isActive) return@resolve
                cont.resume(
                    directUrl?.let {
                        ResolvedMedia(
                            directUrl = it,
                            title = title,
                            durationSec = durationSec.takeIf { d -> d > 0.0 },
                        )
                    }
                )
            }
        }
    }
}

actual val mediaResolver: MediaResolver = YouTubeKitMediaResolver
