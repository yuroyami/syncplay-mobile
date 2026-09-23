import Foundation
import shared
import YouTubeKit

/// Swift implementation of `YouTubeKitBridge` (declared in Kotlin's iosMain), built on the
/// [YouTubeKit](https://github.com/alexeichhorn/YouTubeKit) Swift package.
///
/// YouTubeKit is pure Swift with no `@objc` surface, so Kotlin cannot call it through cinterop.
/// The Kotlin side declares an abstract class with a callback API. This class subclasses it and
/// runs the async extraction inside a `Task`. `iOSApp.swift` registers it.
///
/// Stream selection: the highest-resolution stream with both audio and video that AVPlayer can
/// decode natively (`isNativelyPlayable`: both codecs are ones AVPlayer decodes, such as H.264
/// and AAC). Adaptive (DASH-style) audio-only and video-only streams are skipped, because the
/// players need one combined source.
class YouTubeKitBridgeImpl: YouTubeKitBridge, @unchecked Sendable {

    /// Kotlin's `(String?, String?, Double) -> Unit` boxes the Double when it crosses to
    /// Objective-C, so the Swift signature takes `KotlinDouble`, not Swift `Double`. A plain
    /// `Double` fails to compile with an override-not-found error.
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
                // A combined audio and video stream that AVPlayer decodes natively. If there is
                // none, `stream` is nil and resolution fails, so the caller uses the original URL.
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
                // YouTubeMetadata has no duration, so pass -1.0 (unknown).
                completion(resolvedURL.absoluteString, title, unknownDuration)
            } catch {
                completion(nil, nil, unknownDuration)
            }
        }
    }
}
