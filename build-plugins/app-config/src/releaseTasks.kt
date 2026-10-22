package io.github.yuroyami.synkplay.buildplugins.appconfig

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** A Play note shorter than this says nothing about the release. */
private const val MIN_PLAY_NOTE = 40

/** Generic lines that stand in for release notes. */
private val PLACEHOLDER_NOTE = Regex(
    """(?i)(maintenance update|bug fixes( and (performance )?improvements)?|minor (fixes|improvements)|various fixes|improvements|todo|tbd|placeholder)\.?""",
)

/**
 * The stores check the length of the store texts at upload, not at build time. This check fails
 * earlier: on a text over the Play limit, on a missing or placeholder Play note for this version,
 * and on a CHANGELOG.md without a section for this version.
 */
@TaskAction
fun checkStoreMetadata(
    version: String,
    rebuild: Int,
    @Input(inferTaskDependency = false) storeMetadata: Path,
    @Input(inferTaskDependency = false) changelog: Path,
) {
    val versionCode = buildNumber(version, rebuild)
    val problems = mutableListOf<String>()
    fun limit(path: String, max: Int, what: String) {
        val file = storeMetadata.resolve(path)
        if (!file.isRegularFile()) {
            problems += "$path is missing ($what)"
            return
        }
        val length = file.readText().trim().length
        if (length > max) problems += "$path is $length characters; Play allows $max ($what)"
    }
    limit("short_description.txt", 80, "the one-line pitch")
    limit("full_description.txt", 4000, "the store listing body")

    val note = storeMetadata.resolve("changelogs/$versionCode.txt")
    if (!note.isRegularFile()) {
        problems += "changelogs/$versionCode.txt is missing; this version has no release notes"
    } else {
        val text = note.readText().trim()
        if (text.length > 500) problems += "changelogs/$versionCode.txt is ${text.length} characters; Play allows 500"
        if (text.length < MIN_PLAY_NOTE || PLACEHOLDER_NOTE.matches(text)) {
            problems += "changelogs/$versionCode.txt is a placeholder (\"$text\"); summarize this version's CHANGELOG.md section"
        }
    }
    if (!changelog.isRegularFile() || changelog.readLines().none { it.trim() == "## $version" }) {
        problems += "CHANGELOG.md has no \"## $version\" section"
    }
    check(problems.isEmpty()) { "Store metadata will be rejected on upload:\n" + problems.joinToString("\n") { "  $it" } }
    println("Store metadata fits, and $versionCode has release notes.")
}

/** Rewrites the three gradient stops (offsets 0, 0.5 and 0.9) of the launcher foreground to the brand colors. */
@TaskAction(ExecutionAvoidance.Disabled)
fun syncTrinityColors(
    trinityColors: List<String>,
    // Not an output: the Android build reads res/, and it would then run this command first.
    @Input(inferTaskDependency = false) launcherForeground: Path,
) {
    check(trinityColors.size == 3) { "trinityColors needs three colors, found ${trinityColors.size}." }
    // The drawable takes #FFRRGGBB: the Trinity colors are opaque.
    val stops = trinityColors.map { "#FF" + argb(it).toString(16).padStart(8, '0').takeLast(6).uppercase() }
    val original = launcherForeground.readText()
    var updated = original
    listOf("0", "0.5", "0.9").zip(stops).forEach { (offset, color) ->
        updated = updated.replace(Regex("""(<item android:offset="$offset" android:color=")#[0-9A-Fa-f]+("/>)""")) {
            "${it.groupValues[1]}$color${it.groupValues[2]}"
        }
    }
    if (updated != original) {
        launcherForeground.writeText(updated)
        println("Updated $launcherForeground")
    } else {
        println("$launcherForeground already has the brand colors.")
    }
}

/** One row of the release page's dependency table. */
private class Row(val component: String, val where: String, val version: String?)

/**
 * Prints the dependency table of the GitHub release notes as Markdown. It lists only the main
 * dependencies: the build tools, the network stack and the video engines.
 */
@TaskAction(ExecutionAvoidance.Disabled)
fun printDependencyTable(
    @Input(inferTaskDependency = false) catalog: Path,
    @Input(inferTaskDependency = false) wrapper: Path,
    @Input(inferTaskDependency = false) swiftPackages: Path,
    // The module.yaml that sets vlcKitVersion for the vlckit plugin.
    @Input(inferTaskDependency = false) vlcKitModule: Path,
) {
    val vlcKitVersion = Regex("""(?m)^\s+vlcKitVersion:\s*"?([^"\s#]+)"?""").find(vlcKitModule.readText())?.groupValues?.get(1)
    val versions = Regex("""^([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
        .findAll(catalog.readText().substringAfter("[versions]").substringBefore("\n["))
        .associate { it.groupValues[1] to it.groupValues[2] }
    val toolchain = Regex("""kotlin_cli_version=([^\s"]+)""").find(wrapper.readText())?.groupValues?.get(1)
    val swift = if (swiftPackages.exists()) {
        Regex(""""identity"\s*:\s*"([^"]+)"[\s\S]*?"version"\s*:\s*"([^"]+)"""")
            .findAll(swiftPackages.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    } else {
        emptyMap()
    }
    val rows = listOf(
        Row("Kotlin", "Toolchain", versions["kotlin"]),
        Row("Compose Multiplatform", "Toolchain", versions["compose-multiplatform"]),
        Row("Kotlin Toolchain", "Toolchain", toolchain),
        Row("Ktor", "Network, all platforms", versions["ktor"]),
        Row("Netty", "Network, Android and desktop", versions["netty"]),
        Row("SwiftNIO", "Network, iOS", swift["swift-nio"]),
        Row("ExoPlayer (Media3)", "Video engine, Android", versions["media3"]),
        Row("libmpvKt (mpv, FFmpeg, libass and libplacebo inside)", "Video engine, Android, full build only", versions["libmpvkt"]),
        Row("VLCKit", "Video engine, iOS", vlcKitVersion),
        Row("KitePlayer, with KiteFFmpeg inside", "Video engine, all platforms, experimental", versions["kiteplayer"]),
    )
    println("| Component | Where | Version |")
    println("|---|---|---|")
    rows.filter { it.version != null }.forEach { println("| ${it.component} | ${it.where} | ${it.version} |") }
}
