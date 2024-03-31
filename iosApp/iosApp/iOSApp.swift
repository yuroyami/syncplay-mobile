import SwiftUI
import shared

@main
struct iOSApp: App {
    
	init() {
		UIApplication.shared.isIdleTimerDisabled = true //Keep screen on
        
        DatastoreUtilsKt.initializeDatastoreIOS()
        
        SomeMoreUtilsKt.instantiateSyncplayProtocolSwiftNIO = {
            return SpProtocolApple()
        }
	}
	
	var body: some Scene {
		WindowGroup {
            SyncplayScreen().ignoresSafeArea(.all)
		}
	}
}
