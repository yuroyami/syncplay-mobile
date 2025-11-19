import java.nio.file.Files
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kSerialization)
    alias(libs.plugins.ksp)
    //alias(libs.plugins.touchlab.skie)
    alias(libs.plugins.buildConfig)
}

val exoOnly = false

val abiCodes = mapOf(
    "armeabi-v7a" to "armv7l",
    "arm64-v8a" to "arm64",
    "x86" to "x86",
    "x86_64" to "x86_64"
)
val mpvLibs = listOf(
    "libavcodec.so", "libavdevice.so", "libavfilter.so", "libavformat.so", "libavutil.so",
    "libmpv.so", "libplayer.so",
    "libswresample.so", "libswscale.so"
)
val ndkRequired = "29.0.14206865"
val verString = "0.16.0"
val verCode = ("1" + verString.split(".").joinToString("") { it.padStart(3, '0') }).toIntOrNull() ?: 0

kotlin {
    jvmToolchain(21)

    // Activating Android target (androidMain)
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                if (!exoOnly) dependsOn("runAndroidMpvNativeBuildScripts")
            }
        }
    }

    // Activating iOS targets (iosMain)
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.compilations.getByName("main") {
            val nsKVO by cinterops.creating {
                defFile("src/nativeInterop/cinterop/NSKeyValueObserving.def")
            }
        }
    }

    // iOS configuration
    cocoapods {
        summary = "Syncplay Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/syncplay-mobile"
        version = "1.0.1"
        ios.deploymentTarget = "14.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            isStatic = false
        }

        pod("SPLPing", libs.versions.splping.get()) //Light-weight Objective-C library to add the ICMP ping functionality
        pod("MobileVLCKit", libs.versions.libvlc.ios.get()) //Adds the VLC player engine to iOS
    }

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                optIn("kotlin.RequiresOptIn")
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("kotlin.uuid.ExperimentalUuidApi")
                optIn("kotlin.ExperimentalUnsignedTypes")
                optIn("kotlin.ExperimentalStdlibApi")
                optIn("kotlin.io.encoding.ExperimentalEncodingApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
                optIn("kotlinx.cinterop.ExperimentalForeignApi") //for iOS
                optIn("kotlinx.cinterop.BetaInteropApi") //for iOS
                optIn("kotlin.time.ExperimentalTime")
                enableLanguageFeature("ExplicitBackingFields") //same as -Xexplicit-baxcking-fields compiler flag
                enableLanguageFeature("NestedTypeAliases") //-Xnested-type-aliases
                enableLanguageFeature("ExpectActualClasses") //-Xexpect-actual-classes
                enableLanguageFeature("ContextParameters") //Xcontext-parameters
            }
        }

        commonMain.dependencies {
            /* Forcing Kotlin libs to match the compiler */
            implementation(libs.kotlin.stdlib)

            /* Explicitly specifying a newer koroutines version */
            implementation(libs.kotlin.coroutines.core)

            /* Official JetBrains Kotlin Date 'n time manager (i.e: generating date from epoch) */
            implementation(libs.kotlinx.datetime)

            /* JSON serializer/deserializer to communicate with Syncplay servers */
            implementation(libs.kotlinx.serialization.json)

            /* Network client */
            implementation(libs.bundles.ktor)

            /* Android's "Uri" class but rewritten for Kotlin multiplatform */
            implementation(libs.uriKmp)

            /* Jetpack Datastore for preferences and settings (accessible in Compose in real-time) */
            implementation(libs.datastore)

            /* Compose core dependencies */
            implementation(libs.bundles.compose.multiplatform)

            /* ViewModel support */
            implementation(libs.compose.viewmodel)

            /* Navigation support with the modern nav3 library */
            implementation(libs.bundles.navigation3)

            /* ComposableHorizons' unstyled composables for more granularly-controlled components */
            implementation(libs.compose.unstyled)

            /* MaterialKolor generates Material3 themes from seed colors */
            implementation(libs.materialKolor)

            /* Helps with color calculations for color preferences */
            implementation(libs.kolorpicker)

            /* Hash digesters */
            implementation(libs.bundles.krypto)

            /* Logging */
            implementation(libs.logging.kermit)

            /* File opener/saver multiplatform */
            implementation(libs.filekit)

            /* Atomics (used only for logs at the moment) */
            implementation(libs.atomicfu)
        }

        androidMain.dependencies {
            /* Backward compatibility APIs */
            implementation(libs.jetpack.core)
            implementation(libs.jetpack.appcompat)

            /* SAF DocumentFile manager with backward compatibility */
            implementation(libs.jetpack.documentfile)

            /* Splash Screen with backward compatibility */
            implementation(libs.jetpack.splashscreen)

            /* Extended coroutine support for Android threading */
            implementation(libs.kotlin.coroutines.android)

            /*  Activity's compose support with backward compatibility */
            implementation(libs.jetpack.activity.compose)

            /* Network and TLS */
            implementation(libs.netty)
            implementation(libs.conscrypt) //TLSv1.3 with backward compatibility

            /* Video player engine: Media3 (ExoPlayer and its extensions) */
            implementation(libs.bundles.media3)
            /* ExoPlayer's FFmpeg-powered audio renderer extension (this does not need to be updated with every media3 release)  */
            implementation(files(File(projectDir, "libs/libffmpeg_media3exo_1.8.0.aar")))

            /* Video player engine: VLC (via libVLC) */
            implementation(libs.libvlc.android)

            /* Jetpack Home shortcut manager for quick launch with backward compatibility */
            implementation(libs.google.shortcuts)
        }

        iosMain.dependencies {
            /* Required ktor network client declaration for iOS */
            //implementation("io.ktor:ktor-client-ios:$ktor")
        }
    }
}

