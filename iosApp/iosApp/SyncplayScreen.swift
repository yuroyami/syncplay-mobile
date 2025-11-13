import SwiftUI
import UIKit
import shared

/**
 The one and only SyncplayScreen.

 This view serves as the main (and only) entry point for the Syncplay feature on iOS.
 It embeds the shared Compose-based Syncplay UI inside a SwiftUI container,
 occupying the entire screen.
 */
struct SyncplayScreen: View {

    /// The content and behavior of the view.
    var body: some View {
        SyncplayCompose()
            .ignoresSafeArea(.all)
    }
}

/**
 A representable structure that hosts the Kotlin Multiplatform Compose UI for Syncplay.

 Use `SyncplayCompose` to integrate the shared Syncplay interface—implemented
 using Kotlin Compose Multiplatform—into a SwiftUI-based iOS app.
 */
struct SyncplayCompose: UIViewControllerRepresentable {

    /**
     Creates the `UIViewController` object that manages the Syncplay Compose UI.
     */
    func makeUIViewController(context: Context) -> UIViewController {
        IOSAppKt.SyncplayController()
    }

    /**
     Updates the specified `UIViewController` with new information from SwiftUI.
     */
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
