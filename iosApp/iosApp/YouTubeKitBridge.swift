import Foundation
import shared
import YouTubeKit

/// Swift implementation of `YouTubeKitBridge` (declared in Kotlin's iosMain) using the
/// [YouTubeKit](https://github.com/alexeichhorn/YouTubeKit) Swift Package.
///
/// YouTubeKit is pure Swift with no `@objc` surface, so Kotlin can't call it via cinterop;
/// the Kotlin side declares an abstract class with a callback API and this class subclasses it,
/// running the async extraction inside a `Task`. Registered in `iOSApp.swift`.
///
/// Stream selection: picks the highest-resolution combined audio+video stream that AVPlayer can
/// decode natively (`isNativelyPlayable` = MP4/H.264 without remuxing). Adaptive (DASH-style)
/// audio-only / video-only streams are ignored since the players want a single combined source.
class YouTubeKitBridgeImpl: YouTubeKitBridge, @unchecked Sendable {

    /// Kotlin's `(String?, String?, Double) -> Unit` boxes the primitive Double across the ObjC
    /// ABI, so the Swift signature uses `KotlinDouble`, not Swift `Double`. Mismatching this
    /// produces an override-not-found error.
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
                // Combined audio+video AVPlayer can decode natively. If none, the call returns
                // nil and resolution fails; the caller falls back to the original URL.
                let stream = try await yt.streams
                    .filterVideoAndAudio()
                    .filter { $0.isNativelyPlayable }
                    .highestResolutionStream()

                guard let resolvedURL = stream?.url else {
                    completion(nil, nil, unknownDuration)
                    return
                }

                // Metadata fetch is best-effort: failure here doesn't abort resolution.
                let title: String? = (try? await yt.metadata)?.title
                // YouTubeMetadata exposes no duration; pass the -1.0 sentinel.
                completion(resolvedURL.absoluteString, title, unknownDuration)
            } catch {
                completion(nil, nil, unknownDuration)
            }
        }
    }
}
