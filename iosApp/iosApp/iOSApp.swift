import SwiftUI
import shared

@main
struct iOSApp: App {
	
	@UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
	
	let d: Void = DatastoreUtilsKt.initializeDatastoreIOS()
	
	@State var isRoom = false
	
	init() {
		UIApplication.shared.isIdleTimerDisabled = true //Keep screen on
	}
	
	var body: some Scene {
		WindowGroup {
			NavigationView {
				ZStack {
					
					NavigationLink(
						destination: WatchScreen().ignoresSafeArea(.all).navigationBarBackButtonHidden(true)
							.onAppear {
								self.appDelegate.myOrientation = .landscape
								
								UIDevice.current.setValue(UIInterfaceOrientation.landscapeLeft.rawValue, forKey: "orientation")
								UIView.setAnimationsEnabled(true)
							}
							.onDisappear {
								
								self.appDelegate.myOrientation = .all
								
								UIDevice.current.setValue(UIInterfaceOrientation.portrait.rawValue, forKey: "orientation")
								UIView.setAnimationsEnabled(true)
							},
						isActive: $isRoom
					) {}
					if (!isRoom) {
						HomeScreen(joinRoomLambda: { self.isRoom = true } ).ignoresSafeArea(.all)
					}
				}
				
			}
		}
	}
}
