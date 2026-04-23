plugins {
    id("com.yuroyami.kmpssot") version "1.0.3"

    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.kotlin.cocoapods).apply(false)

    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.plugin).apply(false)

    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)

    alias(libs.plugins.kSerialization).apply(false)
    alias(libs.plugins.ksp).apply(false)

    alias (libs.plugins.ktorfit).apply(false)

    alias(libs.plugins.buildConfig).apply(false)
}

kmpSsot {
    appName         = "Synkplay"
    versionName     = "0.21.2"
    bundleIdBase    = "com.yuroyami.syncplay"
    iosBundleSuffix = ".iosApp"

    sharedModule     = "shared"
    androidAppModule = "androidApp"

    // exoOnly flavor switches the Android base ID to "com.reddnek.syncplay" — let the
    // user-side flavor branch override the plugin's default (since user's defaultConfig
    // block runs after our plugins.withId callback, the override wins). Plugin still
    // propagates iOS PRODUCT_BUNDLE_IDENTIFIER as com.yuroyami.syncplay.iosApp.

    // Custom Trinity color, logo→imageset, and default-strings fallback propagation
    // stay in buildSrc/AppConfig.kt — outside plugin scope.
}