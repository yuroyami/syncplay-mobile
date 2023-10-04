plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    id("org.jetbrains.compose")
}

android {
    namespace = "com.yuroyami.syncplay"
    compileSdk = 34

    //sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

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
        resourceConfigurations.addAll(setOf("en", "ar", "zh", "fr")) //To use with AppCompatDelegate.setApplicationLocale
        signingConfig = signingConfigs.getByName("github")
        //proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".new"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/io.netty.versions.properties"
        }
    }

    val abiCodes = mapOf(
        "armeabi-v7a" to 1,
        "arm64-v8a" to 2,
        "x86" to 3,
        "x86_64" to 4
    )

    flavorDimensions.add("engine")

    productFlavors {
        create("withLibs") {
            dimension = "engine"

            splits {
                abi {
                    isEnable = true
                    reset()
                    abiCodes.forEach { (abi, _) ->
                        if (file("$projectDir/src/main/jniLibs/$abi").exists())
                            include(abi)
                    }
                    isUniversalApk = true
                }
            }

            tasks.register("stripNativeLibs", Exec::class) {
                val stripToolMap: Map<String, String> = mapOf(
                    "armeabi-v7a" to "arm-linux-androideabi-strip",
                    "arm64-v8a" to "aarch64-linux-android-strip",
                    "x86" to "i686-linux-android-strip",
                    "x86_64" to "x86_64-linux-android-strip"
                )
                workingDir(file("$projectDir/src/main/jniLibs"))
                abiCodes.keys.forEach { abi ->
                    val stripTool: String? = stripToolMap[abi]
                    if (stripTool != null) {
                        val stripCommand = project.exec {
                            commandLine(stripTool)
                            args("-r", "-u", "$abi/*.so")
                        }
                        dependsOn(stripCommand)
                    }
                }
            }
        }
        create("noLibs") {
            dimension = "engine"
            ndk {
                abiFilters.clear()
            }
            splits {
                abi {
                    isEnable = false
                }
            }

            packaging {
                jniLibs.excludes.add("**/libavcodec.so")
                jniLibs.excludes.add("**/libavdevice.so")
                jniLibs.excludes.add("**/libavfilter.so")
                jniLibs.excludes.add("**/libavformat.so")
                jniLibs.excludes.add("**/libavutil.so")
                jniLibs.excludes.add("**/libc++_shared.so")
                jniLibs.excludes.add("**/libmpv.so")
                jniLibs.excludes.add("**/libplayer.so")
                jniLibs.excludes.add("**/libpostproc.so")
                jniLibs.excludes.add("**/libswresample.so")
                jniLibs.excludes.add("**/libswscale.so")
                jniLibs.excludes.add("**/**.so")
            }
        }
    }
}

dependencies {
    implementation(projects.shared)

    implementation(files("libs/ext.aar")) /* ExoPlayer's FFmpeg extension  */
}