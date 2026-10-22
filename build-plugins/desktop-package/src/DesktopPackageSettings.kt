package io.github.yuroyami.synkplay.buildplugins.desktoppackage

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

@Configurable
interface DesktopPackageSettings {
    /** The name of the app and of the installer. */
    val packageName: String

    /** The app version, such as `0.26.0`. On macOS, 0.x.y becomes 1.x.y, because jpackage rejects a 0 major version there. */
    val packageVersion: String

    val description: String?

    val vendor: String?

    /** JVM options that the launcher passes to the app, after the Compose defaults. */
    val jvmArgs: List<String> get() = emptyList()

    /** The macOS bundle identifier. */
    val macBundleId: String?

    /** The Windows Start menu group. */
    val windowsMenuGroup: String?

    /** A fixed Windows upgrade code, so an MSI upgrade replaces the installed version. */
    val windowsUpgradeUuid: String?

    /** True installs the MSI for the current user only, without administrator rights. */
    val windowsPerUserInstall: Boolean get() = false

    /** The icon foreground (the mark), relative to the module directory. */
    val logoForeground: Path

    /** The icon background, drawn to cover the whole icon, relative to the module directory. */
    val logoBackground: Path

    /** The percentage of each side of the icon that the mark fills, from 1 to 100. */
    val logoPercent: Int get() = 100
}
