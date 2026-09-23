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
 The iOS network client, built on SwiftNIO. It subclasses the shared `NetworkManager` and
 implements `ChannelInboundHandler` to receive server data. It is the default iOS client because,
 unlike Ktor, it supports the TLS upgrade: a Syncplay connection starts in plain text and then
 switches the same socket to TLS.
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
     Opens a TCP connection to the Syncplay server, with a 10-second connect timeout. On success
     it keeps the `channel`. On failure it throws, and the shared `connect()` turns the error into
     `onConnectionFailed()`. Inbound bytes are split into lines before they reach this handler,
     with a 64 KiB limit, so a peer that never sends a newline cannot grow the buffer forever.
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

    /// Writes a UTF-8 string and waits until the transport accepts it. A failed write throws, so
    /// the shared retry and queue logic sees the real result.
    override func writeActualString(s: String) async throws {
        guard let channel = channel else {
            // asError() keeps the Kotlin type, so the shared retry logic can tell "no socket" apart.
            throw NetworkManager.SocketGoneException().asError()
        }

        let data = s.data(using: .utf8)!
        let buffer = channel.allocator.buffer(bytes: data)
        try await channel.writeAndFlush(buffer).get()
    }

    /**
     Upgrades the connection to TLS and waits for the handshake before it returns. It inserts a
     `NIOSSLClientHandler` at the head of the pipeline, plus a one-shot tracking handler that
     settles when the handshake ends. The caller (`RoomCallback.onReceivedTLS`) sends `Hello`
     right after this returns, so the channel must be fully encrypted by then. The certificate is
     checked against the host name the user typed (`session.tlsPeerHost`), which is also sent as
     SNI. It is not checked against the official server's name or the IP that the socket dialled.
     A failure throws, and the caller reports it.
     */
    override func upgradeTls() async throws {
        // Without a channel there is nothing to upgrade, so throw. A plain return reads as a
        // finished handshake: the caller then sets `encrypted` and shows the lock on a plain socket.
        guard let channel = channel else {
            throw NetworkManager.SocketGoneException().asError()
        }
        let configuration = TLSConfiguration.makeClientConfiguration()
        let sslContext = try NIOSSLContext(configuration: configuration)
        let peerHost = self.viewmodel.session.tlsPeerHost
        let tlsHandler = try NIOSSLClientHandler(context: sslContext, serverHostname: peerHost)

        let handshakePromise = channel.eventLoop.makePromise(of: Void.self)
        let trackingHandler = TLSHandshakeTrackingHandler(promise: handshakePromise)

        // Add the tracking handler before the TLS handler. Added after it, the tracker can miss a
        // handshake that already finished or failed, and the promise then only ends at the timeout.
        try await channel.pipeline.addHandler(trackingHandler).get()
        try await channel.pipeline.addHandler(tlsHandler, position: .first).get()

        // A server that accepts the socket and then sends nothing must not block this call forever.
        let deadline = channel.eventLoop.scheduleTask(in: .seconds(15)) { trackingHandler.timedOut() }
        defer { deadline.cancel() }
        try await handshakePromise.futureResult.get()
    }


    // MARK: - Channel Handler Methods

    /// Decodes the inbound `ByteBuffer` as UTF-8 and forwards it to `handlePacket(jsonString:)`.
    /// Only the current channel counts. A line from a socket that was already replaced belongs to
    /// a room this client has left, so it must not be answered.
    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        guard context.channel === channel else { return }
        var buffer = self.unwrapInboundIn(data)
        // readString decodes straight from the buffer. A detour through Data would copy every
        // inbound line twice before anything reads it.
        if let received = buffer.readString(length: buffer.readableBytes) {
            self.handlePacket(jsonString: received)
        }
    }

    /// Flushes buffered data after a read cycle.
    func channelReadComplete(context: ChannelHandlerContext) {
        context.flush()
    }

    /// Called when the socket closes. Only the current channel counts, because the teardown of an
    /// old socket is expected. A close during the handshake means the server closed the connection
    /// (after a wrong password, for example). That case must report `onConnectionFailed()`, or the
    /// room stays stuck with no callback at all.
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
        // The TLS handshake tracker sits after this handler. Pass the error on before the close,
        // so the tracker fails the handshake with the real error and not with a plain end-of-file.
        context.fireErrorCaught(error)
        context.close(promise: nil)
    }
}

