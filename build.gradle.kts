plugins {
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

    if (!AppConfig.exoOnly) {
        id("com.google.gms.google-services") version "4.4.4" apply false
        id("com.google.firebase.crashlytics") version "3.0.6" apply false
    }
}