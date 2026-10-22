package io.github.yuroyami.synkplay.buildplugins.vlckit

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface VlcKitSettings {
    /** The VLCKit version, such as `4.0.0a25`. It names the cached archive. */
    val vlcKitVersion: String

    /** The download URL of the VLCKit archive (the `source` of the VLCKit podspec). */
    val url: String

    /** The SHA-256 of the archive. The task fails when the download has another checksum. */
    val sha256: String

    /** The Objective-C bridge header that the Kotlin code calls, relative to the module directory. */
    val header: Path
}
