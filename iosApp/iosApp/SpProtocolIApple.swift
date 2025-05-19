import Foundation
import NIO
import NIOFoundationCompat
import NIOTransportServices
import NIOExtras
import NIOSSL
import shared

class SpProtocolApple: SyncplayProtocol, ChannelInboundHandler {
    typealias InboundIn = ByteBuffer
    
    private var channel: Channel?
    private var eventLoopGroup: EventLoopGroup?
    
    //override let engine = SyncplayProtocol.NetworkEngine.swiftnio
    
    override func connectSocket() {
        let group = NIOTSEventLoopGroup()
        eventLoopGroup = group
        
        let host = session.serverHost
        let port = Int(session.serverPort)
        
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
            self.syncplayCallback?.onConnectionFailed()
        }
        
        do {
            _ = try result.wait()
        } catch {
            print("Connection error: \(error)")
            self.syncplayCallback?.onConnectionFailed()
        }
        
    }
    
    override func isSocketValid() -> Bool {
        return channel?.isActive ?? false
    }
    
    override func supportsTLS() -> Bool {
        return true
    }
    
    override func endConnection(terminating: Bool) {
        try? channel?.close().wait()
        try? eventLoopGroup?.syncShutdownGracefully()
        
        if terminating {
            terminateScope()
        }
    }
    
    override func writeActualString(s: String) {
        guard let channel = channel else {
            syncplayCallback?.onDisconnected()
            return
        }
        
        let data = s.data(using: .utf8)!
        let buffer = channel.allocator.buffer(bytes: data)
        
        channel.writeAndFlush(buffer).whenComplete { result in
            do {
                try result.get()
            } catch {
                self.syncplayCallback?.onDisconnected()
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
                self.syncplayCallback?.onConnectionFailed()
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
