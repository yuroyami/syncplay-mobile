import NIO
import NIOTLS

// The two connection handlers that SwiftNioNetworkManager puts into its pipeline. They import
// nothing but SwiftNIO, so the package in iosApp/NioHandlerTests tests them on a Mac with
// `swift test`, with no simulator.

/**
 Splits inbound bytes into lines, with a size limit. NIO's `LineBasedFrameDecoder` buffers until
 it sees a newline, so a server that never sends one grows memory without limit. This decoder
 throws when a line passes `maxLength` bytes, and the error closes the connection.
 */
final class BoundedLineFrameDecoder: ByteToMessageDecoder {
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
final class TLSHandshakeTrackingHandler: ChannelInboundHandler, RemovableChannelHandler {
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
