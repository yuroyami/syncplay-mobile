import io.github.yuroyami.kiteconfig.kiteConfig
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

val ndkRequired = kiteConfig.ndk.get()

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "androidApp"
    compileSdk = kiteConfig.compileSdk.get()
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
        // by KiteConfig in AGP finalizeDsl (AFTER this block) from the root kiteConfig { } config;
        // the exoOnly applicationId swap lives THERE, a module-level override here cannot win.
        minSdk = kiteConfig.minSdk.get()
        targetSdk = kiteConfig.targetSdk.get()

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
            /* An unsigned APK under a release filename is indistinguishable from a real one until
             * a device refuses to install it. A missing keystore stops the build instead. */
            if (signingConfigs.findByName("synkplay_keystore") == null) {
                gradle.taskGraph.whenReady {
                    val releasing = allTasks.any { it.path.contains("Release") && (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package")) }
                    check(!releasing) {
                        "No signing keystore: put keystore/syncplaykey.jks and its local.properties entries in place, or build a debug variant."
                    }
                }
            }
            /* R8's mapping and the native symbol table, or a crash report from the store is a
             * page of obfuscated frames and addresses. */
            ndk { debugSymbolLevel = "FULL" }
        }
        debug {
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        // Always package our own NDK-matched libc++_shared (src/main/libs), never a copy that
        // arrives inside some dependency's AAR — a mismatched one crashes mpv at load.
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
                // KitePlayer's FFmpeg backend, the single largest native library here: it carries
                // a whole statically linked FFmpeg, so leaving it in would cost this flavor more
                // than mpv does and defeat the point of shipping no native players.
                // KitePlayerPlatform.isAvailable detects that this payload is absent.
                excludes += ("**/libkitecodec_jni.so")
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
                val v = kiteConfig.version.get()
                val fileName = if (exoOnly) {
                    "${kiteConfig.appName.get().lowercase()}-$v-exo-only.apk"
                } else {
                    val abiName = abiFilter ?: "universal"
                    "${kiteConfig.appName.get().lowercase()}-$v-full-${abiName}.apk"
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
