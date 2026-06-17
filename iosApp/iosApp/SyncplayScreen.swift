import SwiftUI
import UIKit
import shared

/// Fullscreen SwiftUI host for the shared Compose Syncplay UI; the app's only screen.
struct SyncplayScreen: View {

    var body: some View {
        SyncplayCompose()
            .ignoresSafeArea(.all)
    }
}

/// Bridges the Compose Multiplatform UI (`IOSAppKt.SyncplayController()`) into SwiftUI.
struct SyncplayCompose: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        IOSAppKt.SyncplayController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
