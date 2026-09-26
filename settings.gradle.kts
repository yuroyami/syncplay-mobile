enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

/**
 * Local overrides for the io.github.yuroyami libraries (KiteConfig, KitePlayer), off by default.
 *
 * They let you test changes to those libraries without publishing them. They stay off by default
 * because a stale local build silently wins over Maven Central, and the result is behaviour that
 * nobody else can reproduce. Turn them on for one build:
 *
 *     ./gradlew <task> -PuseMavenLocal=true
 *
 * A release build with this flag fails (see the root build.gradle.kts).
 */
pluginManagement {
    // Gradle evaluates pluginManagement before the rest of this script, so read the flag here too.
    val useMavenLocal = providers.gradleProperty("useMavenLocal").orNull.toBoolean()
    repositories {
        if (useMavenLocal) {
            mavenLocal {
                content { includeGroupByRegex("io\\.github\\.yuroyami(\\..*)?") }
            }
        }
        mavenCentral()
        gradlePluginPortal()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    val useMavenLocal = providers.gradleProperty("useMavenLocal").orNull.toBoolean()
    repositories {
        if (useMavenLocal) {
            mavenLocal {
                content { includeGroupByRegex("io\\.github\\.yuroyami(\\..*)?") }
            }
        }
        google()
        mavenCentral()
        // libmpvKt's artifacts live in a static Maven repository on GitHub Pages, not on Central.
        // The filter keeps every other io.github.yuroyami artifact (KiteConfig, KitePlayer) on
        // Central.
        maven("https://yuroyami.github.io/maven") {
            content { includeModuleByRegex("io\\.github\\.yuroyami", "libmpvkt.*") }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        // NewPipe Extractor (the YouTube, SoundCloud and PeerTube resolver on Android and
        // desktop), and nothing else. Without the filter, JitPack can answer for any coordinate
        // that Central and Google miss.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.TeamNewPipe")
                includeGroupByRegex("com\\.github\\.TeamNewPipe\\..*")
            }
        }
    }
}

// Reproducible builds (issue #105, IzzyOnDroid): do not add foojay-resolver, and do not pin a
// JVM toolchain vendor. The rebuild servers have restricted network access and provide their own
// JDK 21. gradle.properties and gradle/gradle-daemon-jvm.properties request the JDK without
// naming a vendor.

rootProject.name = "SyncplayMobile"
include(":androidApp")
include(":shared")
include(":desktopApp")
include(":webApp")
include(":baselineprofile")
