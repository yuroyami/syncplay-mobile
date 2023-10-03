import Foundation
import Darwin

public typealias Observer = ((_ response: PingResponse) -> Void)
public typealias FinishedCallback = ((_ result: PingResult) -> Void)


/// Represents a single ping instance. A ping instance has a single destination.
public class SwiftyPing: NSObject {
    /// Describes the ping host destination.
    public struct Destination {
        /// The host name, can be a IP address or a URL.
        public let host: String
        /// IPv4 address of the host.
        public let ipv4Address: Data
        /// Socket address of `ipv4Address`.
        public var socketAddress: sockaddr_in? { return ipv4Address.socketAddressInternet }
        /// IP address of the host.
        public var ip: String? {
            guard let address = socketAddress else { return nil }
            return String(cString: inet_ntoa(address.sin_addr), encoding: .ascii)
        }

        /// Resolves the `host`.
        public static func getIPv4AddressFromHost(host: String) throws -> Data {
            var streamError = CFStreamError()
            let cfhost = CFHostCreateWithName(nil, host as CFString).takeRetainedValue()
            let status = CFHostStartInfoResolution(cfhost, .addresses, &streamError)

            var data: Data?
            if !status {
                if Int32(streamError.domain) == kCFStreamErrorDomainNetDB {
                    throw PingError.addressLookupError
                } else {
                    throw PingError.unknownHostError
                }
            } else {
                var success: DarwinBoolean = false
                guard let addresses = CFHostGetAddressing(cfhost, &success)?.takeUnretainedValue() as? [Data] else {
                    throw PingError.hostNotFound
                }

                for address in addresses {
                    let addrin = address.socketAddress
                    if address.count >= MemoryLayout<sockaddr>.size && addrin.sa_family == UInt8(AF_INET) {
                        data = address
                        break
                    }
                }

                if data?.count == 0 || data == nil {
                    throw PingError.hostNotFound
                }
            }
            guard let returnData = data else { throw PingError.unknownHostError }
            return returnData
        }

    }
    // MARK: - Initialization
    /// Ping host
    public let destination: Destination
    /// Ping configuration
    public let configuration: PingConfiguration
    /// This closure gets called with ping responses.
    public var observer: Observer?
    /// This closure gets called when pinging stops, either when `targetCount` is reached or pinging is stopped explicitly with `stop()` or `halt()`.
    public var finished: FinishedCallback?
    /// This delegate gets called with ping responses.
    public var delegate: PingDelegate?
    /// The number of pings to make. Default is `nil`, which means no limit.
    public var targetCount: Int?

    /// The current ping count, starting from 0.
    public var currentCount: UInt64 {
        return trueSequenceIndex
    }
    /// Array of all ping responses sent to the `observer`.
    public private(set) var responses: [PingResponse] = []
    /// A random identifier which is a part of the ping request.
    private let identifier = UInt16.random(in: 0..<UInt16.max)
    /// A random UUID fingerprint sent as the payload.
    private let fingerprint = UUID()
    /// User-specified dispatch queue. The `observer` is always called from this queue.
    private let currentQueue: DispatchQueue

    /// Socket for sending and receiving data.
    private var socket: CFSocket?
    /// Socket source
    private var socketSource: CFRunLoopSource?
    /// An unmanaged instance of `SocketInfo` used in the current socket's callback. This must be released manually, otherwise it will leak.
    private var unmanagedSocketInfo: Unmanaged<SocketInfo>?

    /// When the current request was sent.
    private var sequenceStart = Date()
    /// The current sequence number.
    private var _sequenceIndex: UInt16 = 0
    private var sequenceIndex: UInt16 {
        get {
            _serial_property.sync { self._sequenceIndex }
        }
        set {
            _serial_property.sync { self._sequenceIndex = newValue }
        }
    }
    /// The true sequence number.
    private var _trueSequenceIndex: UInt64 = 0
    private var trueSequenceIndex: UInt64 {
        get {
            _serial_property.sync { self._trueSequenceIndex }
        }
        set {
            _serial_property.sync { self._trueSequenceIndex = newValue }
        }
    }

    private var erroredIndices = [Int]()
    /// Initializes a pinger.
    /// - Parameter destination: Specifies the host.
    /// - Parameter configuration: A configuration object which can be used to customize pinging behavior.
    /// - Parameter queue: All responses are delivered through this dispatch queue.
    public init(destination: Destination, configuration: PingConfiguration, queue: DispatchQueue) throws {
        self.destination = destination
        self.configuration = configuration
        self.currentQueue = queue

        super.init()
        try createSocket()
    }

