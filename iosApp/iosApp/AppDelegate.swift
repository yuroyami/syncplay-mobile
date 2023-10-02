import Foundation
import SwiftUI

class AppDelegate: NSObject, UIApplicationDelegate {
	//Orientation Variables
	var myOrientation: UIInterfaceOrientationMask = .portrait
	
	func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
		return myOrientation
	}
	
}
