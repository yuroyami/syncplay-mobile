plugins {
    id("com.android.application")
    id("kotlin-android")
}

val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4
)

android {

    compileSdk = 33
    namespace = "app"

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
        minSdk = 23
        targetSdk = 32
        versionCode = 1000012000 /* 1 000 012 000 */
        versionName = "0.12.0"
        resourceConfigurations.addAll(setOf("en", "ar", "zh", "fr")) //To use with AppCompatDelegate.setApplicationLocale
        signingConfig = signingConfigs.getByName("github")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.7"
    }

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

    packaging {
        pickFirst("META-INF/INDEX.LIST")
        pickFirst("META-INF/io.netty.versions.properties")
    }

    tasks.register("stripNativeLibs", Exec::class) {
        /*commandLine("arm64-linux-android-strip")
        workingDir(file("$projectDir/src/main/jniLibs"))
        args("-r", "-u", "*.so")*/

        val stripToolMap: Map<String, String> = mapOf(
            "armeabi-v7a" to "arm-linux-androideabi-strip",
            "arm64-v8a" to "aarch64-linux-android-strip",
            "x86" to "i686-linux-android-strip",
            "x86_64" to "x86_64-linux-android-strip"
        )

        // Set the working directory where the task will be executed
        workingDir(file("$projectDir/src/main/jniLibs"))

        // Iterate over each target architecture and create a strip command for it
        abiCodes.keys.forEach { abi ->
            val stripTool: String? = stripToolMap[abi]
            if (stripTool != null) {
                val stripCommand = project.exec {
                    // Set the command to the appropriate strip tool for the current target architecture
                    commandLine(stripTool)

                    // Add arguments to specify the native library files to strip
                    args("-r", "-u", "$abi/*.so")
                }

                // Execute the strip command
                dependsOn(stripCommand)
            }
        }
    }

}

dependencies {
    /* Related to Android APIs and functionality */
    implementation("androidx.core:core-ktx:1.10.1")

    implementation("androidx.appcompat:appcompat:1.7.0-alpha02") {
        exclude(group = "androidx.core", module = "core")
    }

    implementation("androidx.documentfile:documentfile:1.0.1") /* Managing Scoped Storage */

    /* AndroidX's SplashScreen API */
    implementation("androidx.core:core-splashscreen:1.0.1")  {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

    /* Lottie Animation */
    implementation("com.airbnb.android:lottie-compose:6.0.0") {
        exclude(group ="androidx.compose.foundation", module =  "foundation")
        exclude(group ="androidx.compose.ui", module =  "ui")
    }

    implementation("com.google.code.gson:gson:2.10.1")

    implementation("io.netty:netty-all:4.1.92.Final") /* TCP Network Client library */

    implementation("androidx.core:core-google-shortcuts:1.1.0") {
        exclude(group = "androidx.core", module = "core")
        exclude(group = "com.google.crypto.tink", module = "tink-android")
    }

    implementation("androidx.datastore:datastore-preferences:1.1.0-alpha04") {/* Jetpack Datastore */
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    }

    val compose = "1.5.0-beta01"
    implementation("androidx.compose.material:material-icons-core:$compose") //Material3 doesn't have icons (BOM)
    implementation("androidx.compose.material:material-icons-extended:$compose") //More Icons (BOM)

    val material3 = "1.1.0"
    implementation("androidx.compose.material3:material3:$material3") //Material3 + Foundation + UI (core)
    //implementation("androidx.compose.material3:material3-window-size-class:$material3") //Window size utils (BOM)
    //implementation("androidx.constraintlayout:constraintlayout-compose:$material3") /* ConstraintLayout */

    /* More compose add-ons */
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.0-alpha10")
    implementation("androidx.activity:activity-compose:1.7.2") {
        exclude(group ="androidx.compose.ui", module =  "ui")
    }
    implementation("com.godaddy.android.colorpicker:compose-color-picker-android:0.7.0")
    implementation("com.google.accompanist:accompanist-flowlayout:0.30.1")

    //implementation("com.google.android.material:material:1.9.0-alpha02")/* Google's MaterialComponents */
    implementation("androidx.preference:preference-ktx:1.2.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "androidx.core", module = "core")
    }

    /* Media3 (ExoPlayer + MediaSession etc) */
    val media3_version = "1.0.2"
    implementation(files("libs/ext.aar")) /* ExoPlayer's FFmpeg extension  */
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3_version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3_version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3_version")
    //api("androidx.media3:media3-exoplayer-ima:$media3_version")
    //api("androidx.media3:media3-datasource-cronet:$media3_version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3_version")
    //implementation("androidx.media3:media3-datasource-rtmp:$media3_version")
    implementation("androidx.media3:media3-ui:$media3_version")
    //implementation("androidx.media3:media3-ui-leanback:$media3_version")
    implementation("androidx.media3:media3-session:$media3_version")
    implementation("androidx.media3:media3-extractor:$media3_version")
    //implementation("androidx.media3:media3-cast:$media3_version")
    //implementation("androidx.media3:media3-exoplayer-workmanager:$media3_version")
    //implementation("androidx.media3:media3-transformer:$media3_version")
    //api("androidx.media3:media3-test-utils:$media3_version")
    //api("androidx.media3:media3-test-utils-robolectric:$media3_version")
    //api("androidx.media3:media3-database:$media3_version")
    implementation("androidx.media3:media3-decoder:$media3_version")
    implementation("androidx.media3:media3-datasource:$media3_version")
    implementation("androidx.media3:media3-common:$media3_version")

    /** Unnecessary-for-functionality Libraries */
    //implementation 'org.conscrypt:conscrypt-android:2.5.2' //Will use for TLSv1.3
}