    // MARK: - Convenience Initializers
    /// Initializes a pinger from an IPv4 address string.
    /// - Parameter ipv4Address: The host's IP address.
    /// - Parameter configuration: A configuration object which can be used to customize pinging behavior.
    /// - Parameter queue: All responses are delivered through this dispatch queue.
    public convenience init(ipv4Address: String, config configuration: PingConfiguration, queue: DispatchQueue) throws {
        var socketAddress = sockaddr_in()

        socketAddress.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        socketAddress.sin_family = UInt8(AF_INET)
        socketAddress.sin_port = 0
        socketAddress.sin_addr.s_addr = inet_addr(ipv4Address.cString(using: .utf8))
        let data = Data(bytes: &socketAddress, count: MemoryLayout<sockaddr_in>.size)

        let destination = Destination(host: ipv4Address, ipv4Address: data)
        try self.init(destination: destination, configuration: configuration, queue: queue)
    }

    /// Initializes a CFSocket.
    /// - Throws: If setting a socket options flag fails, throws a `PingError.socketOptionsSetError(:)`.
    private func createSocket() throws {
        try _serial.sync {
            // Create a socket context...
            let info = SocketInfo(pinger: self, identifier: identifier)
            unmanagedSocketInfo = Unmanaged.passRetained(info)
            var context = CFSocketContext(version: 0, info: unmanagedSocketInfo!.toOpaque(), retain: nil, release: nil, copyDescription: nil)

            // ...and a socket...
            socket = CFSocketCreate(kCFAllocatorDefault, AF_INET, SOCK_DGRAM, IPPROTO_ICMP, CFSocketCallBackType.dataCallBack.rawValue, { socket, type, address, data, info in
                // Socket callback closure
                guard let socket = socket, let info = info, let data = data else { return }
                let socketInfo = Unmanaged<SocketInfo>.fromOpaque(info).takeUnretainedValue()
                let ping = socketInfo.pinger
                if (type as CFSocketCallBackType) == CFSocketCallBackType.dataCallBack {
                    let cfdata = Unmanaged<CFData>.fromOpaque(data).takeUnretainedValue()
                    ping?.socket(socket: socket, didReadData: cfdata as Data)
                }
            }, &context)

            // Disable SIGPIPE, see issue #15 on GitHub.
            let handle = CFSocketGetNative(socket)
            var value: Int32 = 1
            let err = setsockopt(handle, SOL_SOCKET, SO_NOSIGPIPE, &value, socklen_t(MemoryLayout.size(ofValue: value)))
            guard err == 0 else {
                throw PingError.socketOptionsSetError(err: err)
            }

            // Set TTL
            if var ttl = configuration.timeToLive {
                let err = setsockopt(handle, IPPROTO_IP, IP_TTL, &ttl, socklen_t(MemoryLayout.size(ofValue: ttl)))
                guard err == 0 else {
                    throw PingError.socketOptionsSetError(err: err)
                }
            }

            // ...and add it to the main run loop.
            socketSource = CFSocketCreateRunLoopSource(nil, socket, 0)
            CFRunLoopAddSource(CFRunLoopGetMain(), socketSource, .commonModes)
        }
    }

    // MARK: - Tear-down
    private func tearDown() {
        if socketSource != nil {
            CFRunLoopSourceInvalidate(socketSource)
            socketSource = nil
        }
        if socket != nil {
            CFSocketInvalidate(socket)
            socket = nil
        }
        unmanagedSocketInfo?.release()
        unmanagedSocketInfo = nil
        timeoutTimer?.invalidate()
        timeoutTimer = nil
    }
    deinit {
        tearDown()
    }

    // MARK: - Single ping

    private var _isPinging = false
    private var isPinging: Bool {
        get {
            return _serial_property.sync { self._isPinging }
        }
        set {
            _serial_property.sync { self._isPinging = newValue }
        }
    }

    private var _timeoutTimer: Timer?
    private var timeoutTimer: Timer? {
        get {
            return _serial_property.sync { self._timeoutTimer }
        }
        set {
            _serial_property.sync { self._timeoutTimer = newValue }
        }
    }