/**
 Splits inbound bytes into lines, with a size limit. NIO's `LineBasedFrameDecoder` buffers until
 it sees a newline, so a server that never sends one grows memory without limit. This decoder
 throws when a line passes `maxLength` bytes, and the error closes the connection.
 */
private final class BoundedLineFrameDecoder: ByteToMessageDecoder {
    typealias InboundOut = ByteBuffer

    private let maxLength: Int

    /**
     How many bytes past the reader index have already been searched. `decode` is called again on
     every socket read with everything buffered so far, so without this a message that arrives in
     forty segments is scanned from the top forty times. NIO's own `LineBasedFrameDecoder` keeps
     the same offset for the same reason.
     */
    private var scannedBytes: Int = 0

    init(maxLength: Int) {
        self.maxLength = maxLength
    }

    func decode(context: ChannelHandlerContext, buffer: inout ByteBuffer) throws -> DecodingState {
        let view = buffer.readableBytesView
        let alreadyScanned = min(scannedBytes, view.count)
        let searchFrom = view.index(view.startIndex, offsetBy: alreadyScanned)
        guard let newlineIndex = view[searchFrom...].firstIndex(of: UInt8(ascii: "\n")) else {
            scannedBytes = view.count
            if buffer.readableBytes > maxLength {
                throw LineTooLongError(bytes: buffer.readableBytes)
            }
            return .needMoreData
        }
        scannedBytes = 0
        // The view's indices are the buffer's own, so the line runs from the reader index to
        // the newline.
        let lineLength = newlineIndex - buffer.readerIndex
        // A complete line can also be too long: the limit is on the size of one message,
        // whether or not its newline has arrived.
        if lineLength > maxLength {
            throw LineTooLongError(bytes: lineLength)
        }
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
 One-shot handler that settles `promise` when the TLS handshake ends, so
 `SwiftNioNetworkManager.upgradeTls` can wait for it. `TLSUserEvent.handshakeCompleted` succeeds
 the promise and removes this handler from the pipeline. An error, a channel close, the handler's
 removal or the timeout fails it.
 */
private final class TLSHandshakeTrackingHandler: ChannelInboundHandler, RemovableChannelHandler {
    typealias InboundIn = NIOAny
    private let promise: EventLoopPromise<Void>
    private var settled = false

    struct HandshakeTimedOut: Error {}

    init(promise: EventLoopPromise<Void>) {
        self.promise = promise
    }

    /// Settles the promise. Every exit path calls this, and only the first call counts.
    private func settle(_ result: Result<Void, Error>) {
        guard !settled else { return }
        settled = true
        promise.completeWith(result)
    }

    func timedOut() {
        settle(.failure(HandshakeTimedOut()))
    }

    func userInboundEventTriggered(context: ChannelHandlerContext, event: Any) {
        // `.handshakeCompleted` carries a `negotiatedProtocol: String?` payload, so match the
        // case with a pattern instead of `==`.
        if let tlsEvent = event as? TLSUserEvent, case .handshakeCompleted = tlsEvent {
            settle(.success(()))
            context.pipeline.removeHandler(self, promise: nil)
        }
        context.fireUserInboundEventTriggered(event)
    }

    func errorCaught(context: ChannelHandlerContext, error: Error) {
        settle(.failure(error))
        context.fireErrorCaught(error)
    }

    /// The channel closed without ever completing a handshake.
    func channelInactive(context: ChannelHandlerContext) {
        settle(.failure(ChannelError.eof))
        context.fireChannelInactive()
    }

    /// The handler left the pipeline before the handshake ended. Nothing else reports that case.
    func handlerRemoved(context: ChannelHandlerContext) {
        settle(.failure(ChannelError.ioOnClosedChannel))
    }
}
