import io.github.yuroyami.kiteconfig.kiteConfig
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlin.coroutines.swing)

    /* :shared declares its dependencies with `implementation`, so the types that its public API
     * exposes (ViewModel supertypes) must be declared again here. */
    implementation(libs.compose.viewmodel)

}

/* The same explicit Skiko pin as in :shared (see the comment there). */
configurations.configureEach {
    resolutionStrategy.force("org.jetbrains.skiko:skiko:${libs.versions.skiko.get()}")
}

compose.desktop {
    application {
        mainClass = "app.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            // packageName and packageVersion come from the root kiteConfig block.
            description = "Synchronized media playback with Syncplay"
            vendor = "yuroyami"

            /* The full JRE module set. Netty, JNA, OkHttp and DataStore each use different
             * jdk.* modules, and a trimmed jlink image fails at runtime. Correctness comes
             * before size. */
            includeAllModules = true

            macOS {
                // The bundle ID comes from KiteConfig. jpackage rejects a 0.x.y version on macOS,
                // because CFBundleVersion must start at 1, so 0.x.y becomes 1.x.y here.
                packageVersion = kiteConfig.version.map {
                    if (it.startsWith("0.")) "1." + it.removePrefix("0.") else it
                }.get()
            }
            windows {
                menuGroup = kiteConfig.appName.get()
                // A fixed GUID, so an MSI upgrade replaces the previous install instead of adding a
                // second one.
                upgradeUuid = "9E2B62D1-5C3A-4A8F-9C41-3B7E2B0C11D7"
                perUserInstall = true
            }
        }
    }
}

/* The app image needs no separate native player files. The desktop build runs one engine,
 * KitePlayer, and its decoder ships inside the KiteCodec jar, so there is no appResourcesRootDir
 * to set. */
