package com.yuroyami.syncplay.protocol

class SpProtocolApple {

    /** The implementation of the native iOS network client is done in pure Swift
     * using SwiftNIO due to its extreme resemblance with Java's Netty.
     * It's also as stable and completely reliable, and supports TLS.
     *
     * Unlike Ktor, which is contrary to all above:
     * keeps causing crashes, doesn't support TLS, terrible concurrency...etc
     *
     * You can find said SwiftNIO implementation in iosApp native code */
}