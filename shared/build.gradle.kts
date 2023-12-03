plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.native.cocoapods")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("dev.icerock.mobile.multiplatform-resources")
    kotlin("plugin.serialization")
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Syncplay Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/syncplay-mobile"
        version = "0.13.0"
        ios.deploymentTarget = "14.1"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            isStatic = false
        }
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

        val ktor = "3.0.0-beta-1"
        val commonMain by getting {
            dependencies {
                /* Official JetBrains Kotlin Date 'n time manager (i.e: generating date from epoch) */
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

                /* Hash digesters */
                val kotlincrypto = "0.3.0"
                implementation("org.kotlincrypto.core:digest:$kotlincrypto")
                implementation("org.kotlincrypto.hash:md:$kotlincrypto")
                implementation("org.kotlincrypto.hash:sha2:$kotlincrypto")

                /* Network client */
                implementation("io.ktor:ktor-client-core:$ktor")
                implementation("io.ktor:ktor-network:$ktor")
                implementation("io.ktor:ktor-network-tls:$ktor")

                /* JSON serializer/deserializer to communicate with Syncplay servers */
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

                /* Jetpack Datastore for preferences and settings (accessible in Compose in real-time) */
                val datastore = "1.1.0-alpha07"
                api("androidx.datastore:datastore-preferences-core:$datastore")
                //api("androidx.datastore:datastore-core-okio:$datastore")

                /* Compose core dependencies */
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                api(compose.components.resources)

                /* Multiplatform localized string accessor helper */
                api("dev.icerock.moko:resources-compose:0.23.0") // for compose multiplatform

                implementation("com.github.ajalt.colormath:colormath:3.3.3")
            }
        }

        val androidMain by getting {
            dependencies {
                dependsOn(commonMain)

                /* Required ktor network client declaration for Android */
                implementation("io.ktor:ktor-client-android:$ktor")

                /* AndroidX compat */
                api("androidx.core:core-ktx:1.12.0")
                api("androidx.appcompat:appcompat:1.7.0-alpha03")

                /* SAF DocumentFile manager */
                api("androidx.documentfile:documentfile:1.0.1")

                /* AndroidX's Splash Screen */
                api("androidx.core:core-splashscreen:1.1.0-alpha02")

                /* Jetpack Shared Preferences */
                api("androidx.preference:preference-ktx:1.2.1")

                /* Jetpack Home shortcut manager */
                api("androidx.core:core-google-shortcuts:1.1.0") {
                    exclude(group = "com.google.crypto.tink", module = "tink-android")
                    exclude(group = "com.google.android.gms")
                }

                /* Compose add-ons */
                api("androidx.activity:activity-compose:1.8.1")

                /* Lottie for animations (like Nightmode toggle button) */
                implementation("com.airbnb.android:lottie-compose:6.2.0")

                /* Media3 (ExoPlayer and its extensions) */
                val media3 = "1.2.0"
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
            }
        }
//        val iosX64Main by getting
//        val iosArm64Main by getting
//        val iosSimulatorArm64Main by getting
        val iosMain by getting {
            dependsOn(commonMain)
//            iosX64Main.dependsOn(this)
//            iosArm64Main.dependsOn(this)
//            iosSimulatorArm64Main.dependsOn(this)

            dependencies {
                /* Required ktor network client declaration for iOS */
                implementation("io.ktor:ktor-client-ios:$ktor")
            }
        }
    }
}
//
//compose {
//    kotlinCompilerPlugin.set("1.5.4-dev1-kt2.0.0-Beta1")
//}

android {
    compileSdk = (findProperty("android.compileSdk") as String).toInt()
    namespace = "com.yuroyami.syncplay.shared"

    //sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    //sourceSets["main"].res.srcDirs("src/androidMain/res")
    //sourceSets["main"].resources.srcDirs("src/commonMain/resources")
    sourceSets {
        named("main") {
            resources.srcDirs("src/commonMain/resources")
           // assets.srcDirs("src/commonMain/resources/assets")
        }
    }

    defaultConfig {
        minSdk = (findProperty("android.minSdk") as String).toInt()
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


multiplatformResources {
    multiplatformResourcesPackage = "com.yuroyami.syncplay.shared" // required
    //disableStaticFrameworkWarning = true
    //multiplatformResourcesClassName = "SharedRes" // optional, default MR
    //multiplatformResourcesVisibility = MRVisibility.Internal // optional, default Public
    //iosBaseLocalizationRegion = "en" // optional, default "en"
    // multiplatformResourcesSourceSet = "commonClientMain"  // optional, default "commonMain"

    //  /Users/mac/Library/Java/JavaVirtualMachines/openjdk-20.02/Contents/Home
    // /Applications/Android\ Studio.app/Contents/jbr/Contents/Home
}
