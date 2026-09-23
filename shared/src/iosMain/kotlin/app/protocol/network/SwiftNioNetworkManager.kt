package app.protocol.network

import app.room.RoomViewmodel

/**
 * Factory for the SwiftNIO-based [NetworkManager], the default iOS network engine.
 *
 * SwiftNIO is used instead of Ktor's iOS transport because Ktor on iOS is unstable and has no
 * StartTLS support. The Syncplay protocol needs StartTLS: a connection starts in plain text and
 * then upgrades the same socket to TLS.
 *
 * The implementation lives in Swift, in `iosApp/iosApp/SwiftNioNetworkManager.swift`. It sets
 * this reference during app startup, and the reference stays null until then.
 *
 * @see NetworkManager
 */
var instantiateSwiftNioNetworkManager: ((roomViewmodel: RoomViewmodel) -> NetworkManager)? = null