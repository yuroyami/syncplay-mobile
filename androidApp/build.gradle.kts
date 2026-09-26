import com.android.build.api.artifact.SingleArtifact
import io.github.yuroyami.kiteconfig.kiteConfig

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
}

// The exoOnly Gradle property (-PexoOnly=true or gradle.properties) overrides AppConfig.exoOnly.
val exoOnly = AppConfig.resolveExoOnly(providers)

/* -PunsignedRelease=true builds a release without a keystore, on purpose. It lets anyone check
 * that a published APK was built from this source. The APK name says "unsigned", so nobody
 * mistakes it for a signed release. docs/DEVELOPING.md documents the command. */
val unsignedRelease = providers.gradleProperty("unsignedRelease").orNull.toBoolean()

android {
    namespace = "androidApp"
    // Pinned for reproducible builds (issue #105): AGP's default build-tools can resolve
    // differently on a clean CI checkout.
    buildToolsVersion = providers.gradleProperty("android.buildToolsVersion").get()

    // :shared holds almost all the code. With checkDependencies, one lint run covers this module
    // and :shared, so the repo's lint.xml suppression reaches the :shared code it was written for.
    lint {
        checkDependencies = true
        xmlReport = true
        htmlReport = true
        abortOnError = false
    }

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
        // KiteConfig sets applicationId, versionCode, versionName and manifestPlaceholders[appName]
        // from the root kiteConfig { } block. It runs in AGP finalizeDsl, after this block, so a
        // value set here cannot win. For that reason the exoOnly applicationId swap is there too.

        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        // An unsigned release stays unsigned even on a machine that holds the keystore, so the
        // file name always matches the file. The debug build type sets its own signing below.
        if (!unsignedRelease) {
            signingConfigs.findByName("synkplay_keystore")?.let { config ->
                signingConfig = config
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            /* An unsigned APK with a release file name looks like a real one until a device
             * refuses to install it. So a missing keystore stops a release build, unless
             * -PunsignedRelease=true asks for an unsigned one. */
            if (signingConfigs.findByName("synkplay_keystore") == null && !unsignedRelease) {
                gradle.taskGraph.whenReady {
                    val releasing = allTasks.any { it.path.contains("Release") && (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package")) }
                    check(!releasing) {
                        "No signing keystore: put keystore/syncplaykey.jks and its local.properties entries in place, or build a debug variant."
                    }
                }
            }
            /* Put the full native symbol table in the bundle, next to the R8 mapping. Without
             * both, a crash report from the store shows only obfuscated frames and addresses. */
            ndk { debugSymbolLevel = "FULL" }
        }
        debug {
            // Signed with the release key when the keystore exists, so a debug build installs
            // over a release build and keeps the app data.
            signingConfigs.findByName("synkplay_keystore")?.let { config ->
                signingConfig = config
            }
        }
        /* The baseline profile plugin builds these two for the :baselineprofile module, which
         * installs them, runs, and uninstalls them. Their own application id keeps the installed
         * app and its data out of that. The plugin sets the rest of each build type. */
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
        }
        create("benchmarkRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        // No pickFirst for libc++_shared.so: only the libmpvkt AAR ships one. If a second
        // dependency brings its own copy, AGP fails the merge. Then check which copy is newer
        // instead of adding a pickFirst.
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/versions/9/previous-compilation-data.bin"
            pickFirsts += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/license/**"
            excludes += "META-INF/native-image/**"
            // Service loader files from transitive dependencies (Rhino's JSR-223 entry,
            // BlockHound's JVM agent hook). Neither can run on Android, and dropping them
            // removes R8 warnings.
            excludes += "META-INF/services/javax.script.ScriptEngineFactory"
            excludes += "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration"
        }
    }

    if (exoOnly) {
        packaging {
            jniLibs {
                // The exoOnly APK carries no native player library. The libmpvkt dependency
                // stays so that the engine code compiles, and this block drops its libraries.
                // The APK gate (buildSrc/ExoOnlyApkGate.kt) reads the finished APK and fails the
                // build if one of them is still inside.
                for (lib in AppConfig.libmpvNativeLibs) {
                    excludes += ("**/$lib")
                }
                // KitePlayer's FFmpeg backend is the largest native library here, because it
                // links all of FFmpeg statically. It costs more size than mpv, so the exoOnly
                // APK drops it too. KitePlayerPlatform.isAvailable detects that it is missing.
                excludes += ("**/libkitecodec_jni.so")
                // KitePlayer's subtitle renderer has no user once its decoder is gone.
                excludes += ("**/libkiteplayer_libass_jni.so")
            }
        }
    }
    /* No ABI splits: one universal APK per flavor carries every ABI. Per-ABI APKs save download
     * size, but they put a five-way choice on the release page. They also force the APK and AAB
     * builds into separate Gradle runs (AGP issuetracker 402800800). Play still gets the AAB and
     * serves each phone only its own libraries. */

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

    // Keep Tink and Google Play services out of the app, even if a dependency pulls them in.
    configurations.all {
        exclude(group = "com.google.crypto.tink", module = "tink-android")
        exclude(group = "com.google.android.gms")
    }
}

/* The baseline profile lives in src/main/generated/baselineProfiles, where the generator writes
 * it. A build never generates it: a profile made again on each machine would differ, and an
 * outside builder could no longer match the published APK. docs/DEVELOPING.md has the steps. */
baselineProfile {
    mergeIntoMain = true
    automaticGenerationDuringBuild = false
}

androidComponents {
    // Each APK gets its native library check as part of its assemble task: no player library in
    // exoOnly (buildSrc/ExoOnlyApkGate.kt), and KitePlayer's decoder for every ABI in full
    // (buildSrc/KiteDecoderApkGate.kt).
    onVariants { variant ->
        val apks = variant.artifacts.get(SingleArtifact.APK)
        val gate = if (exoOnly) registerExoOnlyApkGate(variant.name, apks) else registerKiteDecoderApkGate(variant.name, apks)
        val assemble = "assemble" + variant.name.replaceFirstChar { it.uppercase() }
        tasks.matching { it.name == assemble }.configureEach { dependsOn(gate) }
    }

    // KiteConfig sets the major compile SDK. AGP needs the minor level set again after that.
    finalizeDsl { it.compileSdkMinor = providers.gradleProperty("android.compileSdkMinor").get().toInt() }
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val v = kiteConfig.version.get()
                // "universal" in the full APK name tells a downloader that every ABI is inside.
                // The exoOnly APK name keeps "syncplay" on purpose, whatever the app name is.
                // IzzyOnDroid's updater fetches the release asset by that name, so a rename
                // breaks its updates.
                // An unsigned release says so in its name. See `unsignedRelease` at the top.
                val unsigned = if (unsignedRelease && variant.buildType == "release") "-unsigned" else ""
                val fileName = if (exoOnly) {
                    "syncplay-$v-exo-only$unsigned.apk"
                } else {
                    "${kiteConfig.appName.get().lowercase()}-$v-full-universal$unsigned.apk"
                }
                output.outputFileName = fileName
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)
    implementation(projects.shared)
    baselineProfile(projects.baselineprofile)
}
