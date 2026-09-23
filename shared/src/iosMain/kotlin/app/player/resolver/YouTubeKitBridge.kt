package app.player.resolver

/**
 * Factory for the Swift YouTubeKit bridge, registered at app startup
 * (`YouTubeKitBridgeKt.instantiateYouTubeKitBridge = { YouTubeKitBridgeImpl() }`).
 *
 * [YouTubeKit](https://github.com/alexeichhorn/YouTubeKit) is a pure Swift package (no `@objc`),
 * so Kotlin/Native cannot see it through cinterop. The SwiftNIO bridge uses the same pattern: the
 * abstract class is declared here and subclassed in Swift (`iosApp/iosApp/YouTubeKitBridge.swift`).
 *
 * Resolution is asynchronous (a network fetch plus cipher solving). The bridge takes a callback
 * (see [YouTubeKitBridge]), and [YouTubeKitMediaResolver] wraps it in
 * `suspendCancellableCoroutine` to provide the suspend [MediaResolver.resolve] API. The Swift side
 * uses YouTubeKit's default extraction method, which is local only on iOS. YouTubeKit's remote
 * extraction server, a fallback for when YouTube changes its signature cipher, is not enabled.
 */
var instantiateYouTubeKitBridge: (() -> YouTubeKitBridge)? = null

/**
 * Bridge that the Swift side subclasses. The methods take callbacks, so the bridge does not
 * depend on Kotlin and Swift coroutine interop (which would need SKIE).
 */
abstract class YouTubeKitBridge {
    /**
     * Resolves [url] to a direct streamable URL plus best-effort metadata. Always calls
     * [completion] exactly once, from any thread:
     *  - Success: `directUrl` is not null, `title` may be null, and `durationSec` is `-1.0` when
     *    unknown (YouTubeKit's metadata struct has no duration field).
     *  - Any failure (parse, network, no playable stream): null, null and -1.0.
     */
    abstract fun resolve(
        url: String,
        completion: (directUrl: String?, title: String?, durationSec: Double) -> Unit,
    )
}
