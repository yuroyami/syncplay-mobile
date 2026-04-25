import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * What lives here: things the kmp-ssot plugin (io.github.yuroyami.kmpssot) does NOT cover.
 * Plugin owns: appName / versionName / versionCode / bundleId / locales propagation
 * to Android + iOS. Toolchain (compileSdk/minSdk/ndkVersion) lives in gradle.properties.
 *
 * AppConfig keeps:
 *  - localProperties (signing secrets — never propagate)
 *  - exoOnly flavor flag + abi/mpv lib lists (build/packaging logic)
 *  - Trinity brand colors + custom propagators (drawable XML rewrite, logo imageset
 *    copy, default-strings fallback) that the plugin's narrow scope doesn't address
 */
object AppConfig {
    val localProperties = Properties().apply {
        val file = File("local.properties")
        if (file.exists()) load(file.inputStream())
    }

    const val exoOnly = false

    /* ── Trinity brand colors (SSOT for the logo gradient) ──────────────────────────────────────── */
    const val TRINITY_1 = 0xFF4FD1FF  // Cyan
    const val TRINITY_2 = 0xFF5A7CFF  // Blue
    const val TRINITY_3 = 0xFF7A3CFF  // Purple

    val abiCodes = mapOf(
        "armeabi-v7a" to "armv7l",
        "arm64-v8a" to "arm64",
        "x86" to "x86",
        "x86_64" to "x86_64"
    )

    val mpvLibs = listOf(
        "libavcodec.so", "libavdevice.so", "libavfilter.so",
        "libavformat.so", "libavutil.so", "libmpv.so", "libplayer.so",
        "libswresample.so", "libswscale.so"
    )

    /* ── Propagation: trinity colors → Android vector drawable gradients ────────────────────────── */
    fun Project.propagateTrinityColors() {
        val hex1 = "#FF${TRINITY_1.toString(16).takeLast(6).uppercase()}"
        val hex2 = "#FF${TRINITY_2.toString(16).takeLast(6).uppercase()}"
        val hex3 = "#FF${TRINITY_3.toString(16).takeLast(6).uppercase()}"

        val fgFile = File("${rootDir}/shared/src/androidMain/res/drawable/ic_launcher_foreground.xml")
        if (fgFile.exists()) {
            val original = fgFile.readText()
            val updated = original
                .replace(Regex("""(<item android:offset="0" android:color=")#[0-9A-Fa-f]+("/>)""")) { m ->
                    "${m.groupValues[1]}$hex1${m.groupValues[2]}"
                }
                .replace(Regex("""(<item android:offset="0.5" android:color=")#[0-9A-Fa-f]+("/>)""")) { m ->
                    "${m.groupValues[1]}$hex2${m.groupValues[2]}"
                }
                .replace(Regex("""(<item android:offset="0.9" android:color=")#[0-9A-Fa-f]+("/>)""")) { m ->
                    "${m.groupValues[1]}$hex3${m.groupValues[2]}"
                }
            if (updated != original) {
                fgFile.writeText(updated)
                logger.lifecycle("✅ ic_launcher_foreground.xml trinity colors updated")
            }
        }
    }

    /* ── Propagation: logo PNG from composeResources → iOS Assets.xcassets imageset ─────────────── */
    fun Project.propagateLogoToIOS() {
        val sourcePng = File("${rootDir}/shared/src/commonMain/composeResources/drawable/neosyncplay.png")
        val targetImageset = File("${rootDir}/iosApp/iosApp/Assets.xcassets/syncplay_original.imageset/neosyncplay.png")

        if (sourcePng.exists() && targetImageset.parentFile.exists()) {
            sourcePng.copyTo(targetImageset, overwrite = true)
            logger.lifecycle("✅ Logo PNG propagated to iOS Assets.xcassets")
        }
    }

    /* ── Propagation: values-en/strings.xml → values/strings.xml (default fallback) ─────────────── */
    fun Project.propagateDefaultStrings() {
        val src = File("${rootDir}/shared/src/commonMain/composeResources/values-en/strings.xml")
        val dst = File("${rootDir}/shared/src/commonMain/composeResources/values/strings.xml")
        if (src.exists()) {
            src.copyTo(dst, overwrite = true)
            logger.lifecycle("✅ Default strings fallback synced from values-en")
        }
    }

    /* ── Master propagation: invoke all custom (non-plugin) SSOT propagators ────────────────────── */
    fun Project.propagateAllCustom() {
        propagateTrinityColors()
        propagateLogoToIOS()
        propagateDefaultStrings()
    }
}
