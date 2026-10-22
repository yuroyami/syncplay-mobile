package io.github.yuroyami.synkplay.buildplugins.androidrelease

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.reader
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText

/** The resource that carries the stamp. It lands in the app's classes jar. */
private const val STAMP = "META-INF/synkplay-android-inputs.sha256"

/** `SYNKPLAY_UNSIGNED_RELEASE=true` allows a release build without the signing material. */
private const val UNSIGNED_RELEASE_ENV = "SYNKPLAY_UNSIGNED_RELEASE"

/** The keys that the toolchain reads from the signing properties file. */
private val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

/**
 * Copies the `.so` files under `jni/<abi>/` of [aar] into [jniLibsDir]/<abi>. The Media3 FFmpeg extension ships only
 * as an AAR. Its Java classes are in the shared module as sources, and its native decoder comes
 * from here. jniLibs is not in git: this task writes it on every machine.
 */
@OptIn(ExperimentalPathApi::class)
@TaskAction
fun unpackNativeLibraries(
    @Input aar: Path,
    @Output jniLibsDir: Path,
) {
    jniLibsDir.deleteRecursively()
    var count = 0
    ZipFile(aar.toFile()).use { zip ->
        for (entry in zip.entries()) {
            if (entry.isDirectory || !entry.name.startsWith("jni/") || !entry.name.endsWith(".so")) continue
            val target = jniLibsDir.resolve(entry.name.removePrefix("jni/")).normalize()
            check(target.startsWith(jniLibsDir)) { "${entry.name} points outside $jniLibsDir." }
            target.parent.createDirectories()
            zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            count++
        }
    }
    check(count > 0) { "$aar has no native library." }
    println("Unpacked $count native libraries from ${aar.fileName}.")
}

/**
 * Writes one SHA-256 over every file in [inputs] to [STAMP] under [outputDir]. The toolchain
 * repackages the APK only when the app's classpath or its Android settings change. The stamp is a
 * generated resource of the app, so a change in jniLibs, res, assets, the manifest or the R8 rules
 * changes the app's jar, and the next Android build uses the new files.
 */
@OptIn(ExperimentalPathApi::class)
@TaskAction
fun stampAndroidInputs(
    @Input inputs: List<Path>,
    @Output outputDir: Path,
) {
    val digest = MessageDigest.getInstance("SHA-256")
    for (root in inputs.filter { it.exists() }) {
        val files = if (root.isRegularFile()) sequenceOf(root) else root.walk().filter { it.isRegularFile() }
        for (file in files.sortedBy { it.invariantSeparatorsPathString }) {
            // The path takes part too, so a renamed or moved file also changes the stamp.
            digest.update(file.relativeTo(root.parent).invariantSeparatorsPathString.toByteArray())
            digest.update(file.readBytes())
        }
    }
    val hex = digest.digest().joinToString("") { "%02x".format(it) }
    outputDir.deleteRecursively()
    outputDir.resolve(STAMP).apply { parent.createDirectories() }.writeText("$hex\n")
    println("Android inputs stamp: $hex")
}

/**
 * Fails a release build when the signing material is missing. Without this check, the toolchain
 * only warns and writes an unsigned APK. Set SYNKPLAY_UNSIGNED_RELEASE=true to build an unsigned
 * release on purpose, for example for a reproducible build. The task reads the properties file at
 * build time only. It prints no value from it.
 */
@TaskAction(ExecutionAvoidance.Disabled)
fun guardReleaseSigning(
    moduleName: String,
    moduleDir: String,
    signingEnabled: Boolean,
    propertiesFile: String,
    // Read only for the Maven Local check below. The project is not a task output.
    @Input(inferTaskDependency = false) projectDir: Path,
    @Output outputDir: Path,
) {
    outputDir.createDirectories()
    if (!BuildIntent.isRelease()) return
    // A release resolved from the local Maven repository cannot be rebuilt by anyone else.
    val withMavenLocal = mavenLocalUsers(projectDir)
    check(withMavenLocal.isEmpty()) {
        "$moduleName: a release cannot use the local Maven repository. Remove `mavenLocal` from: ${withMavenLocal.joinToString()}."
    }
    if (System.getenv(UNSIGNED_RELEASE_ENV).equals("true", ignoreCase = true)) {
        println("$moduleName: $UNSIGNED_RELEASE_ENV is set, so the release is not signed.")
        return
    }
    val module = kotlin.io.path.Path(moduleDir)
    check(signingEnabled) { "$moduleName: a release needs settings.android.signing.enabled in module.yaml." }
    val properties = module.resolve(propertiesFile).normalize()
    check(properties.isRegularFile()) {
        "$moduleName: the release signing properties are missing at $properties. " +
            "Create the file with ${signingKeys.joinToString()}, or set $UNSIGNED_RELEASE_ENV=true for an unsigned release."
    }
    val values = Properties().apply { properties.reader().use(::load) }
    signingKeys.forEach { key ->
        check(!values.getProperty(key).isNullOrBlank()) { "$moduleName: the signing property '$key' is missing from $properties." }
    }
    // The toolchain resolves storeFile against the module directory, so this check does too.
    val keystore = module.resolve(values.getProperty("storeFile")).normalize()
    check(keystore.isRegularFile()) { "$moduleName: the release keystore is missing at $keystore." }
    println("$moduleName: the release signing material is in place.")
}

/** The module and template files under [projectDir] that list the `mavenLocal` repository. */
private fun mavenLocalUsers(projectDir: Path): List<String> {
    val entry = Regex("""(?m)^\s*-\s*(url:\s*)?["']?mavenLocal["']?\s*(#.*)?$""")
    return projectDir.toFile().walkTopDown()
        .onEnter { it.name != "build" && !it.name.startsWith(".") }
        .filter { it.isFile && (it.name == "module.yaml" || it.name.endsWith(".module-template.yaml")) }
        .filter { entry.containsMatchIn(it.readText()) }
        .map { it.relativeTo(projectDir.toFile()).path }
        .sorted()
        .toList()
}
