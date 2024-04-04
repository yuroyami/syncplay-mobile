import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.native.cocoapods")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

val lyricist = "1.6.2"

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Syncplay Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/syncplay-mobile"
        version = "0.14.0"
        ios.deploymentTarget = "14.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            isStatic = false
        }

        pod("MobileVLCKit", "3.6.0b10") //Adds the VLC player engine to iOS
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


        val commonMain by getting {
            dependencies {
                //Strings internationalization and localization
                api("cafe.adriel.lyricist:lyricist:$lyricist")

                //api("dev.icerock.moko:mvvm-core:0.16.1")

                /* Official JetBrains Kotlin Date 'n time manager (i.e: generating date from epoch) */
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

                /* Hash digesters */
                val kotlincrypto = "0.4.0"
                implementation("org.kotlincrypto.core:digest:$kotlincrypto")
                implementation("org.kotlincrypto.hash:md:$kotlincrypto")
                implementation("org.kotlincrypto.hash:sha2:$kotlincrypto")

                /* Network client */
                val ktor =  /* "2.3.9" */ "3.0.0-beta-1"
                implementation("io.ktor:ktor-network:$ktor")
                //api("io.ktor:ktor-network-tls:$ktor")

                /* Android's "Uri" class but rewritten for Kotlin multiplatform */
                implementation("com.eygraber:uri-kmp:0.0.18")

                /* JSON serializer/deserializer to communicate with Syncplay servers */
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

                /* Explicitly specifying a newer koroutines */
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1-Beta")

                /* Jetpack Datastore for preferences and settings (accessible in Compose in real-time) */
                val datastore = "1.1.0-rc01"
                api("androidx.datastore:datastore-preferences-core:$datastore")

                /* Compose core dependencies */
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)
                api(compose.materialIconsExtended)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                api(compose.components.resources)

                /* Helps with color calculations for color preferences */
                implementation("com.github.ajalt.colormath:colormath:3.4.0")
            }
        }

        val androidMain by getting {
            dependencies {
                dependsOn(commonMain)

                /* Backward compatibility APIs */
                api("androidx.core:core-ktx:1.13.0-rc01")
                api("androidx.appcompat:appcompat:1.7.0-alpha03")

                /* SAF DocumentFile manager with backward compatibility */
                implementation("androidx.documentfile:documentfile:1.0.1")

                /* Splash Screen with backward compatibility */
                api("androidx.core:core-splashscreen:1.1.0-rc01")

                /* Jetpack Home shortcut manager for quick launch with backward compatibility */
                api("androidx.core:core-google-shortcuts:1.2.0-alpha01") {
                    exclude(group = "com.google.crypto.tink", module = "tink-android")
                    exclude(group = "com.google.android.gms")
                }

                /*  Activity's compose support with backward compatibility */
                api("androidx.activity:activity-compose:1.9.0-rc01")

                /* Lottie for animations (like Nightmode toggle button) */
                implementation("com.airbnb.android:lottie-compose:6.4.0")

                /* Network and TLS */
                implementation("io.netty:netty-all:4.1.108.Final")
                api("org.conscrypt:conscrypt-android:2.5.2") //TLSv1.3 with backward compatibility

                /* Video player engine: Media3 (ExoPlayer and its extensions) */
                val media3 = "1.3.0"
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
        }

        val iosMain by getting {
            dependencies {
                /* Required ktor network client declaration for iOS */
                //implementation("io.ktor:ktor-client-ios:$ktor")
            }
        }
    }
}

//compose {
//    kotlinCompilerPlugin.set("1.5.9-kt-2.0.0-Beta4")
//}

android {
    compileSdk = (findProperty("android.compileSdk") as String).toInt()
    namespace = "com.yuroyami.syncplay.shared"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk = (findProperty("android.minSdk") as String).toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlin {
        jvmToolchain(8)
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

tasks.withType<AndroidLintAnalysisTask>{
    dependsOn("copyFontsToAndroidAssets")
}

tasks.withType<LintModelWriterTask>{
    dependsOn("copyFontsToAndroidAssets")
}
