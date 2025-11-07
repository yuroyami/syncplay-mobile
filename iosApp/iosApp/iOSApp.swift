import SwiftUI
import shared

@main
struct iOSApp: App {
    
	init() {
		UIApplication.shared.isIdleTimerDisabled = true //Keep screen on
        
        DatastoreInitKt.initializeDS()
        
        SwiftNioNetworkManagerKt.instantiateSwiftNioNetworkManager = { (roomViewmodel: RoomViewmodel) -> NetworkManager in
            return SwiftNioNetworkManager(viewmodel: roomViewmodel) as NetworkManager
        }
	}
	
	var body: some Scene {
		WindowGroup {
            SyncplayScreen().ignoresSafeArea(.all)
		}
	}
}
