plugins {
    id("io.github.yuroyami.kitessot") version "2.0.2"

    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.kotlin.cocoapods).apply(false)

    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.plugin).apply(false)

    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)

    alias(libs.plugins.kSerialization).apply(false)
    alias(libs.plugins.ksp).apply(false)

    alias(libs.plugins.ktorfit).apply(false)

    alias(libs.plugins.buildConfig).apply(false)
}

// KiteSSOT applies app identity in AGP finalizeDsl (AFTER module DSL blocks), so the
// exoOnly applicationId swap must happen here, not inside androidApp's defaultConfig.
val exoOnly = AppConfig.resolveExoOnly(providers)

kiteSsot {
    appName = AppConfig.APP_NAME
    versionName = AppConfig.VERSION_NAME
    bundleIdBase = if (exoOnly) AppConfig.BUNDLE_ID_BASE_EXO else AppConfig.BUNDLE_ID_BASE
    iosBundleSuffix = ".iosApp"

    sharedProjectPath = ":shared"
    androidApplicationProjects.add(":androidApp")

    javaVersion = 21

    // SDK/toolchain values come from gradle.properties (single value source; the modules
    // read the same keys). compileSdk >= 33 also makes the logo sync emit the themed-icon
    // monochrome wrappers (issue #143).
    android {
        compileSdk = providers.gradleProperty("android.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("android.targetSdk").get().toInt()
        ndkVersion = providers.gradleProperty("android.ndkVersion").get()
    }

    // Mutation stays on-demand in KiteSSOT: these flags only ENABLE the explicit sync
    // tasks (kiteSsotSyncAndroidLogo / kiteSsotSyncIosConfig); normal builds never
    // rewrite sources. Run the iOS config sync after every version bump.
    syncIos = true
    ios {
        // Compatibility assertion for the universal AppIcon installer only (matches the
        // cocoapods deploymentTarget in :shared); it does not configure Xcode.
        deploymentTarget = "14.0"
    }
    propagateLogo = true
    cleanupLegacyLogoArtifacts = true
    appLogoPngForeground = layout.projectDirectory.file("shared/src/commonMain/composeResources/drawable/logo_fg.png")
    appLogoPngBackground = layout.projectDirectory.file("shared/src/commonMain/composeResources/drawable/logo_bg.png")
    appLogoAndroidSafeZoneRatio = 0.5
}

registerAndroidReleaseAllTask()
