enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

/**
 * Local overrides for the io.github.yuroyami libraries (KiteConfig, KitePlayer), off by default.
 *
 * They live here so those libraries can be iterated on without publishing, but as an always-on
 * repository they were a silent trap: a stale local build quietly wins over Maven Central, and
 * the difference only shows up as behaviour nobody can reproduce. Turn them on deliberately:
 *
 *     ./gradlew <task> -PuseMavenLocal=true
 */
pluginManagement {
    // pluginManagement is evaluated before the rest of this script, so the flag is read here too.
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
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        // NewPipe Extractor (Android-only YouTube/SoundCloud/PeerTube resolver), and nothing else:
        // unfiltered, jitpack can answer for any coordinate Central and Google happen to miss.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.TeamNewPipe")
                includeGroupByRegex("com\\.github\\.TeamNewPipe\\..*")
            }
        }
    }
}

// Reproducible builds (issue #105 / IzzyOnDroid): do NOT add foojay-resolver and do NOT pin a
// JVM toolchain vendor — RB builders are network-restricted and provision their own JDK 21.
// The JDK is requested vendor-neutrally via gradle.properties + gradle-daemon-jvm.properties.

rootProject.name = "SyncplayMobile"
include(":androidApp")
include(":shared")
include(":desktopApp")
