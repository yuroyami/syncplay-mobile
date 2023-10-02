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

class AppDelegate: NSObject, UIApplicationDelegate {
	//Orientation Variables
	var myOrientation: UIInterfaceOrientationMask = .portrait
	
	func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
		return myOrientation
	}
	
}

struct HomeScreen: View {
	
	@State private var isWatchScreenActive = false
	
	let joinRoomLambda: () -> Void
	
	var body: some View {
		HomeScreenCompose(joinRoomLambda: joinRoomLambda)
			.ignoresSafeArea(.keyboard) // Compose has own keyboard handler
		
	}
}

struct HomeScreenCompose: UIViewControllerRepresentable {
	
	let joinRoomLambda: () -> Void
	
	func makeUIViewController(context: Context) -> UIViewController {
		GeneralUtilsKt.HomeScreenControllerIOS(joinRoomLambda: joinRoomLambda)
	}
	
	func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}


struct WatchScreenCompose: UIViewControllerRepresentable {
	func makeUIViewController(context: Context) -> UIViewController {
		GeneralUtilsKt.WatchScreenControllerIOS()
	}
	
	func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct WatchScreen: View {
	var body: some View {
		WatchScreenCompose()
			.ignoresSafeArea(.keyboard) // Compose has own keyboard handler
	}
}

