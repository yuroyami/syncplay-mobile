
import AppConfig.ndkRequired
import java.nio.file.Files

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(AppConfig.javaVersion)
}

android {
    namespace = "com.yuroyami.syncplay.app"
    compileSdk = AppConfig.compileSdk

    ndkVersion = ndkRequired

    signingConfigs {
        file("${rootDir}/keystore/syncplaykey.jks").takeIf { it.exists() }?.let { keystoreFile ->
            create("syncplay_keystore") {
                storeFile = keystoreFile
                AppConfig.localProperties.apply {
                    keyAlias = getProperty("yuroyami.keyAlias")
                    keyPassword = getProperty("yuroyami.keyPassword")
                    storePassword = getProperty("yuroyami.storePassword")
                }
            }
        }
    }

    defaultConfig {
        applicationId = if (AppConfig.forGooglePlay) "com.yuroyami.syncplay" else "com.reddnek.syncplay"
        minSdk = AppConfig.minSdk
        targetSdk = AppConfig.compileSdk
        versionCode = AppConfig.versionCode
        versionName = AppConfig.versionName

        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        signingConfigs.findByName("syncplay_keystore")?.let { config ->
            signingConfig = config
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(AppConfig.javaVersion)
        targetCompatibility = JavaVersion.toVersion(AppConfig.javaVersion)
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = AppConfig.exoOnly //TODO Fix minified build when mpv and libVLC are included
        }
        debug {
            applicationIdSuffix = ".debug"
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

        }
    }

    packaging {
        jniLibs {
            pickFirsts += "**/libc++_shared.so" //Pick our local c++_shared only and not the one in VLC aar
        }
    }

    if (AppConfig.exoOnly) {
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

    flavorDimensions.add("flavor")
    productFlavors {
        create(if (AppConfig.exoOnly) "exoOnly" else "allEngines") {
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
    //This will remove them also from any other library that might use them
    configurations.all {
        exclude(group = "com.google.crypto.tink", module = "tink-android")
        exclude(group = "com.google.android.gms")
    }
}

if (!AppConfig.exoOnly) {
    afterEvaluate {
        val sdkComponents = androidComponents.sdkComponents
        val ndkPath = sdkComponents.ndkDirectory.get().asFile

        if (!ndkPath.exists()) {
            throw GradleException(
                """
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            ❌ ANDROID NDK $ndkRequired REQUIRED!
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            
            Install via:
              • Android Studio → SDK Manager → SDK Tools → 
                NDK (Side by side) → Show Package Details → 
                Check version $ndkRequired
            
            Or add to local.properties:
              ndk.dir=/path/to/android/sdk/ndk/$ndkRequired
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            """.trimIndent()
            )
        }

        println("$ndkPath IS YOUR NDK!!!!!!!")

        // Verify it's the correct version
        val actualVersion = ndkPath.name // e.g., "26.1.10909125"
        if (actualVersion != ndkRequired) {
            throw GradleException(
                """
            ❌ Wrong NDK version!
            Required: $ndkRequired
            Found: $actualVersion at ${ndkPath.absolutePath}
            
            Please install NDK $ndkRequired via SDK Manager.
            """.trimIndent()
            )
        }

        logger.lifecycle("✓ NDK $actualVersion found at: ${ndkPath.absolutePath}")
    }

    tasks.register<Exec>("runAndroidMpvNativeBuildScripts") {
        workingDir = File(rootProject.rootDir, "buildscripts")

        inputs.files(
            File(workingDir, "mpv_download_deps.sh"),
            File(workingDir, "mpv_build.sh")
        )

        // Use inputs.property with normalization
        inputs.property("ndkVersion", ndkRequired)
            .optional(false)

        // Register all output files explicitly
        AppConfig.abiCodes.forEach { abiCode ->
            AppConfig.mpvLibs.forEach { mpvLib ->
                outputs.file(File(projectDir, "src/main/libs/${abiCode.key}/$mpvLib"))
            }
        }

        outputs.cacheIf { true }

        if (System.getProperty("os.name").startsWith("Windows")) {
            doFirst {
                logger.warn("Native library build is not supported on Windows. Skipping...")
            }
            isEnabled = false
        } else {
            val sdkComponents = androidComponents.sdkComponents

            doFirst {
                val sdkPath = sdkComponents.sdkDirectory.get().asFile
                val ndkPath = sdkComponents.ndkDirectory.get().asFile

                if (!ndkPath.exists()) {
                    throw GradleException(
                        "Android NDK is required but not found!\n" +
                                "Please install NDK via Android Studio SDK Manager or set ndk.dir in local.properties"
                    )
                }

                environment("ANDROID_SDK_ROOT", sdkPath.absolutePath)
                environment("ANDROID_NDK_HOME", ndkPath.absolutePath)

                println("✓ NDK found at: ${ndkPath.absolutePath}")

                val osName = System.getProperty("os.name").lowercase()
                val myOS = when {
                    osName.contains("mac") || osName.contains("darwin") -> "mac"
                    osName.contains("linux") -> "linux"
                    osName.contains("windows") -> "windows"
                    else -> "unknown"
                }

                logger.lifecycle("Detected OS: $myOS")

                val sdkSymlinkDir = File(workingDir, "sdk")
                val symlink = File(sdkSymlinkDir, "android-sdk-$myOS")

                if (!sdkSymlinkDir.exists()) {
                    sdkSymlinkDir.mkdirs()
                }

                if (symlink.exists()) {
                    if (Files.isSymbolicLink(symlink.toPath())) {
                        Files.delete(symlink.toPath())
                        logger.lifecycle("Removed old symlink: ${symlink.absolutePath}")
                    } else {
                        throw GradleException("${symlink.absolutePath} exists but is not a symlink!")
                    }
                }

                try {
                    Files.createSymbolicLink(symlink.toPath(), sdkPath.toPath())
                    logger.lifecycle("✓ Created symlink: ${symlink.absolutePath} -> ${sdkPath.absolutePath}")
                } catch (e: Exception) {
                    throw GradleException("Failed to create symlink: ${e.message}")
                }

                commandLine(
                    "sh", "-c", """
                sh mpv_download_deps.sh "$sdkPath" "$ndkPath" &&
                sh mpv_build.sh --arch armv7l mpv &&
                sh mpv_build.sh --arch arm64 mpv &&
                sh mpv_build.sh --arch x86 mpv &&
                sh mpv_build.sh --arch x86_64 mpv &&
                sh mpv_build.sh -n syncplay-withmpv
            """.trimIndent()
                )

                logger.lifecycle("Running: ${commandLine.joinToString(" ")}")
            }
        }

        // Run task only if output files are missing (partially or fully)
        onlyIf {
            val allFilesExist = AppConfig.abiCodes.all { abiCode ->
                AppConfig.mpvLibs.all { mpvLib ->
                    File(projectDir, "src/main/libs/${abiCode.key}/$mpvLib").exists()
                }
            }

            if (allFilesExist) {
                logger.lifecycle("✓ All MPV libs exist, skipping build")
            } else {
                logger.lifecycle("✗f Some MPV libs missing, will build")
            }

            !allFilesExist
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val abiFilter = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
                val fileName = if (AppConfig.exoOnly) {
                    "syncplay-${AppConfig.versionName}-exo-only.apk"
                } else {
                    val abiName = abiFilter ?: "universal"
                    "syncplay-${AppConfig.versionName}-full-${abiName}.apk"
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

if (!AppConfig.exoOnly) {
    tasks.named("preBuild") {
        dependsOn("runAndroidMpvNativeBuildScripts")
    }
}

