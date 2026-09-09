import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Every version number the build pins, read once from where each one actually lives: the
 * version catalog, gradle.properties, the Gradle wrapper, the Swift package lock and the
 * CocoaPods lock. The documentation version tables and the release page's
 * dependency table both come from here, so the two cannot disagree.
 */
internal class ToolVersions(root: File) {
    val catalog: Map<String, String> = Regex("""^([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
        .findAll(File(root, "gradle/libs.versions.toml").readText().substringAfter("[versions]").substringBefore("["))
        .associate { it.groupValues[1] to it.groupValues[2] }

    val props: Map<String, String> = Regex("""^([A-Za-z0-9_.-]+)\s*=\s*(.+)$""", RegexOption.MULTILINE)
        .findAll(File(root, "gradle.properties").readText())
        .associate { it.groupValues[1].trim() to it.groupValues[2].trim() }

    /** Gradle's own version lives in the wrapper, not the catalog. */
    val gradle: String = Regex("""gradle-([0-9.]+)-bin\.zip""")
        .find(File(root, "gradle/wrapper/gradle-wrapper.properties").readText())?.groupValues?.get(1) ?: "?"

    /** Swift packages by identity, from the workspace's Package.resolved. */
    val swift: Map<String, String> = File(root, "iosApp/iosApp.xcworkspace/xcshareddata/swiftpm/Package.resolved")
        .takeIf { it.isFile }?.readText()?.let { text ->
            Regex(""""identity"\s*:\s*"([^"]+)"[\s\S]*?"version"\s*:\s*"([^"]+)"""")
                .findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        }.orEmpty()

    /** CocoaPods by name, from the first "- Name (version)" line of Podfile.lock. */
    val pods: Map<String, String> = File(root, "iosApp/Podfile.lock").takeIf { it.isFile }?.readText()?.let { text ->
        Regex("""^\s+- ([A-Za-z0-9_-]+) \(([^)]+)\)$""", RegexOption.MULTILINE)
            .findAll(text).map { it.groupValues[1] to it.groupValues[2].removePrefix("= ") }.distinctBy { it.first }.toMap()
    }.orEmpty()

}

/** One row of the release page's dependency table. */
private class Row(val component: String, val where: String, val version: String?)

/**
 * The dependency table the release notes carry, as Markdown. Only the main ones: the toolchain,
 * the network stack and the video engines. Someone deciding whether to trust or debug the app
 * wants those; nobody reads forty rows of AndroidX artifacts on a release page.
 */
internal fun releaseDependencyTable(v: ToolVersions): String {
    fun c(key: String) = v.catalog[key]
    val rows = listOf(
        Row("Kotlin", "Toolchain", c("kotlin")),
        Row("Compose Multiplatform", "Toolchain", c("compose-multiplatform")),
        Row("Android Gradle Plugin", "Toolchain", c("agp")),
        Row("Gradle", "Toolchain", v.gradle),
        Row("Ktor", "Network, all platforms", c("ktor")),
        Row("Netty", "Network, Android and desktop", c("netty")),
        Row("SwiftNIO", "Network, iOS", v.swift["swift-nio"]),
        Row("ExoPlayer (Media3)", "Video engine, Android", c("media3")),
        Row("libmpvKt (mpv, FFmpeg, libass and libplacebo inside)", "Video engine, Android, full build only", c("libmpvkt")),
        Row("VLCKit", "Video engine, iOS", v.pods["VLCKit"]),
        Row("KitePlayer, with KiteFFmpeg inside", "Video engine, all platforms, experimental", c("kiteplayer")),
    )
    return buildString {
        appendLine("| Component | Where | Version |")
        appendLine("|---|---|---|")
        rows.filter { it.version != null }.forEach { appendLine("| ${it.component} | ${it.where} | ${it.version} |") }
    }.trim()
}

/** `printDependencyTable`: the release workflow captures this into the GitHub release notes. */
fun Project.registerDependencyTableTask(): TaskProvider<*> = tasks.register("printDependencyTable") {
    group = "syncplay"
    description = "Prints the dependency table for the GitHub release notes as Markdown."
    outputs.upToDateWhen { false }
    doLast { println(releaseDependencyTable(ToolVersions(projectDir))) }
}
