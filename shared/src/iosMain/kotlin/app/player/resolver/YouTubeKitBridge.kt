package app.player.resolver

/**
 * Factory function reference for the Swift-implemented YouTubeKit bridge.
 *
 * ## Why a bridge?
 * [YouTubeKit](https://github.com/alexeichhorn/YouTubeKit) is a pure-Swift Swift Package
 * (no `@objc` exposure), so its API isn't visible to Kotlin/Native through cinterop. Instead
 * we mirror the MPVKit pattern used elsewhere in this project: declare an abstract class
 * here, subclass it in Swift inside `iosApp`, and register a factory at app startup.
 *
 * ## Implementation
 * Swift side: `iosApp/iosApp/YouTubeKitBridge.swift`
 *
 * ```swift
 * YouTubeKitBridgeKt.instantiateYouTubeKitBridge = {
 *     return YouTubeKitBridgeImpl()
 * }
 * ```
 *
 * Resolution itself is async (network fetch + cipher solving). To stay JVM-free in commonMain
 * we use a *callback* signature on the bridge — [YouTubeKitMediaResolver] wraps that in
 * `suspendCancellableCoroutine` to expose the standard suspend [MediaResolver.resolve] API.
 *
 * ## YouTubeKit's Cloudflare fallback
 * When YouTube rotates its signature cipher, local extraction can break for everyone running
 * the same library version. YouTubeKit ships a maintained server-side fallback (Cloudflare
 * Workers + youtube-dl) that the library transparently falls back to — this is the main
 * reason we picked it over the older XCDYouTubeKit (last release 2021).
 */
var instantiateYouTubeKitBridge: (() -> YouTubeKitBridge)? = null

/**
 * Bridge that the Swift side subclasses. Methods are declared with callback signatures so
 * we don't depend on Kotlin/Swift coroutine interop (which would otherwise require SKIE).
 */
abstract class YouTubeKitBridge {
    /**
     * Resolve [url] to a direct streamable URL plus best-effort metadata. Always invokes
     * [completion] exactly once, on any thread:
     *  - On success: `directUrl` non-null, `title` may be null, `durationSec` is `-1.0` if
     *    unknown (YouTubeKit's metadata struct has no duration field as of v0.4.x).
     *  - On any failure (parse, network, no playable stream): all three null/-1.0.
     */
    abstract fun resolve(
        url: String,
        completion: (directUrl: String?, title: String?, durationSec: Double) -> Unit,
    )
}
