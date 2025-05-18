@file:OptIn(ExperimentalKotlinGradlePluginApi::class)
@file:Suppress("UnstableApiUsage")
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)

    alias(libs.plugins.android.application)

    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)

    alias(libs.plugins.kSerialization)
    //id("com.google.devtools.ksp")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
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
        pod("SPLPing") //Light-weight Objective-C library to add the ICMP ping functionality
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
            }
        }

        commonMain.dependencies {
            /* Forcing Kotlin libs to match the compiler */
            api(libs.kotlin.stdlib)

            /* Explicitly specifying a newer koroutines version */
            implementation(libs.kotlin.coroutines.core)

            /* Official JetBrains Kotlin Date 'n time manager (i.e: generating date from epoch) */
            api(libs.kotlinx.datetime)

            /* JSON serializer/deserializer to communicate with Syncplay servers */
            implementation(libs.kotlinx.serialization.json)

            /* Network client */
            api(libs.bundles.ktor)

            /* Android's "Uri" class but rewritten for Kotlin multiplatform */
            implementation("com.eygraber:uri-kmp:0.0.19")

            /* Jetpack Datastore for preferences and settings (accessible in Compose in real-time) */
            api(libs.datastore)

            /* Compose core dependencies */
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.components.resources)

            /* ViewModel support */
            api(libs.compose.viewmodel)

            /* Helps with color calculations for color preferences */
            implementation(libs.colormath)

            /* Hash digesters */
            implementation(libs.bundles.krypto)

            /* Logging */
            implementation(libs.logging.kermit)
        }

        androidMain.dependencies {
            /* Backward compatibility APIs */
            api(libs.jetpack.core)
            api(libs.jetpack.appcompat)

            /* SAF DocumentFile manager with backward compatibility */
            implementation(libs.jetpack.documentfile)

            /* Splash Screen with backward compatibility */
            api(libs.jetpack.splashscreen) //1.2.0 bugs our navbar opacity

            /* Extended coroutine support for Android threading */
            api(libs.kotlin.coroutines.android)

            /*  Activity's compose support with backward compatibility */
            api(libs.jetpack.activity.compose)

            /* Network and TLS */
            api(libs.netty)
            api(libs.conscrypt) //TLSv1.3 with backward compatibility

            /* Video player engine: Media3 (ExoPlayer and its extensions) */
            api(libs.bundles.media3)

            /* Video player engine: VLC (via libVLC) */
            api(libs.libvlc.android)

            /* Jetpack Home shortcut manager for quick launch with backward compatibility */
            api("androidx.core:core-google-shortcuts:1.2.0-alpha01") {
                exclude(group = "com.google.crypto.tink", module = "tink-android")
                exclude(group = "com.google.android.gms")
            }

        }

        iosMain.dependencies {
            /* Required ktor network client declaration for iOS */
            //implementation("io.ktor:ktor-client-ios:$ktor")
        }
    }
}

val exoOnly = false

android {
    namespace = "com.yuroyami.syncplay"
    compileSdk = 36

    sourceSets["main"].java.apply {
        srcDirs(srcDirs , "src/androidMain/java")
    }

    signingConfigs {
        create("github") {
            storeFile = file("${rootDir}/keystore/keystore.jks")
            keyAlias = "keystore"
            keyPassword = "az90az09"
            storePassword = "az90az09"
        }
    }

    defaultConfig {
        applicationId = "com.reddnek.syncplay"
        minSdk = 21
        targetSdk = 35
        versionCode = 1000015002 //Changing versionName semantic projection from 1.XXX.XXX.XXX to 1.XX.XX.XX
        versionName = "0.15.2"
        signingConfig = signingConfigs.getByName("github")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
            applicationIdSuffix = ".new"
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
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

dependencies {
    //implementation(files("libs/ext.aar")) /* ExoPlayer's FFmpeg extension  */
    coreLibraryDesugaring (libs.desugaring)
}

compose.resources {
    publicResClass = true
    generateResClass = always
}