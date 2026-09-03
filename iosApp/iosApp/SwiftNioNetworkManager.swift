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
     the `channel` is retained; on failure the error is thrown, and the shared `connect()` turns
     it into `onConnectionFailed()`. Inbound bytes are line-framed before reaching this handler,
     with a 64 KiB cap so a peer that never sends a newline cannot grow the buffer forever.
     */
    override func connectSocket() async throws {
        let group = NIOTSEventLoopGroup()
        eventLoopGroup = group

        let host = self.viewmodel.session.serverHost
        let port = Int(self.viewmodel.session.serverPort)

        let result: EventLoopFuture<Channel> = NIOTSConnectionBootstrap(group: group)
        .connectTimeout(TimeAmount.seconds(10))
        .channelInitializer { channel in
            channel.pipeline.addHandler(ByteToMessageHandler(BoundedLineFrameDecoder(maxLength: 65536))).flatMap {
                channel.pipeline.addHandler(self)
            }
        }.connect(host: host, port: port)

        let connected = try await result.get()
        self.channel = connected
        print("Connected!")
    }

    /// Always `true`: SwiftNIO supports the TLS upgrade.
    override func supportsTLS() -> Bool {
        return true
    }

    /// Closes the channel and shuts down the event loop group without blocking the caller.
    override func terminateExistingConnection() {
        let closing = channel
        channel = nil
        closing?.close(promise: nil)
        eventLoopGroup?.shutdownGracefully { _ in }
        eventLoopGroup = nil
    }

    /// Writes a UTF-8 string and waits for the transport to accept it; a failed write throws so
    /// the shared retry and queue logic sees the real outcome.
    override func writeActualString(s: String) async throws {
        guard let channel = channel else {
            // asError() keeps the Kotlin type, so the shared retry logic can tell "no socket" apart.
            throw NetworkManagerSocketGoneException().asError()
        }

        let data = s.data(using: .utf8)!
        let buffer = channel.allocator.buffer(bytes: data)
        try await channel.writeAndFlush(buffer).get()
    }

    /**
     Upgrades the connection to TLS and awaits the handshake before returning. Inserts a
     `NIOSSLClientHandler` at the head of the pipeline plus a one-shot tracking handler that
     resolves once `TLSUserEvent.handshakeCompleted` fires. The caller
     (`RoomCallback.onReceivedTLS`) sends `Hello` immediately after this returns, so the channel
     must be fully ciphered by then. The certificate is checked against the host name the user
     typed (`session.tlsPeerHost`), which is also sent as SNI, not against the official server's
     name or the IP the socket dialled. A failure throws; the caller reports it.
     */
    override func upgradeTls() async throws {
        guard let channel = channel else { return }
        let configuration = TLSConfiguration.makeClientConfiguration()
        let sslContext = try NIOSSLContext(configuration: configuration)
        let peerHost = self.viewmodel.session.tlsPeerHost
        let tlsHandler = try NIOSSLClientHandler(context: sslContext, serverHostname: peerHost)

        let handshakePromise = channel.eventLoop.makePromise(of: Void.self)
        let trackingHandler = TLSHandshakeTrackingHandler(promise: handshakePromise)

        try await channel.pipeline.addHandler(tlsHandler, position: .first).get()
        try await channel.pipeline.addHandler(trackingHandler).get()
        try await handshakePromise.futureResult.get()
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

    /// The socket went away under us. Only the current channel counts: our own teardown of a
    /// previous socket is not news. During the handshake that is a server closing on us (a wrong
    /// password, for one), which used to leave the room stuck with no callback at all.
    func channelInactive(context: ChannelHandlerContext) {
        guard context.channel === channel else {
            context.fireChannelInactive()
            return
        }
        channel = nil
        let current = self.state.value as? ConnectionState
        if current == ConnectionState.connecting {
            viewmodel.callback.onConnectionFailed()
        } else if current == ConnectionState.connected {
            viewmodel.callback.onDisconnected()
        }
        context.fireChannelInactive()
    }

    func errorCaught(context: ChannelHandlerContext, error: Error) {
        print("Reader exception: \(error)")
        context.close(promise: nil)
    }
}

/**
 A line framer with a ceiling. `LineBasedFrameDecoder` buffers until it sees a newline, so a
 server that never sends one grows memory without bound; past `maxLength` bytes with no newline
 the connection is closed instead.
 */
private final class BoundedLineFrameDecoder: ByteToMessageDecoder {
    typealias InboundOut = ByteBuffer

    private let maxLength: Int

    init(maxLength: Int) {
        self.maxLength = maxLength
    }

    func decode(context: ChannelHandlerContext, buffer: inout ByteBuffer) throws -> DecodingState {
        guard let newlineIndex = buffer.readableBytesView.firstIndex(of: UInt8(ascii: "\n")) else {
            if buffer.readableBytes > maxLength {
                throw LineTooLongError(bytes: buffer.readableBytes)
            }
            return .needMoreData
        }
        // The view's indices are the buffer's own, so the line runs from the reader index to the newline.
        let lineLength = newlineIndex - buffer.readerIndex
        var line = buffer.readSlice(length: lineLength)!
        buffer.moveReaderIndex(forwardBy: 1)
        // Strip a trailing carriage return: the protocol delimits with CRLF.
        if line.readableBytesView.last == UInt8(ascii: "\r") {
            line = line.getSlice(at: line.readerIndex, length: line.readableBytes - 1)!
        }
        context.fireChannelRead(wrapInboundOut(line))
        return .continue
    }

    func decodeLast(context: ChannelHandlerContext, buffer: inout ByteBuffer, seenEOF: Bool) throws -> DecodingState {
        // A final line without a newline is not a complete protocol line; drop it.
        return .needMoreData
    }

    struct LineTooLongError: Error {
        let bytes: Int
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
