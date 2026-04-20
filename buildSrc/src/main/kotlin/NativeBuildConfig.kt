import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import java.io.File
import java.nio.file.Files

/**
 * Encapsulates native-build related Gradle logic (NDK validation,
 * mpv build scripts) so that androidApp/build.gradle.kts stays
 * declarative and concise.
 *
 * Uses only Gradle-native types — no Android Gradle Plugin imports —
 * to avoid classpath conflicts in buildSrc.
 */
object NativeBuildConfig {

    /**
     * Validates that the correct NDK version is installed.
     * Should be called inside `afterEvaluate` for the androidApp module.
     *
     * @param ndkPath The resolved NDK directory
     * @param requiredVersion The expected NDK version string
     */
    fun Project.validateNdk(
        ndkPath: File,
        requiredVersion: String
    ) {
        if (!ndkPath.exists()) {
            throw GradleException(
                """
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                ❌ ANDROID NDK $requiredVersion REQUIRED!
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

                Install via:
                  • Android Studio → SDK Manager → SDK Tools →
                    NDK (Side by side) → Show Package Details →
                    Check version $requiredVersion

                Or add to local.properties:
                  ndk.dir=/path/to/android/sdk/ndk/$requiredVersion
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                """.trimIndent()
            )
        }

        val actualVersion = ndkPath.name
        if (actualVersion != requiredVersion) {
            throw GradleException(
                """
                ❌ Wrong NDK version!
                Required: $requiredVersion
                Found: $actualVersion at ${ndkPath.absolutePath}

                Please install NDK $requiredVersion via SDK Manager.
                """.trimIndent()
            )
        }

        logger.lifecycle("✓ NDK $actualVersion found at: ${ndkPath.absolutePath}")
    }

    /**
     * Registers the `runAndroidMpvNativeBuildScripts` Exec task that
     * downloads mpv dependencies and cross-compiles native libraries
     * for all ABI targets.
     *
     * @param sdkPathProvider Provider for the Android SDK directory path
     * @param ndkPathProvider Provider for the Android NDK directory path
     */
    fun Project.registerNativeBuildTask(
        sdkPathProvider: () -> File,
        ndkPathProvider: () -> File
    ) {
        tasks.register("runAndroidMpvNativeBuildScripts", Exec::class.java) {
            workingDir = File(rootProject.rootDir, "buildscripts")

            inputs.files(
                File(workingDir, "mpv_download_deps.sh"),
                File(workingDir, "mpv_build.sh")
            )

            inputs.property("ndkVersion", providers.gradleProperty("android.ndkVersion").get())
                .optional(false)

            AppConfig.abiCodes.forEach { abiCode ->
                AppConfig.mpvLibs.forEach { mpvLib ->
                    outputs.file(File(projectDir, "src/main/libs/${abiCode.key}/$mpvLib"))
                }
            }

            outputs.cacheIf { true }

            if (System.getProperty("os.name").startsWith("Windows")) {
                doFirst {
                    logger.warn("Native library build is not supported on Windows. Skipping...")
                }
                isEnabled = false
            } else {
                doFirst {
                    val sdkPath = sdkPathProvider()
                    val ndkPath = ndkPathProvider()

                    if (!ndkPath.exists()) {
                        throw GradleException(
                            "Android NDK is required but not found!\n" +
                                    "Please install NDK via Android Studio SDK Manager or set ndk.dir in local.properties"
                        )
                    }

                    environment("ANDROID_SDK_ROOT", sdkPath.absolutePath)
                    environment("ANDROID_NDK_HOME", ndkPath.absolutePath)

                    println("✓ NDK found at: ${ndkPath.absolutePath}")

                    val osName = System.getProperty("os.name").lowercase()
                    val myOS = when {
                        osName.contains("mac") || osName.contains("darwin") -> "mac"
                        osName.contains("linux") -> "linux"
                        osName.contains("windows") -> "windows"
                        else -> "unknown"
                    }

                    logger.lifecycle("Detected OS: $myOS")

                    val sdkSymlinkDir = File(workingDir, "sdk")
                    val symlink = File(sdkSymlinkDir, "android-sdk-$myOS")

                    if (!sdkSymlinkDir.exists()) {
                        sdkSymlinkDir.mkdirs()
                    }

                    if (symlink.exists()) {
                        if (Files.isSymbolicLink(symlink.toPath())) {
                            Files.delete(symlink.toPath())
                            logger.lifecycle("Removed old symlink: ${symlink.absolutePath}")
                        } else {
                            throw GradleException("${symlink.absolutePath} exists but is not a symlink!")
                        }
                    }

                    try {
                        Files.createSymbolicLink(symlink.toPath(), sdkPath.toPath())
                        logger.lifecycle("✓ Created symlink: ${symlink.absolutePath} -> ${sdkPath.absolutePath}")
                    } catch (e: Exception) {
                        throw GradleException("Failed to create symlink: ${e.message}")
                    }

                    commandLine(
                        "sh", "-c", """
                        sh mpv_download_deps.sh "$sdkPath" "$ndkPath" &&
                        sh mpv_build.sh --arch armv7l mpv &&
                        sh mpv_build.sh --arch arm64 mpv &&
                        sh mpv_build.sh --arch x86 mpv &&
                        sh mpv_build.sh --arch x86_64 mpv &&
                        sh mpv_build.sh -n syncplay-withmpv
                    """.trimIndent()
                    )

                    logger.lifecycle("Running: ${commandLine.joinToString(" ")}")
                }
            }

            onlyIf {
                val allFilesExist = AppConfig.abiCodes.all { abiCode ->
                    AppConfig.mpvLibs.all { mpvLib ->
                        File(projectDir, "src/main/libs/${abiCode.key}/$mpvLib").exists()
                    }
                }

                if (allFilesExist) {
                    logger.lifecycle("✓ All MPV libs exist, skipping build")
                } else {
                    logger.lifecycle("✗ Some MPV libs missing, will build")
                }

                !allFilesExist
            }
        }
    }
}
