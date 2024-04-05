import Foundation
import NIO
import NIOFoundationCompat
import NIOTransportServices
import NIOExtras
import shared

@objc class SpProtocolApple: SyncplayProtocol, ChannelInboundHandler {
    typealias InboundIn = ByteBuffer
    
    private var channel: Channel?
    private var eventLoopGroup: EventLoopGroup?
    
    //override let engine = SyncplayProtocol.NetworkEngine.swiftnio
    
    @objc override func connectSocket() {
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
    
    @objc override func isSocketValid() -> Bool {
        return channel?.isActive ?? false
    }
    
    @objc override func supportsTLS() -> Bool {
        return false
    }
    
    @objc override func endConnection(terminating: Bool) {
        try? channel?.close().wait()
        try? eventLoopGroup?.syncShutdownGracefully()
        
        if terminating {
            terminateScope()
        }
    }
    
    @objc override func writeActualString(s: String) {
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
    
    @objc override func upgradeTls() {
        // TLS setup for SwiftNIO
    }
    
    
    /** Channel handler stuff */
    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        var buffer = self.unwrapInboundIn(data)
        let readableBytes = buffer.readableBytes
        let data = buffer.readData(length: readableBytes)!
        
        if let received = String(data: data, encoding: .utf8) {
            self.jsonHandler.parse(protocol: self, json: received)
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
