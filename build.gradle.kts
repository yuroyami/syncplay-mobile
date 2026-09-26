import io.github.yuroyami.kiteconfig.kiteConfig
plugins {
    alias(libs.plugins.kiteconfig)

    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.kotlin.cocoapods).apply(false)

    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.plugin).apply(false)

    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)

    alias(libs.plugins.kSerialization).apply(false)
    // Ktorfit generates its API implementations with KSP. Without KSP, there is no createKlipyAPI.
    alias(libs.plugins.ksp).apply(false)

    alias(libs.plugins.ktorfit).apply(false)

    // Static analysis (detekt) and coverage (kover), both set up for this repo:
    // see config/detekt/detekt.yml and the kover block below.
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

/**
 * The coverage floor for app.protocol and app.server, set just under what a clean full run
 * measures (24.9 percent). Raise it when new tests add coverage. Never lower it to make a build
 * pass.
 *
 * The measured value can fall as well as rise, for example when untested code moves into these
 * packages. The sync decision and the position report are mostly covered by tests; the managers
 * around them are not.
 */
val COVERAGE_FLOOR = 24

val exoOnly = AppConfig.resolveExoOnly(providers)
val localProperties = AppConfig.localProperties(rootDir)

/* A release must resolve only published artifacts, so that anyone can rebuild it and compare the
 * bytes. `-PuseMavenLocal` serves this project's own libraries from the local machine, which an
 * outsider cannot see, so a release build with that flag fails here. Debug builds may use the
 * flag. */
if (providers.gradleProperty("useMavenLocal").orNull.toBoolean()) {
    gradle.taskGraph.whenReady {
        val releasing = allTasks.any { task ->
            task.path.contains("Release") &&
                (task.name.startsWith("assemble") || task.name.startsWith("bundle") || task.name.startsWith("package"))
        }
        check(!releasing) {
            "-PuseMavenLocal builds a release against libraries from this machine, and nobody else " +
                "can reproduce it. Drop the flag for a release, or build a debug variant."
        }
    }
}

kiteConfig {
    appName = "Synkplay"
    appId = "com.yuroyami.syncplay"
    version = "0.25.0"
    // A Gradle sync updates the Xcode project before Xcode opens it. A build applies only the
    // changes for its own platform.
    autoApply = true

    // Both shared and webApp use Kotlin Multiplatform. The generated app configuration goes
    // into shared.
    modules { shared = ":shared" }

    jvm {
        toolchain = providers.gradleProperty("org.gradle.toolchains.jvm.version").map(String::toInt)
        target = toolchain
    }

    android {
        // KiteConfig applies the app identity in AGP finalizeDsl, after the module DSL blocks.
        // So the applicationId swap for the exoOnly flavor (ExoPlayer only, no native player
        // library) happens here, not in androidApp's defaultConfig.
        if (exoOnly) appId = "com.reddnek.syncplay"
        version { rebuild = 1 }
        sdk(
            min = providers.gradleProperty("android.minSdk").get().toInt(),
            target = providers.gradleProperty("android.targetSdk").get().toInt(),
            compile = providers.gradleProperty("android.compileSdk").get().toInt(),
        )
        ndk = providers.gradleProperty("android.ndkVersion").get()
        logo { foregroundScale = 0.5 }
    }

    ios {
        appId { suffix = ".iosApp" }
        version { rebuild = 1 }
        infoPlist {
            proMotion = true
            // True: the app bundles its own TLS (SwiftNIO SSL), so a French declaration is due for
            // the French store. See "App Store encryption answer" in docs/DEVELOPING.md.
            nonExemptEncryption = true
        }
    }

    desktop {
        appId { suffix = ".desktop" }
    }

    logo {
        foreground = file("shared/src/commonMain/composeResources/drawable/synkplay_fg.png")
        background = image(file("shared/src/commonMain/composeResources/drawable/synkplay_bg.png"))
    }

    optIns {
        add("kotlinx.cinterop.BetaInteropApi")
    }

    buildConfig {
        includeIdentity = false
        // The generated object is KiteBuildConfig, so it does not clash with AGP's BuildConfig.
        packageName = "SyncplayMobile.shared"
        stringField("APP_NAME", kiteConfig.appName)
        stringField("APP_VERSION", kiteConfig.version)
        // The name is IS_DEBUG, not DEBUG. KiteConfig generates a public object, so every field
        // appears in the exported Objective-C header. Xcode defines DEBUG=1 in Debug
        // configurations, so "BOOL DEBUG" becomes "BOOL 1" and every iOS Debug build fails to
        // precompile the module.
        //
        // Detect the value on each invocation and never hardcode it: a hardcoded `true` ships
        // debug-only engine entries in Release binaries. On iOS, the pod script phase passes
        // -Pkotlin.native.cocoapods.configuration=Debug|Release, which is the reliable signal.
        // On Android and desktop, the requested task names carry the variant. Anything unclear
        // (mixed variants, a sync, no variant in the name) counts as not debug.
        val requestedTasks = gradle.startParameter.taskNames.map { it.lowercase() }
        val podConfiguration =
            providers.gradleProperty("kotlin.native.cocoapods.configuration").orNull?.lowercase()
        val isDebugInvocation = when {
            podConfiguration != null -> podConfiguration == "debug"
            requestedTasks.any { it.contains("release") } -> false
            requestedTasks.any { it.contains("debug") } -> true
            else -> false
        }
        booleanField("IS_DEBUG", isDebugInvocation)
        // Logs every raw protocol line when set: ./gradlew ... -PdebugProtocol=true
        booleanField(
            "DEBUG_SYNCPLAY_PROTOCOL",
            providers.gradleProperty("debugProtocol").map(String::toBoolean).orElse(false),
        )
        booleanField("EXOPLAYER_ONLY", exoOnly)
        // Public on purpose. The key ships inside every APK and travels in the URL path of
        // every request, so a device cannot keep it secret. The committed key lets a third
        // party rebuild a published APK and get the same bytes. KLIPY keys are free and
        // unmetered, so a copied key costs nothing. There is no local override, so every
        // machine builds the same APK.
        stringField("KLIPY_API_KEY", "M5BjLZtHJtX8pSM7bkwL9A1uTRIiWPceLRfb7TA7QHM9dDVmIXaQLSXk6UYgmI70")
        // A local OpenSubtitles client key (yuroyami.keyOpenSubsApi in local.properties)
        // replaces the committed fallback key.
        stringField(
            "OPENSUBTITLES_API_KEY",
            localProperties.getProperty("yuroyami.keyOpenSubsApi")
                ?: "iesFjGxVcXtBMnEbxMRYyWbU3M1UEaaL",
        )
        longField("TRINITY_COLOR_1", AppConfig.TRINITY_1)
        longField("TRINITY_COLOR_2", AppConfig.TRINITY_2)
        longField("TRINITY_COLOR_3", AppConfig.TRINITY_3)
    }
}

