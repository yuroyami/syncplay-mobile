import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * The APKs that one variant's last packaging produced, as AGP lists them in the folder's
 * output-metadata.json. The folder can still hold APKs of older builds, which this skips.
 */
internal fun currentApks(apkDir: File): List<File> {
    val metadata = File(apkDir, "output-metadata.json")
    if (!metadata.isFile) return emptyList()
    return Regex(""""outputFile"\s*:\s*"([^"]+)"""").findAll(metadata.readText())
        .map { File(apkDir, it.groupValues[1]) }
        .filter { it.isFile }
        .toList()
}

/** The native libraries inside [apk], as their paths under `lib/`. */
internal fun nativeLibraries(apk: File): Set<String> = ZipFile(apk).use { zip ->
    zip.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") }.toSet()
}

/**
 * Checks the finished files: an exoOnly APK (the ExoPlayer-only Android flavor) carries no native
 * player library. [apkDir] is the variant's APK folder, so the gate runs after its packaging. The
 * exclusion list in androidApp/build.gradle.kts removes the libraries; this gate proves it.
 */
fun Project.registerExoOnlyApkGate(variant: String, apkDir: Provider<Directory>): TaskProvider<*> {
    val forbiddenPrefixes = listOf("libmpv", "libmpvkt_jni", "libav", "libsw", "libkitecodec", "libkiteplayer", "libc++_shared")
    return tasks.register("verify" + variant.replaceFirstChar { it.uppercase() } + "Apk") {
        group = "verification"
        description = "Fails if the $variant APK carries a native player library."
        inputs.dir(apkDir)
        doLast {
            val offenders = currentApks(apkDir.get().asFile).flatMap { apk ->
                nativeLibraries(apk)
                    .filter { name -> forbiddenPrefixes.any { name.substringAfterLast('/').startsWith(it) } }
                    .map { "${apk.name}: $it" }
            }
            if (offenders.isNotEmpty()) {
                throw GradleException(
                    "exoOnly must ship no native player library, but the APK carries:\n" +
                        offenders.joinToString("\n") { "  $it" } +
                        "\nCheck AppConfig.libmpvNativeLibs against what the pinned libmpvkt version ships.",
                )
            }
            logger.lifecycle("[Synkplay] the $variant APK carries no native player library")
        }
    }
}
