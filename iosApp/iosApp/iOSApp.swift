import SwiftUI
import shared

@main
struct iOSApp: App {
	
	@UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
	
	let d: Void = DatastoreUtilsKt.initializeDatastoreIOS()
	
	@State var isRoom = false
	
	var body: some Scene {
		WindowGroup {
			NavigationView {
				
				//if (!isRoom) {
				//    HomeScreen(joinRoomLambda: { self.isRoom = true } )
				//} else {
				WatchScreen()
					.ignoresSafeArea(.all)
					.onAppear {
						self.appDelegate.myOrientation = .landscape
						
						//UIDevice.current.setValue(UIInterfaceOrientation.landscapeLeft.rawValue, forKey: "orientation")
						//UIView.setAnimationsEnabled(true)
					}
					.onDisappear {
						self.appDelegate.myOrientation = .all
						
						//UIDevice.current.setValue(UIInterfaceOrientation.portrait.rawValue, forKey: "orientation")
						//UIView.setAnimationsEnabled(true)
					}
				//}
				
				//NavigationLink(
				//	destination: WatchScreen(),
				//	isActive: $isRoom
				//) {}
			}
		}
	}
}
