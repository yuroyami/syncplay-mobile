package io.github.yuroyami.synkplay.buildplugins.appconfig

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface AppConfigSettings {
    /** The app name in KiteBuildConfig.APP_NAME. */
    val appName: String

    /** The app version for every platform, as `major.minor.patch`. */
    val version: String

    /** Counter for a new upload of the same version (0 to 9). It changes only the build number. */
    val rebuild: Int get() = 0

    /** The package of the generated KiteBuildConfig object. */
    val buildConfigPackage: String

    /** The Klipy API key. It is public: it ships inside every app. */
    val klipyApiKey: String

    /** The OpenSubtitles key to use when neither SYNKPLAY_OPENSUBTITLES_KEY nor local.properties has one. */
    val openSubtitlesKey: String

    /** The three brand colors (the Trinity), as `#AARRGGBB`. */
    val trinityColors: List<String>

    /** `module.yaml` of the Android app, relative to the module directory. */
    val androidModule: Path

    /** `module.yaml` of the desktop app, relative to the module directory. */
    val desktopModule: Path

    /** `project.pbxproj` of the iOS app, relative to the module directory. */
    val xcodeProject: Path

    /** The launcher foreground drawable whose gradient uses the brand colors, relative to the module directory. */
    val launcherForeground: Path

    /** The Play store texts (`fastlane/metadata/android/en-US`), relative to the module directory. */
    val storeMetadata: Path

    /** CHANGELOG.md, relative to the module directory. */
    val changelog: Path

    /** The logo mark (a PNG with transparency) for the app icons, relative to the module directory. */
    val logoForeground: Path

    /** The square background PNG for the app icons, relative to the module directory. */
    val logoBackground: Path

    /** The percentage of each side that the mark fills on the Android launcher icons. Launcher masks cut the outer part. */
    val androidLogoPercent: Int get() = 50

    /** The percentage of each side that the mark fills on the iOS app icon. */
    val iosLogoPercent: Int get() = 80

    /** The `res` folder of the Android app, relative to the module directory. */
    val androidResources: Path

    /** The `AppIcon.appiconset` folder of the iOS app, relative to the module directory. */
    val iosAppIcon: Path
}
