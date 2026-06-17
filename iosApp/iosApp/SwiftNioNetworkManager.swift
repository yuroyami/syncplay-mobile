import Foundation
import NIO
import NIOFoundationCompat
import NIOTransportServices
import NIOExtras
import NIOSSL
import NIOTLS
import Pods_iosApp
import shared

/**
 iOS networking implementation backed by SwiftNIO. Subclasses the shared `NetworkManager`
 abstract class and implements `ChannelInboundHandler` to receive server data. Used as the
 default iOS client because it (unlike Ktor) supports the opportunistic TLS upgrade.
 */
@preconcurrency
class SwiftNioNetworkManager: NetworkManager, ChannelInboundHandler, @unchecked Sendable {
    typealias InboundIn = ByteBuffer

    private var channel: Channel?
    private var eventLoopGroup: EventLoopGroup?

    override init(viewmodel: RoomViewmodel) {
        super.init(viewmodel: viewmodel)
    }

    /**
     Opens a TCP connection to the Syncplay server with a 10-second connect timeout. On success
     the `channel` is retained; on failure `onConnectionFailed()` fires. Inbound bytes are
     line-framed (`LineBasedFrameDecoder`) before reaching this handler.
     */
    override func connectSocket() async throws {
        let group = NIOTSEventLoopGroup()
        eventLoopGroup = group

        let host = self.viewmodel.session.serverHost
        let port = Int(self.viewmodel.session.serverPort)

        let result: EventLoopFuture<Channel> = NIOTSConnectionBootstrap(group: group)
        .connectTimeout(TimeAmount.seconds(10))
        .channelInitializer { channel in
            channel.pipeline.addHandler(ByteToMessageHandler(LineBasedFrameDecoder())).flatMap {
                channel.pipeline.addHandler(self)
            }
        }.connect(host: host, port: port)

        result.whenSuccess { channel in
            self.channel = channel
            print("Connected!")
        }
        result.whenFailure { error in
            print(error)
            self.viewmodel.callback.onConnectionFailed()
        }

        do {
            _ = try await result.get()
        } catch {
            print("Connection error: \(error)")
            self.viewmodel.callback.onConnectionFailed()
        }
    }

    /// Always `true`: SwiftNIO supports the TLS upgrade.
    override func supportsTLS() -> Bool {
        return true
    }

    /// Closes the channel and shuts down the event loop group.
    override func terminateExistingConnection() {
        try? channel?.close().wait()
        try? eventLoopGroup?.syncShutdownGracefully()
    }

    /// Writes a UTF-8 string to the channel. Triggers `onDisconnected()` if the channel is
    /// unavailable or the write fails.
    override func writeActualString(s: String) async throws {
        guard let channel = channel else {
            viewmodel.callback.onDisconnected()
            return
        }

        let data = s.data(using: .utf8)!
        let buffer = channel.allocator.buffer(bytes: data)

        channel.writeAndFlush(buffer).whenComplete { result in
            do {
                try result.get()
            } catch {
                self.viewmodel.callback.onDisconnected()
                print("Error writing to channel: \(error)")
            }
        }
    }

    /**
     Upgrades the connection to TLS and awaits the handshake before returning. Inserts a
     `NIOSSLClientHandler` at the head of the pipeline plus a one-shot tracking handler that
     resolves once `TLSUserEvent.handshakeCompleted` fires. The caller
     (`RoomCallback.onReceivedTLS`) sends `Hello` immediately after this returns, so the channel
     must be fully ciphered by then. On failure, triggers `onConnectionFailed()`.
     */
    override func upgradeTls() async throws {
        guard let channel = channel else { return }
        do {
            let configuration = TLSConfiguration.makeClientConfiguration()
            let sslContext = try NIOSSLContext(configuration: configuration)
            let tlsHandler = try NIOSSLClientHandler(context: sslContext, serverHostname: "syncplay.pl")

            let handshakePromise = channel.eventLoop.makePromise(of: Void.self)
            let trackingHandler = TLSHandshakeTrackingHandler(promise: handshakePromise)

            try await channel.pipeline.addHandler(tlsHandler, position: .first).get()
            try await channel.pipeline.addHandler(trackingHandler).get()
            try await handshakePromise.futureResult.get()
        } catch {
            print("Error initializing TLS: \(error)")
            self.viewmodel.callback.onConnectionFailed()
            throw error
        }
    }


    // MARK: - Channel Handler Methods

    /// Decodes the inbound `ByteBuffer` as UTF-8 and forwards it to `handlePacket(jsonString:)`.
    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        var buffer = self.unwrapInboundIn(data)
        let readableBytes = buffer.readableBytes
        let data = buffer.readData(length: readableBytes)!

        if let received = String(data: data, encoding: .utf8) {
            self.handlePacket(jsonString: received)
        }
    }

    /// Flushes buffered data after a read cycle.
    func channelReadComplete(context: ChannelHandlerContext) {
        context.flush()
    }

    func errorCaught(context: ChannelHandlerContext, error: Error) {
        print("Reader exception: \(error)")
    }
}

/**
 One-shot handler that resolves `promise` once the TLS handshake fires
 `TLSUserEvent.handshakeCompleted`, then removes itself from the pipeline. Lets
 `SwiftNioNetworkManager.upgradeTls` await the handshake instead of firing and forgetting.
 */
private final class TLSHandshakeTrackingHandler: ChannelInboundHandler, RemovableChannelHandler {
    typealias InboundIn = NIOAny
    private let promise: EventLoopPromise<Void>

    init(promise: EventLoopPromise<Void>) {
        self.promise = promise
    }

    func userInboundEventTriggered(context: ChannelHandlerContext, event: Any) {
        // SwiftNIO ≥ 2.55 changed `.handshakeCompleted` from a plain enum case to one
        // with an associated `negotiatedProtocol: String?` payload, so we pattern-match
        // instead of using `==`.
        if let tlsEvent = event as? TLSUserEvent, case .handshakeCompleted = tlsEvent {
            promise.succeed(())
            context.pipeline.removeHandler(self, promise: nil)
        }
        context.fireUserInboundEventTriggered(event)
    }

    func errorCaught(context: ChannelHandlerContext, error: Error) {
        promise.fail(error)
        context.fireErrorCaught(error)
    }
}