registerAndroidReleaseAllTask(kiteConfig.version.get())
registerDependencyTableTask()

/**
 * Static analysis for this codebase. Most rules in `config/detekt/detekt.yml` are off; the rules
 * that are on match defects that this repo has had.
 *
 * The rule against throws in inbound protocol code lives in [checkProtocolThrows], not here.
 * Detekt's ForbiddenMethodCall needs type resolution, which the plain detekt task does not have,
 * so that rule would always pass silently.
 */
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    // Every Kotlin source folder of every module, read from disk, so a new source set is scanned
    // without an edit here.
    source.setFrom(
        listOf("shared", "androidApp", "desktopApp", "webApp").flatMap { module ->
            file("$module/src").listFiles().orEmpty().sortedBy { it.name }
                .flatMap { set -> listOf(File(set, "kotlin"), File(set, "java")) }
                .filter { it.isDirectory }
        }
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = kiteConfig.jvmTarget.get().toString()
    reports {
        html.required.set(true)
        sarif.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
}

/**
 * Coverage, limited to the two packages where a gap is a real risk: the protocol and the hosted
 * server. Everything else (UI, platform actuals, build code) is left out, because a number for it
 * would not lead to any action.
 */
dependencies {
    // The code being measured lives in :shared; the root project only aggregates.
    kover(project(":shared"))
}

kover {
    reports {
        filters {
            includes {
                classes("app.protocol.*", "app.server.*")
            }
            excludes {
                // Generated classes: serializers, Compose singletons and factories.
                classes("*\$\$serializer", "*ComposableSingletons*", "*_Factory*")
            }
        }
        verify {
            rule("protocol and server line coverage") {
                bound {
                    minValue.set(COVERAGE_FLOOR)
                    coverageUnits.set(kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
                }
            }
        }
    }
}

// Build-time gates live in buildSrc/src/main/kotlin/QualityGates.kt.
registerQualityGates(kiteConfig.versionCode.get().toString(), kiteConfig.version.get())

/**
 * Prints the release identity for tools outside Gradle, such as the release workflow.
 *
 * Read the version and the version code from this task instead of parsing this file or
 * recomputing them. Then KiteConfig's version scheme exists in one place only.
 */
tasks.register("printReleaseIdentity") {
    group = "help"
    description = "Prints release versions, platform build numbers, applicationId and the iOS deployment target as KEY=VALUE."
    val version = kiteConfig.version.get()
    val versionCode = kiteConfig.versionCode.get()
    val iosVersion = kiteConfig.iosMarketingVersion.get()
    val iosBuildNumber = kiteConfig.iosBuildNumber.get()
    val appId = kiteConfig.androidApplicationId.get()
    // The Xcode project owns the deployment target, so read it from project.pbxproj instead of
    // repeating it here.
    val iosMinimum = file("iosApp/iosApp.xcodeproj/project.pbxproj").readLines()
        .firstNotNullOfOrNull { line ->
            Regex("""IPHONEOS_DEPLOYMENT_TARGET = ([0-9.]+);""").find(line)?.groupValues?.get(1)
        } ?: "15.0"
    doLast {
        println("VERSION=$version")
        println("VERSION_CODE=$versionCode")
        println("IOS_VERSION=$iosVersion")
        println("IOS_BUILD_NUMBER=$iosBuildNumber")
        println("APPLICATION_ID=$appId")
        println("IOS_MIN_VERSION=$iosMinimum")
    }
}
