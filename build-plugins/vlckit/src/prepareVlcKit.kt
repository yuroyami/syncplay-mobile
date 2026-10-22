package io.github.yuroyami.synkplay.buildplugins.vlckit

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.writeText

/** The iOS slices of the VLCKit XCFramework, by the Kotlin/Native target that links each one. */
internal val SLICES = mapOf(
    "ios_arm64" to "ios-arm64",
    "ios_simulator_arm64" to "ios-arm64_x86_64-simulator",
)

/**
 * Puts VLCKit into [xcframework] and writes the cinterop definition [defFile].
 *
 * The archive is cached in `~/Library/Caches/Synkplay/VLCKit` (or `SYNKPLAY_VLCKIT_CACHE`), so a
 * new build folder does not download 900 MB again. Only the iOS slices and their debug symbols go
 * into the XCFramework. The Xcode project links and embeds it from `build/vlckit`.
 *
 * The definition uses the package `cocoapods.VLCKit`, because the Kotlin code imports VLCKit from
 * it. It binds [header], which imports the VLCKit headers.
 */
@OptIn(ExperimentalPathApi::class)
@TaskAction
fun prepareVlcKit(
    vlcKitVersion: String,
    url: String,
    sha256: String,
    @Input header: Path,
    @Output xcframework: Path,
    @Output defFile: Path,
    @Output includeDir: Path,
) {
    check(System.getProperty("os.name").lowercase().contains("mac")) { "VLCKit for iOS can only be prepared on macOS." }
    val archive = cachedArchive(vlcKitVersion, url, sha256.lowercase())

    // Unpack the iOS slices, then let xcodebuild write an XCFramework that lists only them.
    val unpacked = xcframework.resolveSibling("${xcframework.name}.unpacked")
    unpacked.deleteRecursively()
    xcframework.deleteRecursively()
    unpacked.createDirectories()
    run(
        listOf("/usr/bin/unzip", "-q", "-o", archive.absolutePathString()) +
            SLICES.values.map { "VLCKit.xcframework/$it/*" } +
            listOf("-d", unpacked.absolutePathString()),
    )
    val create = mutableListOf("/usr/bin/xcrun", "xcodebuild", "-create-xcframework")
    for (slice in SLICES.values) {
        val sliceDir = unpacked.resolve("VLCKit.xcframework/$slice")
        create += listOf("-framework", sliceDir.resolve("VLCKit.framework").absolutePathString())
        val dsym = sliceDir.resolve("dSYMs/VLCKit.framework.dSYM")
        if (dsym.isDirectory()) create += listOf("-debug-symbols", dsym.absolutePathString())
    }
    create += listOf("-output", xcframework.absolutePathString())
    run(create)
    unpacked.deleteRecursively()

    // cinterop splits compiler options at spaces, so a path with a space would break them.
    val root = xcframework.absolutePathString()
    check(' ' !in root) { "The project path must not contain a space for the VLCKit binding: $root" }
    includeDir.deleteRecursively()
    includeDir.createDirectories()
    header.copyTo(includeDir.resolve(header.name), overwrite = true)
    defFile.writeText(
        buildString {
            appendLine("# Written by build-plugins/vlckit. Do not edit.")
            appendLine("language = Objective-C")
            appendLine("package = cocoapods.VLCKit")
            appendLine("headers = ${header.name}")
            for ((target, slice) in SLICES) {
                val frameworks = "$root/$slice"
                // VLCKit's headers include <vlc/...> from the framework's Headers folder.
                appendLine("compilerOpts.$target = -F$frameworks -I$frameworks/VLCKit.framework/Headers")
                appendLine("linkerOpts.$target = -F$frameworks -framework VLCKit")
            }
        },
    )
    println("VLCKit $vlcKitVersion: $xcframework")
}

/** The cached VLCKit archive with the expected checksum. Downloads it when it is missing or wrong. */
private fun cachedArchive(version: String, url: String, sha256: String): Path {
    val home = System.getProperty("user.home")
    val cache = System.getenv("SYNKPLAY_VLCKIT_CACHE")?.let { Path(it) } ?: Path(home, "Library/Caches/Synkplay/VLCKit")
    cache.createDirectories()
    val archive = cache.resolve("VLCKit-$version.zip")
    if (archive.isRegularFile() && sha256Of(archive) == sha256) return archive

    val partial = cache.resolve("VLCKit-$version.zip.part")
    println("Downloading VLCKit $version from $url")
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    val response = client.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofFile(partial))
    check(response.statusCode() == 200) { "Download of $url failed with HTTP ${response.statusCode()}." }
    val actual = sha256Of(partial)
    if (actual != sha256) {
        partial.deleteIfExists()
        error("The VLCKit archive from $url has SHA-256 $actual, expected $sha256.")
    }
    Files.move(partial, archive, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    return archive
}

private fun sha256Of(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 20)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun run(command: List<String>) {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    val exit = process.waitFor()
    check(exit == 0) { "Exit code $exit: ${command.take(3).joinToString(" ")}\n$output" }
}
