import SwiftUI
import UIKit
import shared

struct WatchScreenCompose: UIViewControllerRepresentable {
	func makeUIViewController(context: Context) -> UIViewController {
		ScreensUtilsKt.WatchScreenControllerIOS()
	}
	
	func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct WatchScreen: View {
	var body: some View {
		WatchScreenCompose()
			.ignoresSafeArea(.keyboard) // Compose has own keyboard handler
	}
}

