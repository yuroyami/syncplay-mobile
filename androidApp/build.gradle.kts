import NativeBuildConfig.registerNativeBuildTask
import NativeBuildConfig.validateNdk
import io.github.yuroyami.kmpssot.kmpSsot

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
    // Pinned for reproducible builds — see gradle.properties. Without it AGP uses its default
    // build-tools, which a clean/CI checkout (e.g. IzzyOnDroid) can resolve to a different version.
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
        // applicationId / versionCode / versionName / manifestPlaceholders[appName] are
        // set eagerly by kmp-ssot plugin. The exoOnly override below runs in this block
        // (which evaluates AFTER plugins.withId fires), so it wins for the exoOnly case.
        if (exoOnly) applicationId = "com.reddnek.syncplay"
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("android.targetSdk").get().toInt()

        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        signingConfigs.findByName("synkplay_keystore")?.let { config ->
            signingConfig = config
        }
    }

    compileOptions {
        // sourceCompatibility / targetCompatibility set by kmp-ssot from javaVersion (default 21).
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
        debug {
            //applicationIdSuffix = ".dev"
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/versions/9/previous-compilation-data.bin"
            pickFirsts += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/license/**"
            excludes += "META-INF/native-image/**"

            // R8 warns when META-INF/services/* points at classes that don't exist on
            // the runtime classpath. The two below come from transitive deps whose SPI
            // hooks have no effect on Android, so we drop the service files entirely
            // rather than carry dead pointers in the APK and quiet R8 with -dontwarn.
            //
            //   javax.script.ScriptEngineFactory
            //     Rhino (pulled in by NewPipeExtractor) registers itself as a JSR-223
            //     scripting engine via this service file. Android has no javax.script
            //     package, so the SPI lookup never fires anyway. NewPipe uses Rhino
            //     directly via org.mozilla.javascript.* APIs, not via JSR-223, so
            //     dropping the SPI file doesn't break anything we actually use.
            //
            //   reactor.blockhound.integration.BlockHoundIntegration
            //     BlockHound is a JVM agent (uses ByteBuddy) that detects blocking
            //     calls inside Reactor's non-blocking event loops. It's a Netty
            //     transitive (via reactor-netty if dragged in by some other chain).
            //     Doesn't load on Android — no JVM agent support — so the SPI hook
            //     is purely dead weight.
            excludes += "META-INF/services/javax.script.ScriptEngineFactory"
            excludes += "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration"
        }
    }

    packaging {
        jniLibs {
            pickFirsts += "**/libc++_shared.so" //Pick our local c++_shared only and not the one in VLC aar
        }
    }

    if (exoOnly) {
        packaging {
            jniLibs {
                //mpv libs
                for (mpvLib in AppConfig.mpvLibs) {
                    excludes += ("**/$mpvLib")
                }

                //vlc
                excludes += ("**/libvlc.so")
            }
        }
    } else {
        /* ABI splits and AAB packaging are mutually exclusive: when `splits.abi` is
         * enabled the resource shrinker writes one `shrunk-resources-proto-format-*-release.ap_`
         * per ABI (plus a universal one), but `bundleFullRelease` expects exactly one
         * shrunk resources file in that intermediate directory and fails with
         * "Multiple shrunk-resources files found in directory ..." (AGP issuetracker
         * 402800800). The fix is to keep splits for APK builds (where per-ABI artifacts
         * shave ~70 MB off each download by stripping the libVLC + libmpv .so files for
         * unused architectures) and turn them off when a bundle task is in the build
         * graph — Play handles per-ABI delivery server-side from the AAB anyway, so we
         * lose nothing in that flow.
         *
         * Detection happens from `gradle.startParameter.taskNames`. The check uses the
         * "bundle" substring (case-insensitive) so it matches `bundleFullRelease`,
         * `bundleRelease`, plain `bundle`, and any project-prefixed variant. APK-only
         * invocations (`assembleFullRelease`, `installFullRelease`, etc.) keep splits.
         * Mixing both in one invocation (`./gradlew assembleFullRelease bundleFullRelease`)
         * would still hit the AGP error — don't do that; run them separately. */
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
        // Disables dependency metadata when building APKs & Android App Bundles.
        includeInApk = false
        includeInBundle = false
    }

    // This block strips out odd, unused artifacts that the google-shortcuts library brings along,
    // none of which are needed for its main features.
    // This will remove them also from any other library that might use them
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
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val abiFilter = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
                val v = kmpSsot.versionName.get()
                val name = kmpSsot.appName.get()
                val fileName = if (exoOnly) {
                    "syncplay-$v-exo-only.apk"
                } else {
                    val abiName = abiFilter ?: "universal"
                    "${name.lowercase()}-$v-full-${abiName}.apk"
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

if (!exoOnly) {
    tasks.named("preBuild") {
        dependsOn("runAndroidMpvNativeBuildScripts")
    }
} else {
    // Reproducible-build fix: the full/mpv native build leaves a libc++_shared.so byproduct in
    // src/main/libs/<abi>/ (gitignored, so absent from clean checkouts). Packaging that local copy
    // makes the exo-only APK non-reproducible — a fresh checkout (e.g. IzzyOnDroid's builder) has no
    // such file and instead packages the VLC AAR's libc++, a different binary. Prune the stray copy
    // before the build so the exo-only flavor deterministically uses the AAR's libc++, identical to
    // any clean checkout. (The full flavor keeps its locally-built libc++ via the pickFirst above.)
    val pruneStaleExoOnlyLibcxx = tasks.register<Delete>("pruneStaleExoOnlyLibcxx") {
        delete(fileTree("src/main/libs") { include("**/libc++_shared.so") })
    }
    tasks.named("preBuild") {
        dependsOn(pruneStaleExoOnlyLibcxx)
    }
}

