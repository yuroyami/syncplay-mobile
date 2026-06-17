import SwiftUI
import shared

/// iOS app entry point. Registers the Swift-implemented bridges that Kotlin common code calls
/// into, then shows a single fullscreen `SyncplayScreen`.
@main
struct iOSApp: App {

    /// Runs before the UI: disables the idle timer (screen-on during playback), initializes the
    /// DataStore, and registers the SwiftNIO / MPVKit / YouTubeKit factory bridges. Each bridge
    /// exists because the underlying library is pure Swift with no ObjC surface, so Kotlin/Native
    /// can't instantiate it via cinterop and instead calls a registered factory closure.
    init() {
        UIApplication.shared.isIdleTimerDisabled = true

        DatastoreInitKt.initializeDS()

        SwiftNioNetworkManagerKt.instantiateSwiftNioNetworkManager = { (roomViewmodel: RoomViewmodel) -> NetworkManager in
            return SwiftNioNetworkManager(viewmodel: roomViewmodel) as NetworkManager
        }

        MpvKitBridgeKt.instantiateMpvKitPlayer = { (viewmodel: RoomViewmodel) -> PlayerImpl in
            let bridge = MpvKitBridgeImpl()
            return MpvKitImpl(viewmodel: viewmodel, bridge: bridge)
        }

        // Without this registration, MediaResolver on iOS no-ops and page URLs pass through to
        // the player unresolved (fine for direct media files).
        YouTubeKitBridgeKt.instantiateYouTubeKitBridge = {
            return YouTubeKitBridgeImpl()
        }
    }

    /// Root scene: a single `SyncplayScreen` ignoring safe-area insets for fullscreen video.
    var body: some Scene {
        WindowGroup {
            SyncplayScreen().ignoresSafeArea(.all)
        }
    }
}