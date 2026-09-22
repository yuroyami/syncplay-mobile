import io.github.yuroyami.kiteconfig.kiteConfig

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
}

// Overridable from the CLI / gradle.properties (-PexoOnly=true); defaults to AppConfig.exoOnly.
val exoOnly = AppConfig.resolveExoOnly(providers)

/* The deliberate route to a release build with no keystore (-PunsignedRelease=true), for anyone
 * checking that a published APK was built from this source. The APK it writes says "unsigned" in
 * its name, so it can never be mistaken for a release. README documents the command. */
val unsignedRelease = providers.gradleProperty("unsignedRelease").orNull.toBoolean()

android {
    namespace = "androidApp"
    // Pinned for reproducible builds (issue #105): AGP's default build-tools can resolve
    // differently on a clean CI checkout.
    buildToolsVersion = providers.gradleProperty("android.buildToolsVersion").get()

    // :shared holds essentially all the code, and lint stopped at this module's four files. With
    // dependency checking on, one run covers both, which is also what makes the repo's lint.xml
    // suppression apply to the code it was written for.
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
        // applicationId / versionCode / versionName / manifestPlaceholders[appName] are applied
        // by KiteConfig in AGP finalizeDsl (AFTER this block) from the root kiteConfig { } config;
        // the exoOnly applicationId swap lives THERE, a module-level override here cannot win.

        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        // An unsigned release is unsigned even on a machine that holds the keystore, or the name
        // would say one thing and the file would be another. Debug signs itself below.
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
            /* An unsigned APK under a release filename is indistinguishable from a real one until
             * a device refuses to install it. A missing keystore stops the build instead, unless
             * the build asked for an unsigned one on purpose. */
            if (signingConfigs.findByName("synkplay_keystore") == null && !unsignedRelease) {
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
            // Signed with the release key when the keystore exists, so a debug build installs
            // over a release build and keeps the app data.
            signingConfigs.findByName("synkplay_keystore")?.let { config ->
                signingConfig = config
            }
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        // No pickFirst for libc++_shared.so any more: only the libmpvkt AAR ships one. If a
        // second dependency ever brings its own, AGP fails the merge, and that is the moment to
        // look at which copy is newer, not the moment to add a pickFirst.
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/versions/9/previous-compilation-data.bin"
            pickFirsts += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/license/**"
            excludes += "META-INF/native-image/**"
            // Dead SPI hooks from transitive deps (Rhino's JSR-223 entry, BlockHound's JVM
            // agent hook): neither can fire on Android; dropping them quiets R8 warnings.
            excludes += "META-INF/services/javax.script.ScriptEngineFactory"
            excludes += "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration"
        }
    }

    if (exoOnly) {
        packaging {
            jniLibs {
                // The exoOnly promise: no native player library in the APK. The libmpvkt
                // dependency stays so the engine code compiles; its libraries are dropped here.
                // verifyExoOnlyApk (buildSrc/ExoOnlyApkGate.kt) reads the finished APK and fails
                // the build if any of them slipped through anyway.
                for (lib in AppConfig.libmpvNativeLibs) {
                    excludes += ("**/$lib")
                }
                // KitePlayer's FFmpeg backend, the single largest native library here: it carries
                // a whole statically linked FFmpeg, so leaving it in would cost this flavor more
                // than mpv does and defeat the point of shipping no native players.
                // KitePlayerPlatform.isAvailable detects that this payload is absent.
                excludes += ("**/libkitecodec_jni.so")
            }
        }
    }
    /* No ABI splits, by decision (0.24.0): one universal APK per flavor carries every ABI. The
     * per-ABI files saved a download but put a five-way choice on the release page, and they
     * kept the APK and AAB builds in separate Gradle invocations (AGP issuetracker 402800800).
     * Play still gets the AAB and serves each phone only its own libraries. */

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

if (exoOnly) {
    // The exoOnly promise, checked on the bytes of every exoOnly APK. See buildSrc/ExoOnlyApkGate.kt.
    registerExoOnlyApkGate()
}

androidComponents {
    // KiteConfig applies the major SDK; AGP needs the minor level restored afterward.
    finalizeDsl { it.compileSdkMinor = providers.gradleProperty("android.compileSdkMinor").get().toInt() }
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val v = kiteConfig.version.get()
                // "universal" stays in the full name: it tells a downloader every ABI is inside.
                // The exo name is spelled "syncplay" on purpose, whatever the app is called now:
                // IzzyOnDroid's updater fetches the release asset by that name, and 0.24.0 broke
                // it for a day by following the rename.
                // A release that opted out of signing says so in its name, so the file cannot be
                // mistaken for a real one. See `unsignedRelease` at the top of this file.
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
}
