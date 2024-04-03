package com.yuroyami.syncplay.protocol

class SpProtocolApple {

    /** The implementation of the native iOS network client is done in pure Swift
     * using SwiftNIO due to its extreme resemblance with Java's Netty.
     * It's also as stable and completely reliable, unlike Ktor, which
     * keeps causing crashes (I suspect it still doesn't know how to
     * do memory management yet, or perhaps I haven't implemented it
     * correctly).
     *
     * You can find said SwiftNIO implementation in iosApp native code */
}