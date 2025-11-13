import Foundation
import NIO
import NIOFoundationCompat
import NIOTransportServices
import NIOExtras
import NIOSSL
import Pods_iosApp
import shared

/**
 A network manager that uses SwiftNIO for handling asynchronous TCP connections.

 The `SwiftNioNetworkManager` class establishes and manages a network connection
 using SwiftNIO’s event-driven architecture. It implements `ChannelInboundHandler`
 to receive data from the server and conforms to `NetworkManager` abstract class for shared
 protocol-level operations across platforms.

 - Note: This class integrates with the Kotlin Multiplatform `shared` module,
   providing the iOS-side networking implementation for Syncplay.
 */
@preconcurrency
class SwiftNioNetworkManager: NetworkManager, ChannelInboundHandler, @unchecked Sendable {
    /// Just a typealias for the inbound data type handled by this class.
    typealias InboundIn = ByteBuffer

    /// The active network channel for the current connection.
    private var channel: Channel?

    /// The event loop group managing asynchronous tasks for the connection.
    private var eventLoopGroup: EventLoopGroup?

    // override let engine = SyncplayProtocol.NetworkEngine.swiftnio //This is already defaulted in shared module

    /**
     Creates a new `SwiftNioNetworkManager` instance.

     - Parameter viewmodel: The `RoomViewmodel` instance used for managing session state and callbacks.
     */
    override init(viewmodel: RoomViewmodel) {
        super.init(viewmodel: viewmodel)
    }

    /**
     Establishes a TCP connection to the Syncplay server using SwiftNIO.

     This method creates a new `NIOTSEventLoopGroup`, configures a connection bootstrap,
     and connects to the server specified by the view model.
     As you can see, it's very similar to Netty on JVM. In fact, it is both inspired by Netty and
     maintained by some of Netty's original maintainers :)

     - Throws: An error if the connection could not be established within the timeout period.

     - Discussion:
       Upon successful connection, the `channel` is stored for future communication.
       If the connection fails, the method triggers the `onConnectionFailed()` callback.
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
            self.viewmodel.callbackManager.onConnectionFailed()
        }

        do {
            _ = try await result.get()
        } catch {
            print("Connection error: \(error)")
            self.viewmodel.callbackManager.onConnectionFailed()
        }
    }

    /**
     Indicates whether this network manager supports TLS connections.

     - Returns: Always `true` for this native iOS implementation.
     */
    override func supportsTLS() -> Bool {
        return true
    }

    /**
     Terminates any active connection and gracefully shuts down the event loop.

     - Discussion:
       This method closes the current `channel` and stops the associated
       `eventLoopGroup` to free system resources.
     */
    override func terminateExistingConnection() {
        try? channel?.close().wait()
        try? eventLoopGroup?.syncShutdownGracefully()
    }

    /**
     Writes a UTF-8 string to the active network channel.

     - Parameter s: The string to send to the server.

     - Throws: An error if the channel is not available or the write operation fails.

     - Discussion:
       This method encodes the string as UTF-8 data and writes it to the
       underlying NIO channel. If the channel becomes unavailable during
       the operation, it triggers the `onDisconnected()` callback.
     */
    override func writeActualString(s: String) async throws {
        guard let channel = channel else {
            viewmodel.callbackManager.onDisconnected()
            return
        }

        let data = s.data(using: .utf8)!
        let buffer = channel.allocator.buffer(bytes: data)

        channel.writeAndFlush(buffer).whenComplete { result in
            do {
                try result.get()
            } catch {
                self.viewmodel.callbackManager.onDisconnected()
                print("Error writing to channel: \(error)")
            }
        }
    }

    /**
     Upgrades the existing TCP connection to use TLS encryption.

     - Discussion:
       This method configures a new TLS client context using the default client
       configuration and inserts a `NIOSSLClientHandler` at the start of the
       channel pipeline.

       If initialization fails, it triggers the `onConnectionFailed()` callback.
     */
    override func upgradeTls() {
        if (channel != nil) {
            do {
                let configuration = TLSConfiguration.makeClientConfiguration()
                let sslContext = try NIOSSLContext(configuration: configuration)
                let tlsHandler = try NIOSSLClientHandler(context: sslContext, serverHostname: "syncplay.pl")
                try channel?.pipeline.addHandler(tlsHandler, position: .first).wait()
            } catch {
                print("Error initializing TLS: \(error)")
                self.viewmodel.callbackManager.onConnectionFailed()
            }
        }
    }


    // MARK: - Channel Handler Methods

    /**
     Handles inbound data received from the network channel.

     - Parameters:
        - context: The current channel handler context.
        - data: The data received, wrapped in a `NIOAny` container.

     - Discussion:
       This method decodes the incoming `ByteBuffer` into a UTF-8 string and
       forwards it to `handlePacket(data:)` for further processing.
     */
    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        var buffer = self.unwrapInboundIn(data)
        let readableBytes = buffer.readableBytes
        let data = buffer.readData(length: readableBytes)!

        if let received = String(data: data, encoding: .utf8) {
            self.handlePacket(data: received)
        }
    }

    /**
     Called when all pending read operations are complete.

     - Parameter context: The current channel handler context.

     - Discussion:
       This method ensures that all buffered data is flushed after a read cycle.
     */
    func channelReadComplete(context: ChannelHandlerContext) {
        context.flush()
    }

    /**
     Handles errors that occur during channel operations.

     - Parameters:
        - context: The current channel handler context.
        - error: The error that occurred.

     - Discussion:
       This method logs the error and may trigger recovery or cleanup actions.
     */
    func errorCaught(context: ChannelHandlerContext, error: Error) {
        print("Reader exception: \(error)")
        // context.close(promise: nil)
    }
}
