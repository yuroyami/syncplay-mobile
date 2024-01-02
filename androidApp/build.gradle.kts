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
        versionCode = 101300 //Changing versionName semantic projection from 1.XXX.XXX.XXX to 1.XX.XX.XX
        versionName = "0.13.0"
//        resourceConfigurations.addAll(setOf(
//            //To use with AppCompatDelegate.setApplicationLocale
//            "ar", "de", "en", "es", "fr", "hi", "it", "ja", "ko", "pt", "ru", "tr", "zh"
//        ))
        signingConfig = signingConfigs.getByName("github")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    packaging {
        //jniLibs.useLegacyPackaging = true
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/versions/9/previous-compilation-data.bin"
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".new"
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true //I prefer using viewbinding to quickly inflate ExoPlayer and MPV XML views.
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

//            tasks.register("stripNativeLibs", Exec::class) {
//                /*commandLine("arm64-linux-android-strip")
//                workingDir(file("$projectDir/src/main/jniLibs"))
//                args("-r", "-u", "*.so")*/
//
//                val stripToolMap: Map<String, String> = mapOf(
//                    "armeabi-v7a" to "arm-linux-androideabi-strip",
//                    "arm64-v8a" to "aarch64-linux-android-strip",
//                    "x86" to "i686-linux-android-strip",
//                    "x86_64" to "x86_64-linux-android-strip"
//                )
//
//                // Set the working directory where the task will be executed
//                workingDir(file("$projectDir/src/main/jniLibs"))
//
//                // Iterate over each target architecture and create a strip command for it
//                abiCodes.keys.forEach { abi ->
//                    val stripTool: String? = stripToolMap[abi]
//                    if (stripTool != null) {
//                        val stripCommand = project.exec {
//                            // Set the command to the appropriate strip tool for the current target architecture
//                            commandLine(stripTool)
//
//                            // Add arguments to specify the native library files to strip
//                            args("-r", "-u", "$abi/*.so")
//                        }
//
//                        // Execute the strip command
//                        dependsOn(stripCommand)
//                    }
//                }
//            }
        }

        create("noLibs") {
            dimension = "engine"

            /*
            ndk {
                abiFilters.clear()
            }

            splits {
                abi {
                    isEnable = false
                }
            }

             */


            packaging {
                jniLibs {
                    if (false) {
                        excludes += ("**/libavcodec.so")
                        excludes += ("**/libavdevice.so")
                        excludes += ("**/libavfilter.so")
                        excludes += ("**/libavformat.so")
                        excludes += ("**/libavutil.so")
                        //jniLibs.excludes.add("**/libc++_shared.so")
                        excludes += ("**/libmpv.so")
                        excludes += ("**/libplayer.so")
                        excludes += ("**/libpostproc.so")
                        excludes += ("**/libswresample.so")
                        excludes += ("**/libswscale.so")
                    }
                }
            }
        }
    }
}

dependencies {
    implementation(projects.shared)

    implementation(files("libs/ext.aar")) /* ExoPlayer's FFmpeg extension  */
}

