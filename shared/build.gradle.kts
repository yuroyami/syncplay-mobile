@file:Suppress("UnstableApiUsage")

import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)

    alias(libs.plugins.android.application)

    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)

    alias(libs.plugins.kSerialization)
    //id("com.google.devtools.ksp")

    alias(libs.plugins.touchlab.skie)
}

kotlin {
    jvmToolchain(21)

    // Activating Android target (androidMain)
    androidTarget()

    // Activating iOS targets (iosMain)
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // iOS configuration
    cocoapods {
        summary = "Syncplay Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/syncplay-mobile"
        version = "1.0.0"
        ios.deploymentTarget = "14.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            isStatic = true
        }

        pod("MobileVLCKit", libs.versions.libvlc.ios.get()) //Adds the VLC player engine to iOS
        //pod("SPLPing", "1.1.8") //Light-weight Objective-C library to add the ICMP ping functionality
        //
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
                enableLanguageFeature("ExplicitBackingFields")
                enableLanguageFeature("NestedTypeAliases") //Equivalent to -Xnested-type-aliases compiler flag
                enableLanguageFeature("ExpectActualClasses") //Equivalent to "-Xexpect-actual-classes compiler flag
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
            implementation(compose.runtime)
            implementation(compose.foundation)
            //implementation(compose.material3)
            //noinspection UseTomlInstead
            implementation("org.jetbrains.compose.material3:material3:1.10.0-alpha01") //Temporary to get android's m3 1.5.0 expressive functionality until material3 is updated to 1.5.0 stable
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)

            /* ViewModel support */
            implementation(libs.compose.viewmodel)
            implementation(libs.compose.navigation)

            /* ComposableHorizons' unstyled composables for more granularly-controlled components */
            implementation(libs.compose.unstyled)

            /* Helps with color calculations for color preferences */
            implementation(libs.kolorpicker)

            /* Hash digesters */
            implementation(libs.bundles.krypto)

            /* Logging */
            implementation(libs.logging.kermit)

            /* File opener/saver multiplatform */
            implementation(libs.filekit)

            /* Microsoft Fluent's Design System for Compose multiplatform */
            implementation(libs.fluent.designsystem)
            implementation(libs.fluent.icons)
        }

        androidMain.dependencies {
            /* Backward compatibility APIs */
            implementation(libs.jetpack.core)
            implementation(libs.jetpack.appcompat)

            /* SAF DocumentFile manager with backward compatibility */
            implementation(libs.jetpack.documentfile)

            /* Splash Screen with backward compatibility */
            implementation(libs.jetpack.splashscreen) //1.2.0 bugs our navbar opacity

            /* Extended coroutine support for Android threading */
            implementation(libs.kotlin.coroutines.android)

            /*  Activity's compose support with backward compatibility */
            implementation(libs.jetpack.activity.compose)

            /* Network and TLS */
            implementation(libs.netty)
            implementation(libs.conscrypt) //TLSv1.3 with backward compatibility

            /* Video player engine: Media3 (ExoPlayer and its extensions) */
            implementation(libs.bundles.media3)
            implementation(files(File(projectDir, "libs/ext.aar"))) /* ExoPlayer's FFmpeg extension  */

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
    val exoOnly = false
    val forGPlay = false

    namespace = "com.yuroyami.syncplay"
    compileSdk = 36

    /*sourceSets["main"].java.apply {
        srcDirs(srcDirs , "src/androidMain/java")
    }*/

    signingConfigs {
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        val keystore = file("${rootDir}/keystore/syncplaykeystore.jks")
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
        minSdk = 23
        targetSdk = 35 //TODO We only update to targetSdk 36 when we resolve the 16KB native libs alignment issue
        versionCode = 1_000_016_00_0 //1 XXX XXX XX X (last X is for prerelease versions such as RC)
        versionName = "0.16.0"

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
        buildConfig = true
        viewBinding = true //I prefer using viewbinding to quickly inflate player XML views.
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

    packaging {
        //jniLibs.useLegacyPackaging = true
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/versions/9/previous-compilation-data.bin"
            pickFirsts += "META-INF/io.netty.versions.properties"
        }
    }

    if (!exoOnly) {
        splits {
            val abiCodes = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            abi {
                isEnable = true
                reset()
                for (abi in abiCodes) {
                    val exists = file("$projectDir/src/main/jniLibs/$abi").exists()
                    if (exists) {
                        include(abi)
                    }
                }
                isUniversalApk = true
            }
        }
    } else {
        packaging {
            jniLibs {
                //mpv libs
                excludes += ("**/libavcodec.so")
                excludes += ("**/libavdevice.so")
                excludes += ("**/libavfilter.so")
                excludes += ("**/libavformat.so")
                excludes += ("**/libavutil.so")
                excludes += ("**/libmpv.so")
                excludes += ("**/libplayer.so")
                excludes += ("**/libpostproc.so")
                excludes += ("**/libswresample.so")
                excludes += ("**/libswscale.so")

                //vlc
                excludes += ("**/libvlc.so")
            }
        }
    }

    flavorDimensions.add("flavor")
    productFlavors {
        create(if (exoOnly) "noLibs" else "withLibs") {
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
    coreLibraryDesugaring (libs.desugaring)
}

compose.resources {
    publicResClass = true
    generateResClass = always
}