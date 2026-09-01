import io.github.yuroyami.kiteconfig.kiteConfig
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlin.coroutines.swing)

    /* :shared declares its deps with `implementation`, so types leaking through its public
     * API (ViewModel supertypes) must be redeclared here. */
    implementation(libs.compose.viewmodel)

}

/* Same explicit Skiko pin as :shared — see the comment there. */
configurations.configureEach {
    resolutionStrategy.force("org.jetbrains.skiko:skiko:${libs.versions.skiko.get()}")
}

compose.desktop {
    application {
        mainClass = "app.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            // packageName / packageVersion come from the root kiteConfig { } block.
            description = "Synchronized media playback with Syncplay"
            vendor = "yuroyami"

            /* Full JRE module set: Netty, JNA, OkHttp and DataStore each reach into different
             * jdk.* modules, and a trimmed jlink image fails at RUNTIME. Correctness > size. */
            includeAllModules = true

            macOS {
                // bundleID comes from kiteConfig { id { desktop { suffix } } }.
                // jpackage rejects 0.x.y: macOS CFBundleVersion must start at 1.
                packageVersion = kiteConfig.version.map {
                    if (it.startsWith("0.")) "1." + it.removePrefix("0.") else it
                }.get()
            }
            windows {
                menuGroup = kiteConfig.appName.get()
                // Stable GUID so MSI upgrades replace the previous install instead of duplicating it.
                upgradeUuid = "9E2B62D1-5C3A-4A8F-9C41-3B7E2B0C11D7"
                perUserInstall = true
            }
            linux {
                packageName = kiteConfig.appName.get().lowercase()
            }
        }
    }
}

/* No bundled player natives any more. Native players used to be downloaded into
 * desktopApp/resources/<os>-<arch>/ and packaged into the app image, 277 MB of it on macOS alone.
 * The desktop build runs one engine now, KitePlayer, and its decoder rides inside the KiteCodec
 * jar, so there is nothing left to bundle and no appResourcesRootDir to point at. */