android {
    val forGPlay = false

    namespace = "com.yuroyami.syncplay"
    compileSdk = 36

    ndkVersion = ndkRequired

    signingConfigs {
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        val keystore = file("${rootDir}/keystore/syncplaykey.jks")
        if (keystore.exists()) {
            create("syncplay_keystore") {
                storeFile = keystore
                keyAlias = localProperties.getProperty("yuroyami.keyAlias")
                keyPassword = localProperties.getProperty("yuroyami.keyPassword")
                storePassword = localProperties.getProperty("yuroyami.storePassword")
            }
        }
    }

    defaultConfig {
        applicationId = if (forGPlay) "com.yuroyami.syncplay" else "com.reddnek.syncplay"
        minSdk = 26
        targetSdk = 36
        versionCode = verCode
        versionName = verString

        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        signingConfigs.findByName("syncplay_keystore")?.let { config ->
            signingConfig = config
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        buildConfig = false //We're using a custom buildconfig plugin for KMM, we don't need the Android one
        viewBinding = true //I prefer using viewbinding to inflate player XML views.
    }

    buildTypes {
        release {
            isMinifyEnabled = exoOnly
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (exoOnly) {
                output?.outputFileName = "syncplay-${verString}-exo-only.apk"
            } else {
                val abiFilter = output?.getFilter(com.android.build.OutputFile.ABI)
                val abiName = abiFilter ?: "universal"
                output?.outputFileName = "syncplay-${verString}-full-${abiName}.apk"
            }
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/versions/9/previous-compilation-data.bin"
            pickFirsts += "META-INF/io.netty.versions.properties"
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
                for (mpvLib in mpvLibs) {
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
                for (abi in abiCodes) {
                    include(abi.key)
                }
                isUniversalApk = true
            }
        }
    }

    flavorDimensions.add("flavor")
    productFlavors {
        create(if (exoOnly) "exoOnly" else "allEngines") {
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

dependencies {
    coreLibraryDesugaring(libs.desugaring)
}

compose.resources {
    publicResClass = true
    generateResClass = always
}

if (!exoOnly) {
    afterEvaluate {
        val androidExt = extensions.getByType<com.android.build.gradle.BaseExtension>()
        val ndkPath = androidExt.ndkDirectory

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
        abiCodes.forEach { abiCode ->
            mpvLibs.forEach { mpvLib ->
                outputs.file(File(projectDir, "src/androidMain/libs/${abiCode.key}/$mpvLib"))
            }
        }

        outputs.cacheIf { true }

        if (System.getProperty("os.name").startsWith("Windows")) {
            doFirst {
                logger.warn("Native library build is not supported on Windows. Skipping...")
            }
            isEnabled = false
        } else {
            val androidExt = project.extensions.getByType<com.android.build.gradle.BaseExtension>()

            doFirst {
                val sdkPath = androidExt.sdkDirectory
                val ndkPath = androidExt.ndkDirectory

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
            val allFilesExist = abiCodes.all { abiCode ->
                mpvLibs.all { mpvLib ->
                    File(projectDir, "src/androidMain/libs/${abiCode.key}/$mpvLib").exists()
                }
            }

            if (allFilesExist) {
                logger.lifecycle("✓ All MPV libs exist, skipping build")
            } else {
                logger.lifecycle("✗ Some MPV libs missing, will build")
            }

            !allFilesExist
        }
    }
}

buildConfig {
    buildConfigField("APP_VERSION", verString)
    buildConfigField("IS_DEBUG", true)
    buildConfigField("DEBUG_PROTOCOL_IN_OUT", true)
    buildConfigField("EXOPLAYER_ONLY", exoOnly)
}