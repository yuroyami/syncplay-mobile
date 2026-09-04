package app.home.components

/**
 * The third-party work this app is built on, with the licence each one is offered under.
 *
 * Names and licences are read from what each project ships, not from memory. The list covers what
 * actually ends up inside a build; a library used only to build the app is not in here.
 */
data class Attribution(val name: String, val licence: String, val url: String)

/** Grouped loosely by what the piece does, which is the order they appear in. */
val attributions: List<Attribution> = listOf(
    Attribution("Syncplay", "Apache 2.0", "https://syncplay.pl"),
    Attribution("Kotlin and kotlinx", "Apache 2.0", "https://github.com/JetBrains/kotlin"),
    Attribution("Compose Multiplatform", "Apache 2.0", "https://github.com/JetBrains/compose-multiplatform"),
    Attribution("Skiko", "Apache 2.0", "https://github.com/JetBrains/skiko"),
    Attribution("AndroidX", "Apache 2.0", "https://developer.android.com/jetpack/androidx"),
    Attribution("Ktor", "Apache 2.0", "https://ktor.io"),
    Attribution("Ktorfit", "Apache 2.0", "https://github.com/Foso/Ktorfit"),
    Attribution("Netty", "Apache 2.0", "https://netty.io"),
    Attribution("Conscrypt", "Apache 2.0", "https://github.com/google/conscrypt"),
    Attribution("SwiftNIO", "Apache 2.0", "https://github.com/apple/swift-nio"),
    Attribution("Media3 ExoPlayer", "Apache 2.0", "https://github.com/androidx/media"),
    Attribution("mpv", "GPL 2.0 or later", "https://mpv.io"),
    Attribution("VLCKit and libVLC", "LGPL 2.1 or later", "https://code.videolan.org/videolan/VLCKit"),
    Attribution("FFmpeg", "GPL 3.0 as built here", "https://ffmpeg.org"),
    Attribution("KitePlayer and KiteCodec", "Apache 2.0", "https://github.com/yuroyami"),
    Attribution("NewPipe Extractor", "GPL 3.0", "https://github.com/TeamNewPipe/NewPipeExtractor"),
    Attribution("YouTubeKit", "MIT", "https://github.com/alexeichhorn/YouTubeKit"),
    Attribution("Coil", "Apache 2.0", "https://coil-kt.github.io/coil"),
    Attribution("Haze", "Apache 2.0", "https://github.com/chrisbanes/haze"),
    Attribution("MaterialKolor", "MIT", "https://github.com/jordond/MaterialKolor"),
    Attribution("FileKit", "MIT", "https://github.com/vinceglb/FileKit"),
    Attribution("Kermit", "Apache 2.0", "https://github.com/touchlab/Kermit"),
    Attribution("KotlinCrypto", "Apache 2.0", "https://github.com/KotlinCrypto"),
    Attribution("OpenSubtitles", "Service, own terms", "https://www.opensubtitles.com"),
    Attribution("Klipy", "Service, own terms", "https://klipy.com"),
)
