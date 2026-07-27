import NativeBuildConfig.registerExoOnlyLibcxxPrune
import NativeBuildConfig.registerMpvLibcxxGuards
import NativeBuildConfig.registerNativeBuildTask
import NativeBuildConfig.validateNdk

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
}

// Overridable from the CLI / gradle.properties (-PexoOnly=true); defaults to AppConfig.exoOnly.
val exoOnly = AppConfig.resolveExoOnly(providers)

val ndkRequired = providers.gradleProperty("android.ndkVersion").get()

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "androidApp"
    compileSdk = providers.gradleProperty("android.compileSdk").get().toInt()
    // Pinned for reproducible builds (issue #105) — AGP's default build-tools can resolve
    // differently on a clean CI checkout.
    buildToolsVersion = providers.gradleProperty("android.buildToolsVersion").get()
    ndkVersion = ndkRequired

    signingConfigs {
        file("${rootDir}/keystore/syncplaykey.jks").takeIf { it.exists() }?.let { keystoreFile ->
            create("synkplay_keystore") {
                storeFile = keystoreFile
                AppConfig.localProperties(rootDir).apply {
                    keyAlias = getProperty("keystore.keyAlias")
                    keyPassword = getProperty("keystore.keyPassword")
                    storePassword = getProperty("keystore.storePassword")
                }
            }
        }
    }

    defaultConfig {
        // applicationId / versionCode / versionName / manifestPlaceholders[appName] are applied
        // by KiteSSOT in AGP finalizeDsl (AFTER this block) from the root kiteSsot { } config;
        // the exoOnly applicationId swap lives THERE, a module-level override here cannot win.
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("android.targetSdk").get().toInt()

        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        signingConfigs.findByName("synkplay_keystore")?.let { config ->
            signingConfig = config
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
        debug {
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        // Pick our local libc++_shared only, not the VLC AAR's older one (crashes mpv).
        jniLibs.pickFirsts += "**/libc++_shared.so"
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/versions/9/previous-compilation-data.bin"
            pickFirsts += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/license/**"
            excludes += "META-INF/native-image/**"
            // Dead SPI hooks from transitive deps (Rhino's JSR-223 entry, BlockHound's JVM
            // agent hook) — neither can fire on Android; dropping them quiets R8 warnings.
            excludes += "META-INF/services/javax.script.ScriptEngineFactory"
            excludes += "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration"
        }
    }

    if (exoOnly) {
        packaging {
            jniLibs {
                for (mpvLib in AppConfig.mpvLibs) {
                    excludes += ("**/$mpvLib")
                }
                excludes += ("**/libvlc.so")
            }
        }
    } else {
        /* ABI splits and AAB packaging can't share one task graph (AGP issuetracker 402800800):
         * keep splits for APK builds (per-ABI downloads save ~70 MB each), drop them when any
         * "bundle" task is invoked. Never mix assemble+bundle in one invocation.
         * Full rationale: CLAUDE.md "Release artifacts". */
        val isBuildingBundle = gradle.startParameter.taskNames.any {
            it.contains("bundle", ignoreCase = true)
        }
        if (!isBuildingBundle) {
            splits {
                abi {
                    isEnable = true
                    reset()
                    for (abi in AppConfig.abiCodes) {
                        include(abi.key)
                    }
                    isUniversalApk = true
                }
            }
        }
    }

    flavorDimensions.add("flavor")
    productFlavors {
        create(if (exoOnly) "exoOnly" else "full") {
            dimension = "flavor"
        }
    }

    dependenciesInfo {
        // No dependency metadata in APKs/AABs.
        includeInApk = false
        includeInBundle = false
    }

    // Strip unused artifacts that the google-shortcuts library drags along.
    configurations.all {
        exclude(group = "com.google.crypto.tink", module = "tink-android")
        exclude(group = "com.google.android.gms")
    }
}

if (!exoOnly) {
    afterEvaluate {
        validateNdk(androidComponents.sdkComponents.ndkDirectory.get().asFile, ndkRequired)
    }
    registerNativeBuildTask(
        sdkPathProvider = { androidComponents.sdkComponents.sdkDirectory.get().asFile },
        ndkPathProvider = { androidComponents.sdkComponents.ndkDirectory.get().asFile }
    )
    // libc++ restore/verify guards — see NativeBuildConfig + CLAUDE.md "Android-only native gotchas".
    registerMpvLibcxxGuards(androidComponents.sdkComponents.ndkDirectory)
} else {
    // Reproducible-builds prune (issue #105) — see NativeBuildConfig.
    registerExoOnlyLibcxxPrune()
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val abiFilter = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
                val v = AppConfig.VERSION_NAME
                val fileName = if (exoOnly) {
                    "syncplay-$v-exo-only.apk"
                } else {
                    val abiName = abiFilter ?: "universal"
                    "${AppConfig.APP_NAME.lowercase()}-$v-full-${abiName}.apk"
                }
                output.outputFileName = fileName
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)
    implementation(projects.shared)
}
