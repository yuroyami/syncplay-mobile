package app.player.resolver

/**
 * Factory reference for the Swift-implemented YouTubeKit bridge, registered at app startup
 * (`YouTubeKitBridgeKt.instantiateYouTubeKitBridge = { YouTubeKitBridgeImpl() }`).
 *
 * [YouTubeKit](https://github.com/alexeichhorn/YouTubeKit) is a pure-Swift package (no `@objc`),
 * so it isn't visible to Kotlin/Native via cinterop. Following the MPVKit pattern, the abstract
 * class is declared here and subclassed in Swift (`iosApp/iosApp/YouTubeKitBridge.swift`).
 *
 * Resolution is async (network fetch + cipher solving). The bridge uses a callback signature to
 * stay JVM-free in commonMain; [YouTubeKitMediaResolver] wraps it in `suspendCancellableCoroutine`
 * to expose the suspend [MediaResolver.resolve] API. YouTubeKit transparently falls back to a
 * maintained server-side extractor (Cloudflare Workers + youtube-dl) when YouTube rotates its
 * signature cipher and local extraction breaks.
 */
var instantiateYouTubeKitBridge: (() -> YouTubeKitBridge)? = null

/**
 * Bridge that the Swift side subclasses. Methods use callback signatures to avoid depending on
 * Kotlin/Swift coroutine interop (which would require SKIE).
 */
abstract class YouTubeKitBridge {
    /**
     * Resolve [url] to a direct streamable URL plus best-effort metadata. Always invokes
     * [completion] exactly once, on any thread:
     *  - Success: `directUrl` non-null, `title` may be null, `durationSec` is `-1.0` when unknown
     *    (YouTubeKit's metadata struct has no duration field).
     *  - Any failure (parse, network, no playable stream): all three null/-1.0.
     */
    abstract fun resolve(
        url: String,
        completion: (directUrl: String?, title: String?, durationSec: Double) -> Unit,
    )
}
