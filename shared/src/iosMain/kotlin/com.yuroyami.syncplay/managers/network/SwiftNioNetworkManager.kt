package com.yuroyami.syncplay.managers.network

import com.yuroyami.syncplay.managers.NetworkManager
import com.yuroyami.syncplay.viewmodels.RoomViewmodel

/**
 * The iOS implementation of the network engine uses SwiftNIO,
 * a native iOS network client implemented entirely in pure Swift.
 *
 * Thanks to its strong resemblance to Java's Netty,
 * SwiftNIO is highly stable, fully reliable, and includes built-in TLS support.
 * In contrast, Ktor on iOS has shown several issues: it frequently crashes,
 * lacks proper TLS support, and suffers from poor concurrency handling.
 *
 * The SwiftNIO-based implementation can be found in the native iOS module.
 *
 * Unfortunately, SwiftNIO is a pure Swift framework with no Objective-C
 * interoperability, making it impossible to call directly from Kotlin.
 *
 * See: ```iosApp/iosApp/SwiftNioNetworkManager.swift```
 */
var instantiateSwiftNioNetworkManager: ((roomViewmodel: RoomViewmodel) -> NetworkManager)? = null
