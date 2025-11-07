import Foundation
import NIO
import NIOFoundationCompat
import NIOTransportServices
import NIOExtras
import NIOSSL
import Pods_iosApp
import shared

@preconcurrency
class SwiftNioNetworkManager: NetworkManager, ChannelInboundHandler, @unchecked Sendable {
    typealias InboundIn = ByteBuffer
    
    private var channel: Channel?
    private var eventLoopGroup: EventLoopGroup?
    
    //override let engine = SyncplayProtocol.NetworkEngine.swiftnio
    
    override init(viewmodel: RoomViewmodel) {
        super.init(viewmodel: viewmodel)
    }
    
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
    
    override func supportsTLS() -> Bool {
        return true
    }
    
    override func terminateExistingConnection() {
        try? channel?.close().wait()
        try? eventLoopGroup?.syncShutdownGracefully()
    
    }
    
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
    
    override func upgradeTls() {
        if (channel != nil) {
            do {
                let configuration = TLSConfiguration.makeClientConfiguration()
                let sslContext = try NIOSSLContext(configuration: configuration)
                let tlsHandler = try NIOSSLClientHandler(context: sslContext, serverHostname: "syncplay.pl")
                try channel?.pipeline.addHandler(tlsHandler, position: .first).wait()
            } catch {
                print("Error initializing TLS: \(error)")
                // Handle error appropriately (e.g., call onConnectionFailed())
                self.viewmodel.callbackManager.onConnectionFailed()
            }
        }
    }
    
    
    /** Channel handler stuff */
    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        var buffer = self.unwrapInboundIn(data)
        let readableBytes = buffer.readableBytes
        let data = buffer.readData(length: readableBytes)!
        
        if let received = String(data: data, encoding: .utf8) {
            self.handlePacket(data: received)
        }
    }
    
    func channelReadComplete(context: ChannelHandlerContext) {
        context.flush()
    }
    
    func errorCaught(context: ChannelHandlerContext, error: Error) {
        print("Reader exception: \(error)")
        //onError()
        //context.close(promise: nil)
    }
}
