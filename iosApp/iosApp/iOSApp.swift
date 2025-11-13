import SwiftUI
import shared

/**
 * Main entry point for the Syncplay iOS application.
 * Performs critical initialization before the SwiftUI scene hierarchy is created.
 *
 * The app uses a single WindowGroup containing the main Syncplay screen with
 * safe area insets ignored for true fullscreen video playback.
 */
@main
struct iOSApp: App {

    /**
     * Initializes the application before the UI is created.
     *
     * Initialization steps:
     * 1. **Idle Timer**: Prevents screen from sleeping during video playback
     * 2. **DataStore**: Sets up persistent storage for settings and preferences
     * 3. **SwiftNIO Bridge**: Registers Swift-implemented network manager factory
     *    with Kotlin, enabling Kotlin code to instantiate SwiftNIO network connections
     *
     * The SwiftNIO factory bridge is necessary because SwiftNIO is pure Swift with
     * no Objective-C interoperability, making direct Kotlin/Native calls impossible.
     */
    init() {
        UIApplication.shared.isIdleTimerDisabled = true //Keep screen on

        DatastoreInitKt.initializeDS()

        // Bridge SwiftNIO network manager to Kotlin common code
        SwiftNioNetworkManagerKt.instantiateSwiftNioNetworkManager = { (roomViewmodel: RoomViewmodel) -> NetworkManager in
            return SwiftNioNetworkManager(viewmodel: roomViewmodel) as NetworkManager
        }
    }

    /**
     * The root SwiftUI scene containing the app's UI hierarchy.
     *
     * Displays the main Syncplay screen with safe area insets ignored to enable
     * fullscreen video playback that extends into system UI areas (notch, home indicator).
     */
    var body: some Scene {
        WindowGroup {
            SyncplayScreen().ignoresSafeArea(.all)
        }
    }
}