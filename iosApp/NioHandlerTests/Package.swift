// swift-tools-version:5.9
// Tests the connection handlers of SwiftNioNetworkManager on a Mac, with no simulator:
//   swift test --package-path iosApp/NioHandlerTests
// The source is a link to iosApp/iosApp/NioConnectionHandlers.swift, so the app and the tests
// build the same file. SwiftNIO is pinned to the version in the Xcode project's Package.resolved.
import PackageDescription

let package = Package(
    name: "NioHandlerTests",
    platforms: [.macOS(.v13)],
    dependencies: [
        .package(url: "https://github.com/apple/swift-nio.git", exact: "2.95.0"),
    ],
    targets: [
        .target(
            name: "NioConnectionHandlers",
            dependencies: [
                .product(name: "NIO", package: "swift-nio"),
                .product(name: "NIOTLS", package: "swift-nio"),
            ]
        ),
        .testTarget(
            name: "NioConnectionHandlersTests",
            dependencies: [
                "NioConnectionHandlers",
                .product(name: "NIOEmbedded", package: "swift-nio"),
            ]
        ),
    ],
    swiftLanguageVersions: [.v5]
)
