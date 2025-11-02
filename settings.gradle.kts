//enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "SyncplayMobile"
include(":shared")

//val media3Root = File(rootDir, "buildscripts/media3")
//includeBuild(media3Root)

(gradle as ExtensionAware).extra["androidxMediaModulePrefix"] = "media3-"
apply(from = File(rootDir, "buildscripts/media3/core_settings.gradle"))

