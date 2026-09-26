import SwiftUI
import shared

/// iOS app entry point. Registers the Swift-implemented bridges that Kotlin common code calls
/// into, then shows a single fullscreen `SyncplayScreen`.
@main
struct iOSApp: App {

    @Environment(\.scenePhase) private var scenePhase

    /// Gives the scene a delegate of its own, the only place where iOS delivers a Quick Action.
    @UIApplicationDelegateAdaptor(QuickActionAppDelegate.self) private var quickActions

    /// Runs before the UI: initializes the DataStore and registers the SwiftNIO / YouTubeKit
    /// factory bridges. These libraries are pure Swift with no ObjC surface, so Kotlin/Native
    /// calls a registered factory closure instead of instantiating them through cinterop.
    init() {
        DatastoreInitKt.initializeDS()

        SwiftNioNetworkManagerKt.instantiateSwiftNioNetworkManager = { (roomViewmodel: RoomViewmodel) -> NetworkManager in
            return SwiftNioNetworkManager(viewmodel: roomViewmodel) as NetworkManager
        }

        // Without this registration, the iOS MediaResolver does nothing, and page URLs reach the
        // player unresolved (fine for direct media files).
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
                // An invite link, at a cold start and while the app runs. Home joins it.
                .onOpenURL { url in
                    _ = IncomingJoinsKt.onIncomingLink(url: url.absoluteString)
                }
        }
        .onChange(of: scenePhase) { phase in
            updateIdleTimer(for: phase)
        }
    }

    /// Keeps the display awake while any scene is active, and releases it otherwise (reading
    /// `scenePhase` at App scope gives that). It runs after a scene exists and again on every
    /// activation, not only in `init`. This app-wide policy also covers KitePlayer's renderer.
    private func updateIdleTimer(for phase: ScenePhase) {
        UIApplication.shared.isIdleTimerDisabled = phase == .active
    }
}

/// Configures each scene with `QuickActionSceneDelegate`, which receives the Home Screen's Quick
/// Actions. SwiftUI still owns the window.
final class QuickActionAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = QuickActionSceneDelegate.self
        return configuration
    }
}

/// Hands a Quick Action to the shared code: the one that started the app, and one tapped while
/// it runs. Home then joins its room.
final class QuickActionSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let item = connectionOptions.shortcutItem {
            IncomingJoinsKt.onQuickAction(type: item.type)
        }
    }

    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        IncomingJoinsKt.onQuickAction(type: shortcutItem.type)
        completionHandler(true)
    }
}
