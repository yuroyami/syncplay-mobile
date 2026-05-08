package app.player.resolver

import app.utils.loggy
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** iOS resolver — backed by YouTubeKit (Swift) via [YouTubeKitBridge].
 *
 *  YouTube only. Other URLs fall through to the player unresolved (which is fine for direct
 *  media files; fails predictably for other page URLs the player can't handle natively).
 *
 *  If the bridge factory was never registered (e.g. running unit tests without the iosApp
 *  target wired up), [resolve] returns null silently and the caller passes the original URL
 *  through unchanged. */
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
