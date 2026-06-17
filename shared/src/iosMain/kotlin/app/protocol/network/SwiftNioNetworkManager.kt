package app.protocol.network

import app.room.RoomViewmodel

/**
 * Factory hook for the SwiftNIO-based [NetworkManager] on iOS — the default iOS engine.
 *
 * SwiftNIO is used over Ktor's iOS transport because Ktor on iOS is unstable and offers
 * no StartTLS support, which the Syncplay protocol's opportunistic TLS upgrade requires.
 *
 * The implementation lives in Swift at `iosApp/iosApp/SwiftNioNetworkManager.swift` and
 * assigns this reference during app startup; it stays null until then.
 *
 * @see NetworkManager
 */
var instantiateSwiftNioNetworkManager: ((roomViewmodel: RoomViewmodel) -> NetworkManager)? = null