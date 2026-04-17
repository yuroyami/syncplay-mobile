import org.gradle.api.Project
import java.io.File
import java.util.Properties

object AppConfig {
    val localProperties = Properties().apply {
        val file = File("local.properties")
        if (file.exists()) load(file.inputStream())
    }

    const val javaVersion = 21

    const val compileSdk = 36
    const val minSdk = 26

    const val appName = "Synkplay"

    const val versionName = "0.21.0"
    val versionCode = ("1" + versionName.split(".").joinToString("") { it.padStart(3, '0') }).toInt()

    const val exoOnly = false

    const val ndkRequired = "29.0.14206865"

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

    /* ── Propagation: iOS project version + app name ───────────────────────────────────────────── */
    fun Project.updateIOSVersionAndAppName() {
        val pbxprojFile = File("${rootDir}/iosApp/iosApp.xcodeproj/project.pbxproj")
        if (!pbxprojFile.exists()) {
            logger.warn("project.pbxproj not found at: ${pbxprojFile.absolutePath}")
            return
        }

        val original = pbxprojFile.readText()
        val updated = original
            .replace(Regex("""MARKETING_VERSION = [^;]+;"""), "MARKETING_VERSION = $versionName;")
            .replace(Regex("""CURRENT_PROJECT_VERSION = [^;]+;"""), "CURRENT_PROJECT_VERSION = $versionCode;")
            .replace(Regex("""INFOPLIST_KEY_CFBundleDisplayName = [^;]+;"""), "INFOPLIST_KEY_CFBundleDisplayName = $appName;")
            .replace(Regex("""PRODUCT_NAME = [^;]+;"""), "PRODUCT_NAME = $appName;")

        if (updated != original) {
            pbxprojFile.writeText(updated)
            logger.lifecycle("✅ Xcode version updated to $versionName ($versionCode)")
        } else {
            logger.warn("⚠️ Version fields not found in project.pbxproj")
        }

        // Sync app name to Config.xcconfig
        val xcconfigFile = File("${rootDir}/iosApp/Configuration/Config.xcconfig")
        if (xcconfigFile.exists()) {
            val xcOriginal = xcconfigFile.readText()
            val xcUpdated = xcOriginal.replace(Regex("""APP_NAME=.*"""), "APP_NAME=$appName")
            if (xcUpdated != xcOriginal) {
                xcconfigFile.writeText(xcUpdated)
                logger.lifecycle("✅ Config.xcconfig APP_NAME updated to $appName")
            }
        }
    }

    /* ── Propagation: locales from composeResources → iOS (Info.plist + pbxproj knownRegions) ─── */
    fun Project.propagateLocalesToIOS() {
        // Discover locales from composeResources/values-* directories
        val composeResDir = File("${rootDir}/shared/src/commonMain/composeResources")
        if (!composeResDir.exists()) {
            logger.warn("composeResources directory not found")
            return
        }

        val locales = composeResDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values-") }
            ?.map { it.name.removePrefix("values-") }
            ?.sorted()
            ?: return

        if (locales.isEmpty()) return

        // 1. Update Info.plist CFBundleLocalizations
        val plistFile = File("${rootDir}/iosApp/iosApp/Info.plist")
        if (plistFile.exists()) {
            val plistOriginal = plistFile.readText()
            val localeEntries = locales.joinToString("\n") { "\t\t<string>$it</string>" }
            val plistUpdated = plistOriginal.replace(
                Regex("""<key>CFBundleLocalizations</key>\s*<array>.*?</array>""", RegexOption.DOT_MATCHES_ALL),
                "<key>CFBundleLocalizations</key>\n\t<array>\n$localeEntries\n\t</array>"
            )
            if (plistUpdated != plistOriginal) {
                plistFile.writeText(plistUpdated)
                logger.lifecycle("✅ Info.plist CFBundleLocalizations updated: $locales")
            }
        }

        // 2. Update project.pbxproj knownRegions
        val pbxprojFile = File("${rootDir}/iosApp/iosApp.xcodeproj/project.pbxproj")
        if (pbxprojFile.exists()) {
            val pbxOriginal = pbxprojFile.readText()
            val regionEntries = (locales + "Base").joinToString(",\n") { "\t\t\t\t$it" }
            val pbxUpdated = pbxOriginal.replace(
                Regex("""knownRegions = \(\s*.*?\);""", RegexOption.DOT_MATCHES_ALL),
                "knownRegions = (\n$regionEntries,\n\t\t\t);"
            )
            if (pbxUpdated != pbxOriginal) {
                pbxprojFile.writeText(pbxUpdated)
                logger.lifecycle("✅ project.pbxproj knownRegions updated: ${locales + "Base"}")
            }
        }
    }

    /* ── Propagation: trinity colors → Android vector drawable gradients ────────────────────────── */
    fun Project.propagateTrinityColors() {
        val hex1 = "#FF${TRINITY_1.toString(16).takeLast(6).uppercase()}"
        val hex2 = "#FF${TRINITY_2.toString(16).takeLast(6).uppercase()}"
        val hex3 = "#FF${TRINITY_3.toString(16).takeLast(6).uppercase()}"

        // Update ic_launcher_foreground.xml (uses 3-stop radial gradient)
        val fgFile = File("${rootDir}/shared/src/androidMain/res/drawable/ic_launcher_foreground.xml")
        if (fgFile.exists()) {
            val original = fgFile.readText()
            // Replace all 3-stop radial gradients with the trinity colors
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

    /* ── Propagation: logo SVG/PNG from composeResources → iOS Assets.xcassets ──────────────────── */
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

    /* ── Master propagation: invoke all SSOT propagators at once ────────────────────────────────── */
    fun Project.propagateAll() {
        updateIOSVersionAndAppName()
        propagateLocalesToIOS()
        propagateTrinityColors()
        propagateLogoToIOS()
        propagateDefaultStrings()
    }
}
