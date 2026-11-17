import NIOCore
import NIOEmbedded
import XCTest
@testable import NioConnectionHandlers

/// An error reaches the handshake tracker before the close, in the order the iOS pipeline uses.
final class ErrorThenCloseTests: XCTestCase {
    private struct TlsFailure: Error {}

    func testTheTrackerFailsWithTheErrorNotWithTheClose() throws {
        let channel = EmbeddedChannel()
        let promise = channel.eventLoop.makePromise(of: Void.self)
        var outcomes: [Result<Void, Error>] = []
        promise.futureResult.whenComplete { outcomes.append($0) }
        try channel.pipeline.syncOperations.addHandler(ErrorThenCloseHandler())
        try channel.pipeline.syncOperations.addHandler(TLSHandshakeTrackingHandler(promise: promise))
        try channel.connect(to: SocketAddress(ipAddress: "127.0.0.1", port: 8999)).wait()

        channel.pipeline.fireErrorCaught(TlsFailure())
        channel.embeddedEventLoop.run()

        XCTAssertEqual(outcomes.count, 1)
        guard case .failure(let error) = outcomes.first else { return XCTFail("expected a failure") }
        XCTAssertTrue(error is TlsFailure, "the tracker saw \(error), not the TLS error")
        XCTAssertFalse(channel.isActive, "the channel closed after the error")
    }
}
