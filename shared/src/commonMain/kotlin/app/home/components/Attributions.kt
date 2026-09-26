package app.home.components

import app.utils.Platform

/**
 * One piece of third-party work that the licences screen lists by hand, because the generated
 * dependency list cannot see it: native code inside a library, a Swift package, the original
 * Syncplay, or a service with its own terms. [licenceId] names the text in
 * `shared/config/aboutlibraries/licenses/`: an SPDX identifier, or a component's own text such as
 * `libass`. It is null for a service.
 */
data class Attribution(
    val name: String,
    val licence: String,
    val url: String,
    val licenceId: String? = null,
)

private const val LGPL_21 = "LGPL-2.1-or-later"
private const val GPL_3 = "GPL-3.0-or-later"

/** Always listed: the project this app ports, and the two services that have their own terms. */
private val credits = listOf(
    Attribution("Syncplay", "Apache 2.0", "https://syncplay.pl", "Apache-2.0"),
    Attribution("OpenSubtitles", "Service, own terms", "https://www.opensubtitles.com"),
    Attribution("Klipy", "Service, own terms", "https://klipy.com"),
)

/** The FFmpeg that KiteFFmpeg links into KitePlayer, and the subtitle stack of KitePlayer. */
private val kitePlayerNative = listOf(
    Attribution("FFmpeg, in KitePlayer", "LGPL 2.1 or later", "https://github.com/yuroyami/KiteFFmpeg", LGPL_21),
    Attribution("dav1d, in KitePlayer", "BSD 2-Clause", "https://code.videolan.org/videolan/dav1d", "dav1d"),
    Attribution("libass, in KitePlayer", "ISC", "https://github.com/libass/libass", "libass"),
    Attribution("HarfBuzz, in KitePlayer", "Old MIT", "https://github.com/harfbuzz/harfbuzz", "harfbuzz"),
    Attribution("FreeType, in KitePlayer", "FreeType License", "https://freetype.org", "FTL"),
    Attribution("FriBidi, in KitePlayer", "LGPL 2.1 or later", "https://github.com/fribidi/fribidi", LGPL_21),
)

/** The Media3 FFmpeg audio extension, in both Android builds (shared/libs/README.md). */
private val exoPlayerFfmpeg =
    Attribution("FFmpeg, in the ExoPlayer audio extension", "LGPL 2.1 or later", "https://ffmpeg.org", LGPL_21)

/** What the libmpvKt AAR carries, from its NOTICE. Together they make the full APK GPL 3.0 or later. */
private val mpvNative = listOf(
    Attribution("mpv", "GPL 2.0 or later", "https://mpv.io", "GPL-2.0-or-later"),
    Attribution("FFmpeg, in mpv", "GPL 3.0 or later, as libmpvKt builds it", "https://github.com/yuroyami/libmpvKt", GPL_3),
    Attribution("libplacebo, in mpv", "LGPL 2.1 or later", "https://code.videolan.org/videolan/libplacebo", LGPL_21),
    Attribution("libass, in mpv", "ISC", "https://github.com/libass/libass", "libass"),
    Attribution("dav1d, in mpv", "BSD 2-Clause", "https://code.videolan.org/videolan/dav1d", "dav1d"),
    Attribution("Mbed TLS, in mpv", "Apache 2.0", "https://github.com/Mbed-TLS/mbedtls", "Apache-2.0"),
    Attribution("HarfBuzz, in mpv", "Old MIT", "https://github.com/harfbuzz/harfbuzz", "harfbuzz"),
    Attribution("FreeType, in mpv", "FreeType License", "https://freetype.org", "FTL"),
    Attribution("FriBidi, in mpv", "LGPL 2.1 or later", "https://github.com/fribidi/fribidi", LGPL_21),
    Attribution("libunibreak, in mpv", "zlib", "https://github.com/adah1972/libunibreak", "libunibreak"),
    Attribution("Lua, in mpv", "MIT", "https://www.lua.org", "lua"),
)

/** The iOS packages that Gradle does not resolve: the VLCKit pod and the Swift packages. */
private val iosPackages = listOf(
    Attribution("VLCKit and libVLC", "LGPL 2.1 or later", "https://code.videolan.org/videolan/VLCKit", LGPL_21),
    Attribution("SwiftNIO and SwiftNIO SSL", "Apache 2.0", "https://github.com/apple/swift-nio", "Apache-2.0"),
    Attribution("BoringSSL, in SwiftNIO SSL", "OpenSSL and ISC", "https://boringssl.googlesource.com/boringssl", "boringssl"),
    Attribution("YouTubeKit", "MIT", "https://github.com/alexeichhorn/YouTubeKit", "youtubekit"),
)

/**
 * The hand-written entries of the running build. The ExoPlayer-only Android build carries no mpv
 * and no KitePlayer native code, so its only FFmpeg is the LGPL one of the audio extension.
 */
fun handWrittenAttributions(platform: Platform, exoOnly: Boolean): List<Attribution> = credits + when (platform) {
    Platform.Android -> if (exoOnly) listOf(exoPlayerFfmpeg) else mpvNative + kitePlayerNative + exoPlayerFfmpeg
    Platform.IOS -> iosPackages + kitePlayerNative
    Platform.Desktop -> kitePlayerNative
    Platform.Web -> emptyList()
}
