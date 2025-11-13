package com.yuroyami.syncplay.managers.network

import com.yuroyami.syncplay.viewmodels.RoomViewmodel

/**
 * Factory function reference for instantiating the SwiftNIO-based network manager on iOS.
 *
 * ## Why SwiftNIO?
 * The iOS implementation uses SwiftNIO, a native high-performance networking framework
 * written entirely in Swift. SwiftNIO was chosen over Ktor for iOS due to:
 *
 * - **Stability**: Highly stable and battle-tested in production iOS apps
 * - **Reliability**: Fully reliable with robust error handling
 * - **TLS Support**: Built-in, production-ready TLS/SSL encryption
 * - **Similarity to Netty**: API design closely resembles Netty, making it familiar
 *
 * ## Why Not Ktor on iOS?
 * Ktor's iOS implementation has shown several critical issues:
 * - Frequent crashes and instability
 * - Inadequate TLS support
 * - Poor concurrency handling
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