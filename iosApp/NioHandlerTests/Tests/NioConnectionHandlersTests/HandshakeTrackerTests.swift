import NIOCore
import NIOEmbedded
import NIOTLS
import XCTest
@testable import NioConnectionHandlers

/// The handshake tracker settles its promise exactly once, whichever way the handshake ends.
final class HandshakeTrackerTests: XCTestCase {
    private struct Refused: Error {}

    private var channel: EmbeddedChannel!
    private var tracker: TLSHandshakeTrackingHandler!
    private var outcomes: [Result<Void, Error>] = []

    override func setUp() {
        channel = EmbeddedChannel()
        let promise = channel.eventLoop.makePromise(of: Void.self)
        promise.futureResult.whenComplete { self.outcomes.append($0) }
        tracker = TLSHandshakeTrackingHandler(promise: promise)
        XCTAssertNoThrow(try channel.pipeline.syncOperations.addHandler(tracker))
        outcomes = []
    }

    override func tearDown() {
        _ = try? channel.finish()
    }

    func testAHandshakeErrorFailsItWithThatError() {
        channel.pipeline.fireErrorCaught(Refused())
        // The close that follows an error must not settle it a second time.
        channel.pipeline.fireChannelInactive()
        XCTAssertEqual(outcomes.count, 1)
        guard case .failure(let error) = outcomes.first else { return XCTFail("expected a failure") }
        XCTAssertTrue(error is Refused, "got \(error)")
    }

    func testAChannelThatClosesBeforeTheHandshakeFailsIt() {
        channel.pipeline.fireChannelInactive()
        XCTAssertEqual(outcomes.count, 1)
        guard case .failure(let error) = outcomes.first else { return XCTFail("expected a failure") }
        XCTAssertEqual(error as? ChannelError, .eof)
    }

    func testRemovalBeforeTheHandshakeFailsIt() throws {
        try channel.pipeline.syncOperations.removeHandler(tracker).wait()
        XCTAssertEqual(outcomes.count, 1)
        guard case .failure(let error) = outcomes.first else { return XCTFail("expected a failure") }
        XCTAssertEqual(error as? ChannelError, .ioOnClosedChannel)
    }

    func testATimeoutFailsItAndALateHandshakeChangesNothing() {
        tracker.timedOut()
        channel.pipeline.fireUserInboundEventTriggered(TLSUserEvent.handshakeCompleted(negotiatedProtocol: nil))
        XCTAssertEqual(outcomes.count, 1)
        guard case .failure(let error) = outcomes.first else { return XCTFail("expected a failure") }
        XCTAssertTrue(error is TLSHandshakeTrackingHandler.HandshakeTimedOut, "got \(error)")
    }

    func testACompletedHandshakeSucceedsAndLeavesThePipeline() throws {
        channel.pipeline.fireUserInboundEventTriggered(TLSUserEvent.handshakeCompleted(negotiatedProtocol: nil))
        channel.embeddedEventLoop.run()
        XCTAssertEqual(outcomes.count, 1)
        guard case .success = outcomes.first else { return XCTFail("expected a success") }
        XCTAssertThrowsError(try channel.pipeline.syncOperations.handler(type: TLSHandshakeTrackingHandler.self))
    }
}
