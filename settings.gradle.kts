enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    repositories {
        mavenLocal() // kmp-ssot: remove once io.github.yuroyami.kmpssot is live on Gradle Plugin Portal
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
        maven("https://jitpack.io") // NewPipe Extractor (Android-only YouTube/SoundCloud/PeerTube resolver)
    }
}

// NOTE (issue #105 / IzzyOnDroid reproducible builds): do NOT re-add the
// foojay-resolver-convention plugin, and do NOT pin a JVM toolchain vendor. Foojay makes Gradle
// DOWNLOAD a toolchain, and a vendor pin (e.g. ADOPTIUM) forces a specific one. RB builders are
// network-restricted and provision their own JDK 21, so a forced download/vendor-match fails the
// whole build. The JDK version is requested vendor-neutrally via gradle.properties
// (org.gradle.toolchains.jvm.version=21) and gradle/gradle-daemon-jvm.properties (toolchainVersion=21),
// which any locally-provisioned JDK 21 satisfies.

rootProject.name = "SyncplayMobile"
include(":androidApp")
include(":shared")

