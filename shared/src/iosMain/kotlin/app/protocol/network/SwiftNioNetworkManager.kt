package app.protocol.network

import app.room.RoomViewmodel

/**
 * Factory function reference for instantiating the SwiftNIO-based network manager on iOS.
 *
 * ## Why SwiftNIO?
 * The iOS implementation uses SwiftNIO, a native high-performance networking framework
 * written entirely in Swift.
 *
 * ## Why Not Ktor on iOS?
 * Ktor's iOS implementation has shown several critical issues:
 * - Frequent crashes and instability
 * - No StartTLS support
 *
 * ## Implementation Architecture
 * The actual SwiftNIO implementation can be found at:
 * ```
 * iosApp/iosApp/SwiftNioNetworkManager.swift
 * ```
 *
 * ## Usage
 * This function reference is initialized by the Swift side during app startup:
 * ```swift
 * instantiateSwiftNioNetworkManager = { viewmodel in
 *     // Swift-implemented NetworkManager instance
 * }
 * ```
 *
 * @see NetworkManager
 */
var instantiateSwiftNioNetworkManager: ((roomViewmodel: RoomViewmodel) -> NetworkManager)? = null