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

    const val versionName = "0.18.1"
    val versionCode = ("1" + versionName.split(".").joinToString("") { it.padStart(3, '0') }).toInt()

    const val exoOnly = false

    const val ndkRequired = "29.0.14206865"

    val giphyApiKey = Properties()
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


    fun Project.updateIOSVersion() {
        val pbxprojFile = File("${rootDir}/iosApp/iosApp.xcodeproj/project.pbxproj")
        if (!pbxprojFile.exists()) {
            logger.warn("project.pbxproj not found at: ${pbxprojFile.absolutePath}")
            return
        }

        val original = pbxprojFile.readText()
        val updated = original
            .replace(Regex("""MARKETING_VERSION = [^;]+;"""), "MARKETING_VERSION = $versionName;")
            .replace(Regex("""CURRENT_PROJECT_VERSION = [^;]+;"""), "CURRENT_PROJECT_VERSION = $versionCode;")

        if (updated != original) {
            pbxprojFile.writeText(updated)
            logger.lifecycle("✅ Xcode version updated to $versionName ($versionCode)")
        } else {
            logger.warn("⚠️ Version fields not found in project.pbxproj")
        }
    }
}
