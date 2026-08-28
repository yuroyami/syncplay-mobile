plugins {
    alias(libs.plugins.kitessot)

    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.kotlin.cocoapods).apply(false)

    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.plugin).apply(false)

    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)

    alias(libs.plugins.kSerialization).apply(false)
    alias(libs.plugins.ksp).apply(false)

    alias(libs.plugins.ktorfit).apply(false)
}

// KiteSSOT applies app identity in AGP finalizeDsl (AFTER module DSL blocks), so the
// exoOnly applicationId swap must happen here, not inside androidApp's defaultConfig.
val exoOnly = AppConfig.resolveExoOnly(providers)
val localProperties = AppConfig.localProperties(rootDir)

kiteSsot {
    appName = AppConfig.APP_NAME
    version = AppConfig.VERSION_NAME
    appId = if (exoOnly) AppConfig.BUNDLE_ID_BASE_EXO else AppConfig.BUNDLE_ID_BASE

    jvmTarget = 21

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
        bundleIdSuffix = ".iosApp"

        // Compatibility assertion for the universal AppIcon installer only (matches the
        // cocoapods deploymentTarget in :shared); it does not configure Xcode.
        deploymentTarget = "14.0"

        // Mutation stays on-demand in KiteSSOT: configuring this block only ENABLES the
        // explicit kiteSsotSyncIosConfig / kiteSsotSyncIosLogo tasks; normal builds never
        // rewrite sources. Run the iOS config sync after every version bump.
        sync { }
    }

    // Same rule as ios { sync }: this block authorizes kiteSsotSyncAndroidLogo and
    // kiteSsotSyncIosLogo, it does not run them.
    logo {
        foreground = layout.projectDirectory.file("shared/src/commonMain/composeResources/drawable/logo_fg.png")
        background = layout.projectDirectory.file("shared/src/commonMain/composeResources/drawable/logo_bg.png")
        androidSafeZone = 0.5
        takeOverLegacyIcons = true
    }

    buildConfig {
        includeIdentity = false
        packageName = "SyncplayMobile.shared"
        className = "BuildConfig"

        stringField("APP_NAME", AppConfig.APP_NAME)
        stringField("APP_VERSION", AppConfig.VERSION_NAME)
        // NOT "DEBUG": KiteSSOT generates a PUBLIC object, so every field becomes a property on the
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

registerAndroidReleaseAllTask()
