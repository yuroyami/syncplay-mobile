import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/** KitePlayer's decoder. Without it on an ABI, KitePlayer cannot start on devices of that ABI. */
private const val KITE_DECODER = "libkitecodec_jni.so"

/**
 * ABIs that KitePlayer does not build for. mpv and ExoPlayer still run there, and KitePlayer
 * reports itself unavailable, so the engine picker does not offer it.
 */
private val ABIS_WITHOUT_KITE = setOf("x86")

/**
 * Checks the finished full APK: every ABI it packages carries the KitePlayer decoder. [apkDir] is
 * the variant's APK folder, so the gate runs after its packaging.
 */
fun Project.registerKiteDecoderApkGate(variant: String, apkDir: Provider<Directory>): TaskProvider<*> =
    tasks.register("verify" + variant.replaceFirstChar { it.uppercase() } + "Decoders") {
        group = "verification"
        description = "Fails if the $variant APK packages an ABI without the KitePlayer decoder."
        inputs.dir(apkDir)
        doLast {
            val gaps = currentApks(apkDir.get().asFile).flatMap { apk ->
                val libs = nativeLibraries(apk)
                libs.map { it.split('/')[1] }.distinct().sorted()
                    .filter { abi -> abi !in ABIS_WITHOUT_KITE && "lib/$abi/$KITE_DECODER" !in libs }
                    .map { abi -> "${apk.name}: $abi has no $KITE_DECODER" }
            }
            if (gaps.isNotEmpty()) {
                throw GradleException(
                    "KitePlayer cannot start on these ABIs of the full APK:\n" +
                        gaps.joinToString("\n") { "  $it" } +
                        "\nUse a KitePlayer version that ships its decoder for every packaged ABI.",
                )
            }
            logger.lifecycle("[Synkplay] the $variant APK carries the KitePlayer decoder for every ABI")
        }
    }
