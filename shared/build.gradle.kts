@file:OptIn(ExperimentalKotlinGradlePluginApi::class)
@file:Suppress("UnstableApiUsage")
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.native.cocoapods")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {

    androidTarget()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

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

            /* Compose multiplatform port of Android's lottie-compose. We only need Lottie for
             * the day-night toggle button. This basically relies on lottie-compose for Android,
             * and on skiko's lottie support (aka Skottie) on the iOS side, and other platforms.*/
            implementation("io.github.alexzhirkevich:compottie:1.1.2") //1.1.2

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

android {
    compileSdk = 36
    namespace = "com.yuroyami.syncplay.shared"

    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        buildConfig = true
    }
}

compose.resources {
    publicResClass = true
    generateResClass = always
}