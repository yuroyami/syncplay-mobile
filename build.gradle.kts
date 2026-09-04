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
    // Ktorfit generates its API implementations through KSP; without this there is no createKlipyAPI.
    alias(libs.plugins.ksp).apply(false)

    alias(libs.plugins.ktorfit).apply(false)

    // Static analysis and coverage. Both are configured for this repo, not generically:
    // see config/detekt/detekt.yml and the kover block below.
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// KiteConfig applies app identity in AGP finalizeDsl (AFTER module DSL blocks), so the
// exoOnly applicationId swap must happen here, not inside androidApp's defaultConfig.
/**
 * The coverage floor for app.protocol and app.server, set just under what a clean full run
 * measures (24.9 percent on 2026-09-04). It is a ratchet: raise it when a pass adds tests, never
 * lower it to make a build pass.
 *
 * The number moves both ways for an honest reason. Extracting the sync decision and the position
 * report out of the managers put a hundred previously unreachable lines inside the measured
 * packages, most of which are now covered, but the managers around them still are not.
 */
val COVERAGE_FLOOR = 24

val exoOnly = AppConfig.resolveExoOnly(providers)
val localProperties = AppConfig.localProperties(rootDir)

kiteConfig {
    appName = "Synkplay"
    version = "0.24.0"
    jvmTarget = 21

    id(if (exoOnly) "com.reddnek.syncplay" else "com.yuroyami.syncplay") {
        ios { suffix = ".iosApp" }
        desktop { suffix = ".desktop" }
    }

    modules {
        shared = ":shared"
        androidApps(":androidApp")
        // The desktop shell reads its identity back from this block, so it has to be in it.
        // Its comments already said so; the registration was missing.
        desktopApps(":desktopApp")
    }

    // SDK/toolchain values come from gradle.properties (single value source; the modules
    // read the same keys). compileSdk >= 33 also makes the logo sync emit the themed-icon
    // monochrome wrappers (issue #143).
    android {
        compileSdk = providers.gradleProperty("android.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("android.targetSdk").get().toInt()
        ndk = providers.gradleProperty("android.ndkVersion").get()
    }

    ios {
        // Compatibility assertion for the universal AppIcon installer only (matches the
        // cocoapods deploymentTarget in :shared); it does not configure Xcode.
        deploymentTarget = "14.1"

        // Mutation stays on-demand in KiteConfig: this block only ARMS kiteRewriteXcode;
        // normal builds never rewrite sources. Run it after every version bump.
        rewrite { }
    }

    // Same rule as ios { rewrite }: this arms kiteRewriteLogo, it does not run it.
    logo {
        foreground = layout.projectDirectory.file("shared/src/commonMain/composeResources/drawable/synkplay_fg.png")
        background = layout.projectDirectory.file("shared/src/commonMain/composeResources/drawable/synkplay_bg.png")
        android { safeZone = 0.5 }
        rewrite { replaceOld = true }
    }

    buildConfig {
        includeIdentity = false
        // className is left at its default, KiteBuildConfig, since 1.0.0: no clash
        // with the BuildConfig that AGP generates.
        packageName = "SyncplayMobile.shared"
//
        stringField("APP_NAME", kiteConfig.appName.get())
        stringField("APP_VERSION", kiteConfig.version.get())
        // NOT "DEBUG": KiteConfig generates a PUBLIC object, so every field becomes a property on the
        // exported ObjC header, and Xcode defines DEBUG=1 in Debug configs, so "BOOL DEBUG"
        // preprocesses to "BOOL 1" and every iOS Debug build fails to precompile the module.
        //
        // The value is detected per invocation, never hardcoded (2026-08-26: a hardcoded `true`
        // shipped debug-only engine entries in Release binaries). iOS: the pod script phase passes
        // -Pkotlin.native.cocoapods.configuration=Debug|Release, the authoritative signal there.
        // Android/desktop: the requested task names carry the variant. Anything ambiguous (mixed
        // variants, sync, no variant in the name) is conservatively NOT debug.
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
        // Overridable for wire-level debugging: ./gradlew ... -PdebugProtocol=true
        booleanField(
            "DEBUG_SYNCPLAY_PROTOCOL",
            providers.gradleProperty("debugProtocol").orNull?.toBoolean() ?: false,
        )
        booleanField("EXOPLAYER_ONLY", exoOnly)
        stringField("KLIPY_API_KEY", localProperties.getProperty("yuroyami.keyKlipyApi") ?: "")
        // A local OpenSubtitles client key can replace the legacy fallback.
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

/**
 * Static analysis, aimed at this codebase. The rule set in `config/detekt/detekt.yml` is almost
 * entirely off; what is on maps to defects this repo actually had.
 *
 * The inbound-throws rule lives in [checkProtocolThrows] rather than here, because detekt's
 * ForbiddenMethodCall needs type resolution that the plain detekt task does not have, so it
 * would have passed silently forever.
 */
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(
        files(
            "shared/src/commonMain/kotlin",
            "shared/src/androidMain/kotlin",
            "shared/src/desktopMain/kotlin",
            "shared/src/iosMain/kotlin",
            "shared/src/commonTest/kotlin",
            "shared/src/desktopTest/kotlin",
            "androidApp/src/main/java",
            "desktopApp/src/main/kotlin",
        )
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        sarif.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
}

/**
 * Coverage, scoped to the two packages where a gap is a real risk: the protocol and the hosted
 * server. Everything else (UI, platform actuals, build glue) is excluded, because measuring it
 * would only produce a number nobody can act on.
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
                // Generated, or a platform actual that cannot run on the JVM.
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
registerQualityGates()

/**
 * Release identity, printed once, for anything outside Gradle that needs it.
 *
 * The release workflow used to grep the version out of this file with sed and recompute the
 * version code in an inline Python one-liner, which meant KiteConfig's scheme was written down
 * twice and the second copy could drift without anyone noticing. It asks for these now.
 */
tasks.register("printReleaseIdentity") {
    group = "help"
    description = "Prints version, versionCode, applicationId and the iOS deployment target as KEY=VALUE."
    val version = kiteConfig.version.get()
    val appId = if (exoOnly) "com.reddnek.syncplay" else "com.yuroyami.syncplay"
    // Read from the pbxproj, which is what Xcode and the App Store actually see; KiteConfig's
    // ios block only asserts against it.
    val iosMinimum = file("iosApp/iosApp.xcodeproj/project.pbxproj").readLines()
        .firstNotNullOfOrNull { line ->
            Regex("""IPHONEOS_DEPLOYMENT_TARGET = ([0-9.]+);""").find(line)?.groupValues?.get(1)
        } ?: "14.1"
    // KiteConfig's scheme: 1 | major(3) | minor(3) | patch(2) | rebuild(1).
    val parts = version.split(".")
    val versionCode = "1" + parts[0].padStart(3, '0') + parts[1].padStart(3, '0') +
        parts[2].padStart(2, '0') + "0"
    doLast {
        println("VERSION=$version")
        println("VERSION_CODE=$versionCode")
        println("APPLICATION_ID=$appId")
        println("IOS_MIN_VERSION=$iosMinimum")
    }
}
