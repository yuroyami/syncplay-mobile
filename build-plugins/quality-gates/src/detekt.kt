package io.github.yuroyami.synkplay.buildplugins.qualitygates

import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Runs detekt on every Kotlin source folder of [modules] (`src`, `src@*`, `test` and `test@*`),
 * with [config] on top of detekt's default configuration. It writes HTML and SARIF reports into
 * [reportDir] and fails on any finding: detekt 2 fails on every issue of severity error, the default.
 */
@OptIn(ExperimentalPathApi::class)
@TaskAction(ExecutionAvoidance.Disabled)
fun detekt(
    @Input(inferTaskDependency = false) modules: List<Path>,
    @Input(inferTaskDependency = false) config: Path,
    @Input detektClasspath: Classpath,
    @Output reportDir: Path,
) {
    val sources = modules.filter { it.isDirectory() }.flatMap { module ->
        module.listDirectoryEntries().filter { dir ->
            dir.isDirectory() && listOf("src", "test").any { dir.name == it || dir.name.startsWith("$it@") }
        }
    }.sortedBy { it.absolutePathString() }
    check(sources.isNotEmpty()) { "detekt found no source folder in $modules." }
    reportDir.createDirectories()

    val java = Path(System.getProperty("java.home")).resolve(if (File.separatorChar == '\\') "bin/java.exe" else "bin/java")
    val command = listOf(
        java.absolutePathString(),
        "-cp", detektClasspath.resolvedFiles.joinToString(File.pathSeparator) { it.absolutePathString() },
        "dev.detekt.cli.Main",
        // detekt 2 splits --input on the path separator, not on commas.
        "--input", sources.joinToString(File.pathSeparator) { it.absolutePathString() },
        "--config", config.absolutePathString(),
        "--build-upon-default-config",
        "--jvm-target", "21",
        "--report", "html:${reportDir.resolve("detekt.html").absolutePathString()}",
        "--report", "sarif:${reportDir.resolve("detekt.sarif").absolutePathString()}",
    )
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    process.inputStream.bufferedReader().forEachLine(::println)
    val exit = process.waitFor()
    check(exit == 0) { "detekt failed with exit code $exit. The report is in $reportDir." }
    println("detekt passed on ${sources.size} source folders.")
}
