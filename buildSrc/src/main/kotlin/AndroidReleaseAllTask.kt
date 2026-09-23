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
 * androidReleaseAll: builds every shippable Android artifact into AndroidAppOutput/: one full
 * universal APK, one exoOnly universal APK and one full AAB. The exoOnly flavor is the Android
 * build with ExoPlayer only and no native player library.
 *
 * The task runs two separate `./gradlew` sub-builds. -PexoOnly changes the whole project model,
 * so one invocation builds only one flavor. The full APK and the AAB share one invocation, which
 * works only while there are no ABI splits (AGP issuetracker 402800800).
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

        logger.lifecycle("androidReleaseAll: [1/2] full release APK and AAB...")
        gradle(":androidApp:assembleFullRelease", ":androidApp:bundleFullRelease", "-PexoOnly=false")

        logger.lifecycle("androidReleaseAll: [2/2] exoOnly release APK...")
        gradle(":androidApp:assembleExoOnlyRelease", "-PexoOnly=true")

        /* Start from an empty output folder and copy only this version's files. The build
         * folders keep older APKs, because the artifact name changes with each release. */
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

        // Without this check, an unsigned APK, or an APK left over from another version, gets
        // copied out and uploaded without any warning.
        verifyArtifacts(produced, v)

        logger.lifecycle("androidReleaseAll: done. ${produced.size} artifact(s) in AndroidAppOutput/:")
        produced.forEach { logger.lifecycle("    ${it.name}  (${it.length() / 1_000_000} MB)") }
    }

    /**
     * Fails the task unless the artifacts match a release: the expected count, every file named
     * for this version, and every APK signed so that apksigner accepts it. A bundle is signed at
     * upload, so only its name is checked.
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

    /** The newest apksigner in the local SDK, or null when there is no SDK or no apksigner. */
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

private const val EXPECTED_APKS = 2

/** Registers `androidReleaseAll` on the root project. [version] comes from the root
 *  kiteConfig block: buildSrc compiles before plugins apply, so it cannot read the
 *  accessor itself. */
fun Project.registerAndroidReleaseAllTask(version: String) {
    tasks.register<AndroidReleaseAllTask>("androidReleaseAll") {
        group = "syncplay"
        description = "Build the two release APKs (full universal + exoOnly) plus the full-flavor AAB into AndroidAppOutput/."

        // The real work happens in nested builds, whose outputs Gradle cannot track from here.
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
