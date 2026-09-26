import NIOCore
import NIOEmbedded
import XCTest
@testable import NioConnectionHandlers

/// The line decoder splits the protocol's lines and refuses one that passes its size limit.
final class LineDecoderTests: XCTestCase {
    private func channel(maxLength: Int = 16) throws -> EmbeddedChannel {
        let channel = EmbeddedChannel()
        try channel.pipeline.syncOperations.addHandler(ByteToMessageHandler(BoundedLineFrameDecoder(maxLength: maxLength)))
        return channel
    }

    private func lines(_ channel: EmbeddedChannel) throws -> [String] {
        var out: [String] = []
        while let line = try channel.readInbound(as: ByteBuffer.self) {
            out.append(String(buffer: line))
        }
        return out
    }

    func testLinesArriveWithoutTheirLineEnds() throws {
        let channel = try channel()
        try channel.writeInbound(ByteBuffer(string: "{\"a\":1}\r\n{\"b\":2}\n"))
        XCTAssertEqual(try lines(channel), ["{\"a\":1}", "{\"b\":2}"])
        _ = try channel.finish()
    }

    func testALineInPiecesIsOneLine() throws {
        let channel = try channel()
        try channel.writeInbound(ByteBuffer(string: "{\"he"))
        try channel.writeInbound(ByteBuffer(string: "llo\":1}"))
        XCTAssertEqual(try lines(channel), [])
        try channel.writeInbound(ByteBuffer(string: "\r\n"))
        XCTAssertEqual(try lines(channel), ["{\"hello\":1}"])
        _ = try channel.finish()
    }

    func testAnEndlessLineIsRefusedBeforeItsNewline() throws {
        let channel = try channel()
        XCTAssertThrowsError(try channel.writeInbound(ByteBuffer(string: String(repeating: "x", count: 17)))) { error in
            XCTAssertTrue(error is BoundedLineFrameDecoder.LineTooLongError, "got \(error)")
        }
        _ = try? channel.finish()
    }

    func testACompleteLineOverTheLimitIsRefused() throws {
        let channel = try channel()
        XCTAssertThrowsError(try channel.writeInbound(ByteBuffer(string: String(repeating: "x", count: 17) + "\n"))) { error in
            XCTAssertTrue(error is BoundedLineFrameDecoder.LineTooLongError, "got \(error)")
        }
        _ = try? channel.finish()
    }
}
