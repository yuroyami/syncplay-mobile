plugins {
    id("org.jetbrains.kotlin.android")
    id("com.android.application")
    id("org.jetbrains.compose")
}

val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4
)

android {
    namespace = "com.yuroyami.syncplay"
    compileSdk = 34

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
        minSdk = (findProperty("android.minSdk") as String).toInt()
        targetSdk = (findProperty("android.targetSdk") as String).toInt()
        versionCode = 1000014000 //Changing versionName semantic projection from 1.XXX.XXX.XXX to 1.XX.XX.XX
        versionName = "0.14.0"
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    buildTypes {
        release {
            isMinifyEnabled = true

            postprocessing {
                isRemoveUnusedResources = true
            }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    flavorDimensions.add("engine")

    productFlavors {
        create("withLibs") {
            dimension = "engine"

            splits {
                abi {
                    isEnable = false
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
        }

        create("noLibs") {
            dimension = "engine"
        }

        packaging {
            jniLibs {
                if (false) {
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
    }
}

dependencies {
    implementation(projects.shared)

    implementation(files("libs/ext.aar")) /* ExoPlayer's FFmpeg extension  */
}