    private func sendPing() {
        if isPinging || killswitch {
            return
        }
        isPinging = true
        sequenceStart = Date()

        let timer = Timer(timeInterval: self.configuration.timeoutInterval, target: self, selector: #selector(self.timeout), userInfo: nil, repeats: false)
        RunLoop.main.add(timer, forMode: .common)
        self.timeoutTimer = timer

        _serial.async {
            let address = self.destination.ipv4Address
            do {
                let icmpPackage = try self.createICMPPackage(identifier: UInt16(self.identifier), sequenceNumber: UInt16(self.sequenceIndex))

                guard let socket = self.socket else { return }
                let socketError = CFSocketSendData(socket, address as CFData, icmpPackage as CFData, self.configuration.timeoutInterval)

                if socketError != .success {
                    var error: PingError?

                    switch socketError {
                    case .error: error = .requestError
                    case .timeout: error = .requestTimeout
                    default: break
                    }
                    let response = PingResponse(identifier: self.identifier,
                                                ipAddress: self.destination.ip,
                                                sequenceNumber: self.sequenceIndex,
                                                trueSequenceNumber: self.trueSequenceIndex,
                                                duration: self.timeIntervalSinceStart,
                                                error: error,
                                                byteCount: nil,
                                                ipHeader: nil)

                    self.erroredIndices.append(Int(self.sequenceIndex))
                    self.isPinging = false
                    self.informObserver(of: response)

                    return self.scheduleNextPing()
                }
            } catch {
                let pingError: PingError
                if let err = error as? PingError {
                    pingError = err
                } else {
                    pingError = .packageCreationFailed
                }
                let response = PingResponse(identifier: self.identifier,
                                            ipAddress: self.destination.ip,
                                            sequenceNumber: self.sequenceIndex,
                                            trueSequenceNumber: self.trueSequenceIndex,
                                            duration: self.timeIntervalSinceStart,
                                            error: pingError,
                                            byteCount: nil,
                                            ipHeader: nil)
                self.erroredIndices.append(Int(self.sequenceIndex))
                self.isPinging = false
                self.informObserver(of: response)

                return self.scheduleNextPing()
            }
        }
    }

    private var timeIntervalSinceStart: TimeInterval {
        return Date().timeIntervalSince(sequenceStart)
    }

    @objc private func timeout() {
        let error = PingError.responseTimeout
        let response = PingResponse(identifier: self.identifier,
                                    ipAddress: self.destination.ip,
                                    sequenceNumber: self.sequenceIndex,
                                    trueSequenceNumber: self.trueSequenceIndex,
                                    duration: timeIntervalSinceStart,
                                    error: error,
                                    byteCount: nil,
                                    ipHeader: nil)

        erroredIndices.append(Int(sequenceIndex))
        self.isPinging = false
        informObserver(of: response)

        incrementSequenceIndex()
        scheduleNextPing()
    }

    // MARK: - Socket callback
    private func socket(socket: CFSocket, didReadData data: Data?) {
        if killswitch { return }

        guard let data = data else { return }
        var validationError: PingError? = nil

        do {
            let validation = try validateResponse(from: data)
            if !validation { return }
        } catch let error as PingError {
            validationError = error
        } catch {
            print("Unhandled error thrown: \(error)")
        }

        timeoutTimer?.invalidate()
        var ipHeader: IPHeader? = nil
        if validationError == nil {
            ipHeader = data.withUnsafeBytes({ $0.load(as: IPHeader.self) })
        }
        let response = PingResponse(identifier: identifier,
                                    ipAddress: destination.ip,
                                    sequenceNumber: sequenceIndex,
                                    trueSequenceNumber: trueSequenceIndex,
                                    duration: timeIntervalSinceStart,
                                    error: validationError,
                                    byteCount: data.count,
                                    ipHeader: ipHeader)
        isPinging = false
        informObserver(of: response)

        incrementSequenceIndex()
        scheduleNextPing()
    }

    // MARK: - ICMP package

    /// Creates an ICMP package.
    private func createICMPPackage(identifier: UInt16, sequenceNumber: UInt16) throws -> Data {
        var header = ICMPHeader(type: ICMPType.EchoRequest.rawValue,
                                code: 0,
                                checksum: 0,
                                identifier: CFSwapInt16HostToBig(identifier),
                                sequenceNumber: CFSwapInt16HostToBig(sequenceNumber),
                                payload: fingerprint.uuid)

        let delta = configuration.payloadSize - MemoryLayout<uuid_t>.size
        var additional = [UInt8]()
        if delta > 0 {
            additional = (0..<delta).map { _ in UInt8.random(in: UInt8.min...UInt8.max) }
        }

        let checksum = try computeChecksum(header: header, additionalPayload: additional)
        header.checksum = checksum

        let package = Data(bytes: &header, count: MemoryLayout<ICMPHeader>.size) + Data(additional)
        return package
    }

    private func computeChecksum(header: ICMPHeader, additionalPayload: [UInt8]) throws -> UInt16 {
        let typecode = Data([header.type, header.code]).withUnsafeBytes { $0.load(as: UInt16.self) }
        var sum = UInt64(typecode) + UInt64(header.identifier) + UInt64(header.sequenceNumber)
        let payload = convert(payload: header.payload) + additionalPayload

        guard payload.count % 2 == 0 else { throw PingError.unexpectedPayloadLength }

        var i = 0
        while i < payload.count {
            guard payload.indices.contains(i + 1) else { throw PingError.unexpectedPayloadLength }
            // Convert two 8 byte ints to one 16 byte int
            sum += Data([payload[i], payload[i + 1]]).withUnsafeBytes { UInt64($0.load(as: UInt16.self)) }
            i += 2
        }
        while sum >> 16 != 0 {
            sum = (sum & 0xffff) + (sum >> 16)
        }

        guard sum < UInt16.max else { throw PingError.checksumOutOfBounds }

        return ~UInt16(sum)
    }

    private func icmpHeaderOffset(of packet: Data) -> Int? {
        if packet.count >= MemoryLayout<IPHeader>.size + MemoryLayout<ICMPHeader>.size {
            let ipHeader = packet.withUnsafeBytes({ $0.load(as: IPHeader.self) })
            if ipHeader.versionAndHeaderLength & 0xF0 == 0x40 && ipHeader.protocol == IPPROTO_ICMP {
                let headerLength = Int(ipHeader.versionAndHeaderLength) & 0x0F * MemoryLayout<UInt32>.size
                if packet.count >= headerLength + MemoryLayout<ICMPHeader>.size {
                    return headerLength
                }
            }
        }
        return nil
    }

    private func convert(payload: uuid_t) -> [UInt8] {
        let p = payload
        return [p.0, p.1, p.2, p.3, p.4, p.5, p.6, p.7, p.8, p.9, p.10, p.11, p.12, p.13, p.14, p.15].map { UInt8($0) }
    }

    private func validateResponse(from data: Data) throws -> Bool {
        guard data.count >= MemoryLayout<ICMPHeader>.size + MemoryLayout<IPHeader>.size else {
            throw PingError.invalidLength(received: data.count)
        }

        guard let headerOffset = icmpHeaderOffset(of: data) else { throw PingError.invalidHeaderOffset }
        let payloadSize = data.count - headerOffset - MemoryLayout<ICMPHeader>.size
        let icmpHeader = data.withUnsafeBytes({ $0.load(fromByteOffset: headerOffset, as: ICMPHeader.self) })
        let payload = data.subdata(in: (data.count - payloadSize)..<data.count)

        let uuid = UUID(uuid: icmpHeader.payload)
        guard uuid == fingerprint else {
            // Wrong handler, ignore this response
            return false
        }

        let checksum = try computeChecksum(header: icmpHeader, additionalPayload: [UInt8](payload))

        guard icmpHeader.checksum == checksum else {
            throw PingError.checksumMismatch(received: icmpHeader.checksum, calculated: checksum)
        }
        guard icmpHeader.type == ICMPType.EchoReply.rawValue else {
            throw PingError.invalidType(received: icmpHeader.type)
        }
        guard icmpHeader.code == 0 else {
            throw PingError.invalidCode(received: icmpHeader.code)
        }
        guard CFSwapInt16BigToHost(icmpHeader.identifier) == identifier else {
            throw PingError.identifierMismatch(received: icmpHeader.identifier, expected: identifier)
        }
        let receivedSequenceIndex = CFSwapInt16BigToHost(icmpHeader.sequenceNumber)
        guard receivedSequenceIndex == sequenceIndex else {
            if erroredIndices.contains(Int(receivedSequenceIndex)) {
                // This response either errorred or timed out, ignore it
                return false
            }
            throw PingError.invalidSequenceIndex(received: receivedSequenceIndex, expected: sequenceIndex)
        }
        return true
    }

}

// MARK: ICMP

/// Format of IPv4 header
public struct IPHeader {
    public var versionAndHeaderLength: UInt8
    public var differentiatedServices: UInt8
    public var totalLength: UInt16
    public var identification: UInt16
    public var flagsAndFragmentOffset: UInt16
    public var timeToLive: UInt8
    public var `protocol`: UInt8
    public var headerChecksum: UInt16
    public var sourceAddress: (UInt8, UInt8, UInt8, UInt8)
    public var destinationAddress: (UInt8, UInt8, UInt8, UInt8)
}

/// ICMP header structure
private struct ICMPHeader {
    /// Type of message
    var type: UInt8
    /// Type sub code
    var code: UInt8
    /// One's complement checksum of struct
    var checksum: UInt16
    /// Identifier
    var identifier: UInt16
    /// Sequence number
    var sequenceNumber: UInt16
    /// UUID payload
    var payload: uuid_t
}

/// ICMP echo types
public enum ICMPType: UInt8 {
    case EchoReply = 0
    case EchoRequest = 8
}

// MARK: - Helpers

/// A struct encapsulating a ping response.
public struct PingResponse {
    /// The randomly generated identifier used in the ping header.
    public let identifier: UInt16
    /// The IP address of the host.
    public let ipAddress: String?
    /// Running sequence number, starting from 0.
    /// This number will wrap to zero when it exceeds `UInt16.max`,
    /// which is usually just 65535, and is the one used in the ping
    /// protocol. See `trueSequenceNumber` for the actual count.
    public let sequenceNumber: UInt16
    /// The true sequence number.
    public let trueSequenceNumber: UInt64
    /// Roundtrip time.
    public let duration: TimeInterval
    /// An error associated with the response.
    public let error: PingError?
    /// Response data packet size in bytes.
    public let byteCount: Int?
    /// Response IP header.
    public let ipHeader: IPHeader?
}
/// A struct encapsulating the results of a ping instance.
public struct PingResult {
    /// A struct encapsulating the roundtrip statistics.
    public struct Roundtrip {
        /// The smallest roundtrip time.
        public let minimum: Double
        /// The largest roundtrip time.
        public let maximum: Double
        /// The average (mean) roundtrip time.
        public let average: Double
        /// The standard deviation of the roundtrip times.
        /// - Note: Standard deviation is calculated without Bessel's correction and thus gives zero if only one packet is received.
        public let standardDeviation: Double
    }
    /// Collection of all responses, including errored or timed out.
    public let responses: [PingResponse]
    /// Number of packets sent.
    public let packetsTransmitted: UInt64
    /// Number of packets received.
    public let packetsReceived: UInt64
    /// The packet loss. If the number of packets transmitted (`packetsTransmitted`) is zero, returns `nil`.
    public var packetLoss: Double? {
        if packetsTransmitted == 0 { return nil }
        return 1 - Double(packetsReceived) / Double(packetsTransmitted)
    }
    /// Roundtrip statistics, including min, max, average and stddev.
    public let roundtrip: Roundtrip?
}
/// Controls pinging behaviour.
public struct PingConfiguration {
    /// The time between consecutive pings in seconds.
    public let pingInterval: TimeInterval
    /// Timeout interval in seconds.
    public let timeoutInterval: TimeInterval
    /// If `true`, then `SwiftyPing` will automatically halt and restart the pinging when the app state changes. Only applicable on iOS. If `false`, then the user is responsible for appropriately handling app state changes, see issue #15 on GitHub.
    public var handleBackgroundTransitions = true
    /// Sets the TTL flag on the socket. All requests sent from the socket will include the TTL field set to this value.
    public var timeToLive: Int?
    /// Payload size in bytes. The payload always includes a fingerprint, and a payload size smaller than the fingerprint is ignored. By default, only the fingerprint is included in the payload.
    public var payloadSize: Int = MemoryLayout<uuid_t>.size
    /// If set to `true`, when `targetCount` is reached (if set), the pinging will be halted instead of stopped. This means that the socket will be released and will be recreated if more pings are requested. Defaults to `true`.
    public var haltAfterTarget: Bool = true

    /// Initializes a `PingConfiguration` object with the given parameters.
    /// - Parameter interval: The time between consecutive pings in seconds. Defaults to 1.
    /// - Parameter timeout: Timeout interval in seconds. Defaults to 5.
    public init(interval: TimeInterval = 1, with timeout: TimeInterval = 5) {
        pingInterval = interval
        timeoutInterval = timeout
    }
    /// Initializes a `PingConfiguration` object with the given interval.
    /// - Parameter interval: The time between consecutive pings in seconds.
    /// - Note: Timeout interval will be set to 5 seconds.
    public init(interval: TimeInterval) {
        self.init(interval: interval, with: 5)
    }
}

// MARK: - Data Extensions

public extension Data {
    /// Expresses a chunk of data as a socket address.
    var socketAddress: sockaddr {
        return withUnsafeBytes { $0.load(as: sockaddr.self) }
    }
    /// Expresses a chunk of data as an internet-style socket address.
    var socketAddressInternet: sockaddr_in {
        return withUnsafeBytes { $0.load(as: sockaddr_in.self) }
    }
}