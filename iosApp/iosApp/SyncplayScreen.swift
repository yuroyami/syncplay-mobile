import SwiftUI
import UIKit
import shared

struct SyncplayScreen: View {
    var body: some View {
        SyncplayCompose()
            .ignoresSafeArea(.all)
        
    }
}

struct SyncplayCompose: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        ScreensUtilsKt.SyncplayController()
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
