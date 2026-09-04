import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * androidReleaseAll: builds every shippable Android artifact into AndroidAppOutput/
 * (5 full ABI-split APKs + 1 exoOnly universal APK + 1 full AAB) via THREE isolated
 * `./gradlew` sub-builds. Three processes are mandatory: -PexoOnly flips the whole
 * project model (one flavor per invocation), and ABI splits + AAB can't share a task
 * graph (AGP issuetracker 402800800). Full rationale: CLAUDE.md "Release artifacts".
 */
abstract class AndroidReleaseAllTask @Inject constructor(
    private val execOps: ExecOperations,
    private val fsOps: FileSystemOperations,
) : DefaultTask() {

    @get:Input
    abstract val versionName: Property<String>

    @get:Internal
    abstract val gradlewScript: RegularFileProperty

    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @get:Internal
    abstract val fullApkDir: DirectoryProperty

    @get:Internal
    abstract val exoApkDir: DirectoryProperty

    @get:Internal
    abstract val fullAabDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private fun gradle(vararg args: String) {
        val script = gradlewScript.get().asFile
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val launcher = if (isWindows) listOf("cmd", "/c", script.absolutePath) else listOf(script.absolutePath)
        execOps.exec {
            workingDir = repoRoot.get().asFile
            commandLine(launcher + args)
            // Default isIgnoreExitValue = false, so a failed sub-build aborts the whole task.
        }
    }

    @TaskAction
    fun run() {
        val v = versionName.get()

        logger.lifecycle("androidReleaseAll: [1/3] full release APKs (ABI-split)...")
        gradle(":androidApp:assembleFullRelease", "-PexoOnly=false")

        logger.lifecycle("androidReleaseAll: [2/3] exoOnly release APK...")
        gradle(":androidApp:assembleExoOnlyRelease", "-PexoOnly=true")

        logger.lifecycle("androidReleaseAll: [3/3] full release AAB...")
        gradle(":androidApp:bundleFullRelease", "-PexoOnly=false")

        /* Fresh output dir; copy only THIS version's files (build dirs keep stale
         * older-version APKs around because the artifact name changes per release). */
        val out = outputDir.get().asFile
        fsOps.delete { delete(out) }
        out.mkdirs()
        fsOps.copy {
            from(fullApkDir) { include("*-$v-*.apk") }
            from(exoApkDir) { include("*-$v-*.apk") }
            from(fullAabDir) {
                include("*.aab")
                rename { "synkplay-$v-full.aab" }
            }
            into(out)
        }

        val produced = out.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()

        // Nothing checked any of this before: an unsigned APK, or one left over from a version
        // that no longer matches, would have been copied out and uploaded without a word.
        verifyArtifacts(produced, v)

        logger.lifecycle("androidReleaseAll: done. ${produced.size} artifact(s) in AndroidAppOutput/:")
        produced.forEach { logger.lifecycle("    ${it.name}  (${it.length() / 1_000_000} MB)") }
    }

    /**
     * Refuses to finish on a set of artifacts that is not what a release is supposed to be: the
     * expected count, every file named for this version, and every APK carrying a signature that
     * apksigner accepts. A bundle is signed at upload, so only its name is checked.
     */
    private fun verifyArtifacts(produced: List<File>, version: String) {
        val apks = produced.filter { it.name.endsWith(".apk") }
        val aabs = produced.filter { it.name.endsWith(".aab") }

        require(apks.size == EXPECTED_APKS) {
            "androidReleaseAll: expected $EXPECTED_APKS APKs, found ${apks.size}: ${apks.map { it.name }}"
        }
        require(aabs.size == 1) {
            "androidReleaseAll: expected 1 app bundle, found ${aabs.size}: ${aabs.map { it.name }}"
        }
        produced.forEach { file ->
            require(version in file.name) {
                "androidReleaseAll: ${file.name} does not carry version $version, so it is from another build"
            }
        }

        val apksigner = findApksigner()
        if (apksigner == null) {
            logger.warn("androidReleaseAll: apksigner not found, signatures NOT verified. Set ANDROID_HOME.")
            return
        }
        apks.forEach { apk ->
            val out = java.io.ByteArrayOutputStream()
            val result = execOps.exec {
                commandLine(apksigner.absolutePath, "verify", "--print-certs", apk.absolutePath)
                standardOutput = out
                errorOutput = out
                isIgnoreExitValue = true
            }
            require(result.exitValue == 0) {
                "androidReleaseAll: ${apk.name} failed signature verification:\n${out.toString().trim()}"
            }
        }
        logger.lifecycle("androidReleaseAll: ${apks.size} APK signature(s) verified.")
    }

    /** The newest apksigner in the local SDK, or null when there is no SDK to look in. */
    private fun findApksigner(): File? {
        val sdk = listOfNotNull(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
        ).map(::File).firstOrNull { it.isDirectory } ?: return null

        return File(sdk, "build-tools").listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.reversed()
            ?.map { File(it, "apksigner") }
            ?.firstOrNull { it.canExecute() }
    }
}

private const val EXPECTED_APKS = 6

/** Registers `androidReleaseAll` on the root project. [version] comes from the
 *  root kiteConfig { } block: buildSrc compiles before plugins apply, so it
 *  cannot read the accessor itself. */
fun Project.registerAndroidReleaseAllTask(version: String) {
    tasks.register<AndroidReleaseAllTask>("androidReleaseAll") {
        group = "syncplay"
        description = "Build all release APKs (full ABI-split + exoOnly) plus the full-flavor AAB into AndroidAppOutput/."

        // Real work happens in nested builds whose outputs Gradle can't track from here.
        outputs.upToDateWhen { false }

        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        versionName.set(version)
        gradlewScript.set(layout.projectDirectory.file(if (isWindows) "gradlew.bat" else "gradlew"))
        repoRoot.set(layout.projectDirectory)
        fullApkDir.set(layout.projectDirectory.dir("androidApp/build/outputs/apk/full/release"))
        exoApkDir.set(layout.projectDirectory.dir("androidApp/build/outputs/apk/exoOnly/release"))
        fullAabDir.set(layout.projectDirectory.dir("androidApp/build/outputs/bundle/fullRelease"))
        outputDir.set(layout.projectDirectory.dir("AndroidAppOutput"))
    }
}
