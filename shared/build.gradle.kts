plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.androidLibrary)
    id("org.jetbrains.compose")
    id("dev.icerock.mobile.multiplatform-resources")
    kotlin("plugin.serialization") version "1.9.10"
}

val ktor = "2.3.4"
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()

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
        version = "0.13.0"
        ios.deploymentTarget = "14.1"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.github.aakira:napier:2.6.1")

                val kotlincrypto = "0.3.0"
                implementation("org.kotlincrypto.core:digest:$kotlincrypto")
                implementation("org.kotlincrypto.hash:md:$kotlincrypto")
                implementation("org.kotlincrypto.hash:sha2:$kotlincrypto")

                val ktor = "2.3.4"
                implementation("io.ktor:ktor-client-logging:$ktor")
                implementation("io.ktor:ktor-client-core:$ktor")
                implementation("io.ktor:ktor-network:$ktor")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")

                val datastore = "1.1.0-alpha05"
                api(libs.datastore.preferences.core)
                api(libs.datastore.core.okio)

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)

                //@OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                //implementation(compose.components.resources)

                api(libs.moko.resources.compose) // for compose multiplatform

                //implementation("org.jetbrains.skiko:skiko:0.7.77")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val androidMain by getting {
            dependencies {
                dependsOn(commonMain)
                implementation("io.ktor:ktor-client-android:$ktor")


                api("androidx.core:core-ktx:1.12.0")
                api("androidx.appcompat:appcompat:1.7.0-alpha03")
                api("androidx.documentfile:documentfile:1.0.1") /* Managing Scoped Storage */
                api("androidx.core:core-splashscreen:1.0.1")
                api("androidx.preference:preference-ktx:1.2.1")

                api("androidx.core:core-google-shortcuts:1.1.0") {
                    exclude(group = "com.google.crypto.tink", module = "tink-android")
                    exclude(group = "com.google.android.gms")
                }

                /* More compose add-ons */
                api("androidx.activity:activity-compose:1.7.2")
                implementation("com.godaddy.android.colorpicker:compose-color-picker-android:0.7.0")

                /* Media3 (ExoPlayer + MediaSession etc) */
                val media3 = "1.2.0-alpha02"
                implementation(libs.media3.exoplayer)
                implementation("androidx.media3:media3-exoplayer-dash:$media3")
                implementation("androidx.media3:media3-exoplayer-hls:$media3")
                implementation("androidx.media3:media3-exoplayer-rtsp:$media3")
                implementation("androidx.media3:media3-datasource-okhttp:$media3")
                implementation("androidx.media3:media3-ui:$media3")
                implementation("androidx.media3:media3-session:$media3")
                implementation("androidx.media3:media3-extractor:$media3")
                implementation("androidx.media3:media3-decoder:$media3")
                implementation("androidx.media3:media3-datasource:$media3")
                implementation("androidx.media3:media3-common:$media3")
            }
        }
        val iosMain by getting {
//            dependsOn(commonMain)
//            iosX64Main.dependsOn(this)
//            iosArm64Main.dependsOn(this)
//            iosSimulatorArm64Main.dependsOn(this)
//

            dependencies {
                implementation("io.ktor:ktor-client-ios:$ktor")
            }
        }
    }
}

android {
    compileSdk = (findProperty("android.compileSdk") as String).toInt()
    namespace = "com.yuroyami.syncplay"

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
}


multiplatformResources {
    multiplatformResourcesPackage = "com.yuroyami.syncplay" // required
    //disableStaticFrameworkWarning = true
    //multiplatformResourcesClassName = "SharedRes" // optional, default MR
    //multiplatformResourcesVisibility = MRVisibility.Internal // optional, default Public
    //iosBaseLocalizationRegion = "en" // optional, default "en"
    // multiplatformResourcesSourceSet = "commonClientMain"  // optional, default "commonMain"

    //  /Users/mac/Library/Java/JavaVirtualMachines/openjdk-20.02/Contents/Home
    // /Applications/Android\ Studio.app/Contents/jbr/Contents/Home
}
