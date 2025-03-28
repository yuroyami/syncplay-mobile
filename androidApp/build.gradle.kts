import org.gradle.kotlin.dsl.coreLibraryDesugaring

plugins {
    id("org.jetbrains.kotlin.android")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val exoOnly = false

val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4
)

android {
    namespace = "com.yuroyami.syncplay"
    compileSdk = 35

    ndkVersion = "26.3.11579264"

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

    packaging {
        //jniLibs.useLegacyPackaging = true
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/versions/9/previous-compilation-data.bin"
            pickFirsts += "META-INF/io.netty.versions.properties"
        }
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

    buildFeatures {
        buildConfig = true
        viewBinding = true //I prefer using viewbinding to quickly inflate player XML views.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    if (!exoOnly) {
        splits {
            abi {
                isEnable = true
                reset()
                abiCodes.forEach { (abi, _) ->
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
    implementation(projects.shared)
    implementation(files("libs/ext.aar")) /* ExoPlayer's FFmpeg extension  */
    coreLibraryDesugaring ("com.android.tools:desugar_jdk_libs:2.1.5")

}

