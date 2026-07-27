import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import java.io.File
import java.nio.file.Files

/**
 * Native-build Gradle logic (NDK validation, mpv cross-compile task), kept out of
 * androidApp/build.gradle.kts. Uses only Gradle-native types — no AGP imports — to avoid
 * buildSrc classpath conflicts.
 */
object NativeBuildConfig {

    /**
     * Fails the build unless the exact [requiredVersion] NDK is present at [ndkPath].
     * Call inside `afterEvaluate` for the androidApp module.
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
     * Registers the `runAndroidMpvNativeBuildScripts` Exec task: downloads mpv deps and
     * cross-compiles the native libs for every ABI. Disabled on Windows.
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
                // Gate the heavy native build on the mpv libs only. The libc++_shared.so byproduct
                // is restored cheaply from the NDK by androidApp's restoreMpvLibcxx task, so its
                // absence must NOT force a full mpv recompile (which would punish exoOnly -> full
                // flavor flips). See restoreMpvLibcxx / verifyMpvLibcxx in androidApp/build.gradle.kts.
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

    /**
     * Full flavor: keep the mpv-matching NDK r29 libc++_shared.so next to the mpv libs and
     * refuse to package without it. libVLC's AAR ships an older libc++ that lacks
     * __from_chars_floating_point — packaging that one crashes mpv at load. Full story:
     * CLAUDE.md "Android-only native gotchas".
     */
    fun Project.registerMpvLibcxxGuards(ndkDirProvider: Provider<Directory>) {
        // Restores libc++ straight from the NDK (exact file ndk-build would emit) so an
        // exoOnly -> full flavor flip never forces a full mpv recompile. Only acts on ABIs
        // that ship libmpv.so and only when libc++ is actually missing.
        val restoreMpvLibcxx = tasks.register("restoreMpvLibcxx") {
            dependsOn("runAndroidMpvNativeBuildScripts")
            doLast {
                val prebuilt = File(ndkDirProvider.get().asFile, "toolchains/llvm/prebuilt")
                    .listFiles()?.firstOrNull { it.isDirectory }
                    ?: throw GradleException("Could not locate the NDK llvm prebuilt toolchain directory")
                val sysrootLib = File(prebuilt, "sysroot/usr/lib")
                val triples = mapOf(
                    "armeabi-v7a" to "arm-linux-androideabi",
                    "arm64-v8a" to "aarch64-linux-android",
                    "x86" to "i686-linux-android",
                    "x86_64" to "x86_64-linux-android",
                )
                AppConfig.abiCodes.keys.forEach { abi ->
                    if (!File(projectDir, "src/main/libs/$abi/libmpv.so").exists()) return@forEach
                    val dst = File(projectDir, "src/main/libs/$abi/libc++_shared.so")
                    if (dst.exists()) return@forEach
                    val src = File(sysrootLib, "${triples[abi]}/libc++_shared.so")
                    if (!src.exists()) throw GradleException("NDK libc++ not found at $src")
                    src.copyTo(dst, overwrite = true)
                    logger.lifecycle("✓ Restored mpv-matching libc++_shared.so for $abi from the NDK")
                }
            }
        }

        // The marker is a fragment of __from_chars_floating_point's mangled name — present
        // verbatim in any correct NDK r29 libc++ and absent from VLC's older one.
        val verifyMpvLibcxx = tasks.register("verifyMpvLibcxx") {
            dependsOn(restoreMpvLibcxx)
            doLast {
                val marker = "from_chars_floating"
                AppConfig.abiCodes.keys.forEach { abi ->
                    if (!File(projectDir, "src/main/libs/$abi/libmpv.so").exists()) return@forEach
                    val lib = File(projectDir, "src/main/libs/$abi/libc++_shared.so")
                    if (!lib.exists()) throw GradleException(
                        "$abi ships libmpv.so but is missing libc++_shared.so even after restore. " +
                            "Delete src/main/libs and rebuild so the native build regenerates it from NDK r29."
                    )
                    if (!lib.readBytes().toString(Charsets.ISO_8859_1).contains(marker)) throw GradleException(
                        "$lib does not contain '$marker'. This looks like libVLC's older libc++, not " +
                            "the NDK r29 one mpv needs; the APK would crash on mpv load. Regenerate the " +
                            "local libc++ from NDK r29 before packaging."
                    )
                }
            }
        }

        tasks.named("preBuild") { dependsOn(verifyMpvLibcxx) }
    }

    /**
     * exoOnly flavor: prune the full-flavor native build's libc++ byproduct so the APK
     * deterministically packages the VLC AAR's libc++, identical to a clean checkout —
     * a reproducible-builds requirement (issue #105). See CLAUDE.md.
     */
    fun Project.registerExoOnlyLibcxxPrune() {
        val prune = tasks.register("pruneStaleExoOnlyLibcxx", Delete::class.java) {
            delete(fileTree("src/main/libs") { include("**/libc++_shared.so") })
        }
        tasks.named("preBuild") { dependsOn(prune) }
    }
}
