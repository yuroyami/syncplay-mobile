enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    repositories {
        mavenLocal {
            content { includeGroupByRegex("io\\.github\\.yuroyami(\\..*)?") }
        }
        mavenCentral()
        gradlePluginPortal()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal {
            content { includeGroupByRegex("io\\.github\\.yuroyami(\\..*)?") }
        }
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io") // NewPipe Extractor (Android-only YouTube/SoundCloud/PeerTube resolver)
    }
}

// Reproducible builds (issue #105 / IzzyOnDroid): do NOT add foojay-resolver and do NOT pin a
// JVM toolchain vendor — RB builders are network-restricted and provision their own JDK 21.
// The JDK is requested vendor-neutrally via gradle.properties + gradle-daemon-jvm.properties.

rootProject.name = "SyncplayMobile"
include(":androidApp")
include(":shared")
include(":desktopApp")
