import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.native.cocoapods")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

val lyricist = "1.7.0"

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    //If you're building on a Windows machine, make sure to remove the next 3 targets as they require Xcode.
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
            isStatic = false
        }

        pod("MobileVLCKit", "3.6.0b11") //Adds the VLC player engine to iOS
        //pod("MobileVLCKit", "4.0.0a2") //Adds the VLC player engine to iOS
        //pod("VLCKit", "4.0.0a4") //a2
        pod("SPLPing") //Light-weight Objective-C library to add the ICMP ping functionality
    }

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
            }
        }

        commonMain.dependencies {
            /* Forcing Kotlin libs to match the compiler */
            api("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")

            //Strings internationalization and localization
            api("cafe.adriel.lyricist:lyricist:$lyricist")

            /* Official JetBrains Kotlin Date 'n time manager (i.e: generating date from epoch) */
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

            /* Hash digesters */
            val kotlincrypto = "0.5.1"
            implementation("org.kotlincrypto.core:digest:$kotlincrypto")
            implementation("org.kotlincrypto.hash:md:$kotlincrypto")
            implementation("org.kotlincrypto.hash:sha2:$kotlincrypto")

            /* Network client */
            val ktor = "2.3.11" //"3.0.0-beta-1"
            implementation("io.ktor:ktor-network:$ktor")
            //api("io.ktor:ktor-network-tls:$ktor")

            /* Android's "Uri" class but rewritten for Kotlin multiplatform */
            implementation("com.eygraber:uri-kmp:0.0.18")

            /* JSON serializer/deserializer to communicate with Syncplay servers */
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0-RC")

            /* Explicitly specifying a newer koroutines version */
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

            /* Jetpack Datastore for preferences and settings (accessible in Compose in real-time) */
            val datastore = "1.1.1"
            api("androidx.datastore:datastore-preferences-core:$datastore")

            /* Compose core dependencies */
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.components.resources)

            /* Helps with color calculations for color preferences */
            implementation("com.github.ajalt.colormath:colormath:3.5.0")

            /* Compose multiplatform port of Android's lottie-compose. We only need Lottie for
             * the day-night toggle button. This basically relies on lottie-compose for Android,
             * and on skiko's lottie support (aka Skottie) on the iOS side, and other platforms.*/
            implementation("io.github.alexzhirkevich:compottie:1.1.2")

            /* Annotations */
            api("androidx.annotation:annotation:1.8.0")

        }

        androidMain.dependencies {
            /* Backward compatibility APIs */
            api("androidx.core:core-ktx:1.14.0-alpha01")
            api("androidx.appcompat:appcompat:1.7.0-rc01")

            /* SAF DocumentFile manager with backward compatibility */
            implementation("androidx.documentfile:documentfile:1.0.1")

            /* Splash Screen with backward compatibility */
            api("androidx.core:core-splashscreen:1.1.0-rc01") //1.2.0 bugs our navbar opacity

            /* Jetpack Home shortcut manager for quick launch with backward compatibility */
            api("androidx.core:core-google-shortcuts:1.2.0-alpha01") {
                exclude(group = "com.google.crypto.tink", module = "tink-android")
                exclude(group = "com.google.android.gms")
            }

            /*  Activity's compose support with backward compatibility */
            api("androidx.activity:activity-compose:1.9.0")

            /* Network and TLS */
            implementation("io.netty:netty-all:4.1.109.Final")
            api("org.conscrypt:conscrypt-android:2.5.2") //TLSv1.3 with backward compatibility

            /* Video player engine: Media3 (ExoPlayer and its extensions) */
            val media3 = "1.4.0-alpha01"
            api("androidx.media3:media3-exoplayer:$media3")
            api("androidx.media3:media3-exoplayer-dash:$media3")
            api("androidx.media3:media3-exoplayer-hls:$media3")
            api("androidx.media3:media3-exoplayer-rtsp:$media3")
            api("androidx.media3:media3-datasource-okhttp:$media3")
            api("androidx.media3:media3-ui:$media3")
            api("androidx.media3:media3-session:$media3")
            api("androidx.media3:media3-extractor:$media3")
            api("androidx.media3:media3-decoder:$media3")
            api("androidx.media3:media3-datasource:$media3")
            api("androidx.media3:media3-common:$media3")

            /* Video player engine: VLC (via libVLC) */
            api("org.videolan.android:libvlc-all:4.0.0-eap15")

        }

        iosMain.dependencies {
            /* Required ktor network client declaration for iOS */
            //implementation("io.ktor:ktor-client-ios:$ktor")
        }
    }
}

//compose {
//    kotlinCompilerPlugin.set("1.5.9-kt-2.0.0-Beta4")
//}

android {
    compileSdk = 34
    namespace = "com.yuroyami.syncplay.shared"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        buildConfig = true
    }
}

ksp {
    val strings = kotlin.sourceSets.getByName("commonMain").resources.srcDirs.first()
    arg("lyricist.xml.resourcesPath", strings.absolutePath)
}

dependencies {
    ksp("cafe.adriel.lyricist:lyricist-processor:$lyricist")
    ksp("cafe.adriel.lyricist:lyricist-processor-xml:$lyricist")
}