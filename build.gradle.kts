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
 * The coverage floor for app.protocol and app.server. Raise it when a pass adds tests; never
 * lower it to make a build pass.
 */
val COVERAGE_FLOOR = 30

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


/**
 * The tolerant-deserialization rule, enforced.
 *
 * Anything malformed arriving from the wire must throw SerializationException, because that is
 * the only type the skip-a-poisoned-line catch covers. `error()` and `check()` throw
 * IllegalStateException, which kills the whole inbound pipeline and drops the connection over
 * one bad line.
 *
 * This is a scan rather than a detekt rule on purpose: detekt's ForbiddenMethodCall needs type
 * resolution the plain task does not have, so it never fired.
 */
val protocolThrowSources = listOf(
    "shared/src/commonMain/kotlin/app/protocol",
    "shared/src/commonMain/kotlin/app/server/ClientConnection.kt",
)

val checkProtocolThrows by tasks.registering {
    group = "verification"
    description = "Fails if inbound protocol code throws anything but SerializationException."
    val sources = protocolThrowSources.map { file(it) }
    val root = rootDir
    inputs.files(sources)
    outputs.upToDateWhen { false }
    doLast {
        val banned = Regex("""(^|[^\w."])(error|check|checkNotNull|require|requireNotNull)\s*\(""")
        val offenders = sources
            .flatMap { f -> if (f.isDirectory) f.walkTopDown().toList() else listOf(f) }
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { f ->
                f.readLines().withIndex()
                    .filter { (_, line) ->
                        val code = line.substringBefore("//").trim()
                        // A declaration named error() is our own builder, not kotlin.error.
                        !code.startsWith("*") &&
                            !code.contains("fun error(") &&
                            banned.containsMatchIn(code)
                    }
                    .map { (i, line) -> f.relativeTo(root).path + ":" + (i + 1) + ": " + line.trim() }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Inbound protocol code must throw SerializationException, nothing else.")
                    appendLine("These throw IllegalStateException or IllegalArgumentException instead,")
                    appendLine("which the skip-a-poisoned-line catch does not cover:")
                    offenders.forEach { appendLine("  " + it) }
                }
            )
        }
    }
}

// The root project has no `check`; hang it off the module that owns the code instead.
gradle.projectsEvaluated {
    project(":shared").tasks.findByName("check")?.dependsOn(checkProtocolThrows)
}
