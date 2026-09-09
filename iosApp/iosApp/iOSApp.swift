import SwiftUI
import shared

/// iOS app entry point. Registers the Swift-implemented bridges that Kotlin common code calls
/// into, then shows a single fullscreen `SyncplayScreen`.
@main
struct iOSApp: App {

    @Environment(\.scenePhase) private var scenePhase

    /// Runs before the UI: initializes the DataStore and registers the SwiftNIO / YouTubeKit
    /// factory bridges. These libraries are pure Swift with no ObjC surface, so Kotlin/Native
    /// calls a registered factory closure instead of instantiating them through cinterop.
    init() {
        DatastoreInitKt.initializeDS()

        SwiftNioNetworkManagerKt.instantiateSwiftNioNetworkManager = { (roomViewmodel: RoomViewmodel) -> NetworkManager in
            return SwiftNioNetworkManager(viewmodel: roomViewmodel) as NetworkManager
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
                .onAppear {
                    updateIdleTimer(for: scenePhase)
                }
        }
        .onChange(of: scenePhase) { phase in
            updateIdleTimer(for: phase)
        }
    }

    /// Apply after a scene exists and reapply on every activation, rather than only in init.
    /// This app-wide policy also covers KitePlayer's custom renderer. Reading scenePhase at
    /// App scope keeps the display awake while any scene is active and releases it otherwise.
    private func updateIdleTimer(for phase: ScenePhase) {
        UIApplication.shared.isIdleTimerDisabled = phase == .active
    }
}
