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
}

// KiteConfig applies app identity in AGP finalizeDsl (AFTER module DSL blocks), so the
// exoOnly applicationId swap must happen here, not inside androidApp's defaultConfig.
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
