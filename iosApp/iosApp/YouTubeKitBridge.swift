import Foundation
import shared
import YouTubeKit

/// Swift implementation of `YouTubeKitBridge` (defined in Kotlin's iosMain) using
/// the [YouTubeKit](https://github.com/alexeichhorn/YouTubeKit) Swift Package.
///
/// YouTubeKit is pure Swift and exposes no `@objc` surface, so Kotlin can't call it
/// directly via cinterop. The Kotlin side declares an abstract class with a callback
/// API; this class subclasses it and runs the async extraction inside a `Task`.
///
/// ## Stream selection
/// We pick the highest-resolution combined audio+video stream that's natively playable
/// on Apple platforms (`isNativelyPlayable` returns true for MP4/H.264 streams that
/// AVPlayer can handle without remuxing). YouTubeKit also exposes adaptive (DASH-style)
/// audio-only and video-only streams, but our players want a single combined source.
///
/// ## Registration
/// Wired up in `iOSApp.swift`:
/// ```swift
/// YouTubeKitBridgeKt.instantiateYouTubeKitBridge = {
///     return YouTubeKitBridgeImpl()
/// }
/// ```
class YouTubeKitBridgeImpl: YouTubeKitBridge, @unchecked Sendable {

    /// Kotlin's `(String?, String?, Double) -> Unit` boxes the primitive Double when it
    /// crosses the ObjC ABI, so the Swift signature here uses `KotlinDouble`, not Swift
    /// `Double`. Mismatching this is the override-not-found error you get otherwise.
    override func resolve(
        url: String,
        completion: @escaping (String?, String?, KotlinDouble) -> Void
    ) {
        let unknownDuration = KotlinDouble(value: -1.0)

        guard let videoURL = URL(string: url) else {
            completion(nil, nil, unknownDuration)
            return
        }
        Task {
            do {
                let yt = YouTube(url: videoURL)
                // Prefer combined audio+video that AVPlayer can decode natively. If none, the
                // call returns nil and we surface failure — caller falls back to original URL.
                let stream = try await yt.streams
                    .filterVideoAndAudio()
                    .filter { $0.isNativelyPlayable }
                    .highestResolutionStream()

                guard let resolvedURL = stream?.url else {
                    completion(nil, nil, unknownDuration)
                    return
                }

                // Metadata fetch is best-effort: failure here doesn't abort the resolution.
                let title: String? = (try? await yt.metadata)?.title
                // YouTubeMetadata (v0.4.x) doesn't expose duration; pass -1.0 sentinel.
                completion(resolvedURL.absoluteString, title, unknownDuration)
            } catch {
                completion(nil, nil, unknownDuration)
            }
        }
    }
}
