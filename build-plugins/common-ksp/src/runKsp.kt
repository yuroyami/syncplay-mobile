package io.github.yuroyami.synkplay.buildplugins.commonksp

import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

/**
 * Runs KSP2 once on the common sources and writes the generated Kotlin files to [outputDir].
 *
 * The toolchain runs KSP per platform only, and its output is not visible in the common code. So
 * this task runs the processors once on the common sources, and every platform compiles the same
 * generated files. It runs in a separate JVM, with the KSP command line entry point (`KSPJvmMain`). The processors generate plain Kotlin, so the JVM analysis is
 * enough for the common code.
 */
@OptIn(ExperimentalPathApi::class)
@TaskAction
fun generateCommonCode(
    moduleName: String,
    @Input sourceRoots: List<Path>,
    @Input resources: Path,
    processorOptions: Map<String, String>,
    languageVersion: String,
    @Input kspClasspath: Classpath,
    @Input processorClasspath: Classpath,
    @Input libraries: Classpath,
    // The catalog is a baseline for the version check, not something a task produces.
    @Input(inferTaskDependency = false) catalog: Path,
    versionPins: Map<String, String>,
    @Output outputDir: Path,
    @Output workDir: Path,
) {
    checkProcessorVersions(processorClasspath.resolvedFiles, catalog, versionPins)

    if (outputDir.exists()) outputDir.deleteRecursively()
    if (workDir.exists()) workDir.deleteRecursively()
    outputDir.createDirectories()
    val dirs = listOf("caches", "classes", "resources", "java", "base").associateWith { workDir.resolve(it).createDirectories() }

    val options = processorOptions + ("lyricist.xml.resourcesPath" to resources.absolutePathString())
    val javaHome = Path(System.getProperty("java.home"))
    val java = javaHome.resolve(if (File.separatorChar == '\\') "bin/java.exe" else "bin/java")
    val paths = { files: List<Path> -> files.joinToString(File.pathSeparator) { it.absolutePathString() } }

    val command = buildList {
        add(java.absolutePathString())
        // KSP's analysis code needs these on JDK 24 and newer.
        add("--enable-native-access=ALL-UNNAMED")
        add("--sun-misc-unsafe-memory-access=allow")
        add("-Xmx2g")
        add("-cp"); add(paths(kspClasspath.resolvedFiles))
        add("com.google.devtools.ksp.cmdline.KSPJvmMain")
        add("-module-name=$moduleName")
        add("-source-roots=" + paths(sourceRoots.filter { it.isDirectory() }))
        add("-libraries=" + paths(libraries.resolvedFiles))
        add("-processor-options=" + options.entries.joinToString(File.pathSeparator) { "${it.key}=${it.value}" })
        add("-language-version=$languageVersion")
        add("-api-version=$languageVersion")
        add("-jvm-target=21")
        add("-jdk-home=${javaHome.absolutePathString()}")
        add("-incremental=false")
        add("-project-base-dir=${dirs.getValue("base").absolutePathString()}")
        add("-output-base-dir=${dirs.getValue("base").absolutePathString()}")
        add("-caches-dir=${dirs.getValue("caches").absolutePathString()}")
        add("-class-output-dir=${dirs.getValue("classes").absolutePathString()}")
        add("-kotlin-output-dir=${outputDir.absolutePathString()}")
        add("-resource-output-dir=${dirs.getValue("resources").absolutePathString()}")
        add("-java-output-dir=${dirs.getValue("java").absolutePathString()}")
        add(paths(processorClasspath.resolvedFiles))
    }
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val log = process.inputStream.bufferedReader().readText()
    val exit = process.waitFor()
    if (log.isNotBlank()) println(log.trimEnd())
    check(exit == 0) { "KSP failed with exit code $exit." }

    sortLocales(outputDir)
    val generated = outputDir.walk().filter { it.name.endsWith(".kt") }.count()
    check(generated > 0) { "KSP generated no Kotlin file in $outputDir." }
    println("KSP generated $generated Kotlin files.")
}

/**
 * Fails when a processor has another version than its runtime library in the catalog. [pins]
 * maps a processor artifact (such as `ktorfit-ksp`) to a catalog version key (such as `ktorfit`).
 */
private fun checkProcessorVersions(processorJars: List<Path>, catalog: Path, pins: Map<String, String>) {
    val versions = Regex("""^([A-Za-z0-9_-]+)\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
        .findAll(catalog.readText().substringAfter("[versions]").substringBefore("\n["))
        .associate { it.groupValues[1] to it.groupValues[2] }
    val wrong = pins.mapNotNull { (artifact, key) ->
        val expected = versions[key] ?: return@mapNotNull "the catalog has no version \"$key\""
        val jar = processorJars.firstOrNull { it.name.startsWith("$artifact-") && it.name.endsWith(".jar") }
            ?: return@mapNotNull "no $artifact jar in the processor classpath"
        val actual = jar.name.removePrefix("$artifact-").removeSuffix(".jar")
        if (actual == expected) null else "$artifact is $actual, but the catalog has $key = $expected"
    }
    check(wrong.isEmpty()) {
        "The KSP processors must match their libraries. Change the coordinates in build-plugins/common-ksp/plugin.yaml:\n  " +
            wrong.joinToString("\n  ")
    }
}

/**
 * Puts the generated language table in the same order on every machine.
 *
 * Lyricist writes the `Locales` object and the `appStrings` map in the order that it walked the
 * resource folders, and that order comes from the file system. A different order changes the
 * compiled app, so nobody could rebuild a published APK and get the same bytes.
 */
@OptIn(ExperimentalPathApi::class)
private fun sortLocales(outputDir: Path) {
    val file = outputDir.walk().filter { it.name.endsWith(".kt") }.firstOrNull { "public object Locales" in it.readText() }
        ?: error("No generated file holds the Locales object. Look in $outputDir.")
    val text = file.readText()
    val sorted = LocaleOrder.sort(text, file.name)
    if (sorted != text) file.writeText(sorted)
}
