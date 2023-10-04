import SwiftUI
import UIKit
import shared

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
		ScreensUtilsKt.HomeScreenControllerIOS(joinRoomLambda: joinRoomLambda)
	}
	
	func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
