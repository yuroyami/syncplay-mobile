import CryptoKit
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
     A certificate that the system refuses still passes when the person trusted its fingerprint for
     this address. Otherwise the fingerprint goes to the room, which asks the person about it. A
     failure throws, and the caller reports it.
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
        let pinned = self.pinnedCertificateFingerprint()
        let tlsHandler = try NIOSSLClientHandler(context: sslContext, serverHostname: peerHost) { [weak self] certificates, promise in
            guard let leaf = certificates.first, let der = try? leaf.toDERBytes() else {
                promise.succeed(.failed)
                return
            }
            let fingerprint = SwiftNioNetworkManager.fingerprint(of: der)
            SwiftNioNetworkManager.systemTrusts(certificates, host: peerHost) { trusted in
                if trusted || fingerprint == pinned {
                    promise.succeed(.certificateVerified)
                } else {
                    self?.reportUntrustedCertificate(fingerprint: fingerprint)
                    promise.succeed(.failed)
                }
            }
        }

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


    /// The SHA-256 of a certificate's DER bytes, as colon-separated pairs of uppercase hex. It must
    /// match `CertificatePins.fingerprintOf` in the shared code, which the pins are compared with.
    private static func fingerprint(of der: [UInt8]) -> String {
        SHA256.hash(data: der).map { String(format: "%02X", $0) }.joined(separator: ":")
    }

    /// The system's own trust decision for `certificates`, with the host name check for `host`. A
    /// custom check replaces the TLS library's default one, so this does the default one's work.
    private static func systemTrusts(_ certificates: [NIOSSLCertificate], host: String, completion: @escaping (Bool) -> Void) {
        let chain = certificates.compactMap { certificate -> SecCertificate? in
            guard let der = try? certificate.toDERBytes() else { return nil }
            return SecCertificateCreateWithData(nil, Data(der) as CFData)
        }
        var trust: SecTrust?
        guard !chain.isEmpty,
              SecTrustCreateWithCertificates(chain as CFArray, SecPolicyCreateSSL(true, host as CFString), &trust) == errSecSuccess,
              let trust else {
            completion(false)
            return
        }
        // The evaluation can fetch intermediate certificates, so it runs off the event loop.
        DispatchQueue.global(qos: .userInitiated).async {
            completion(SecTrustEvaluateWithError(trust, nil))
        }
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
