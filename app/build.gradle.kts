plugins {
    id("com.android.application")
    id("kotlin-android")
}

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
        minSdk = 26
        targetSdk = 32
        versionCode = 1000012000 /* 1 000 012 000 */
        versionName = "0.12.0"
        resourceConfigurations.addAll(setOf("en", "ar", "zh", "fr")) //To use with AppCompatDelegate.setApplicationLocale
        signingConfig = signingConfigs.getByName("github")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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
    packaging {
        pickFirst("META-INF/LICENSE")
        pickFirst("META-INF/*.properties")
        pickFirst("META-INF/AL2.0")
        pickFirst("META-INF/LGPL2.1")
        pickFirst("META-INF/INDEX.LIST")
        pickFirst("META-INF/*")
        pickFirst("META-INF/io.netty.versions.properties")
    }
}

dependencies {
    /* Related to Android APIs and functionality */
    implementation("androidx.core:core-ktx:1.10.1") /* AndroidX core library - Kotlin ver */
    implementation("androidx.appcompat:appcompat:1.7.0-alpha02") /* AndroidX's AppCompat library */
    implementation("androidx.documentfile:documentfile:1.0.1") /* Managing Scoped Storage */
    implementation("androidx.core:core-splashscreen:1.0.1") /* AndroidX's SplashScreen API */
    implementation("com.airbnb.android:lottie-compose:6.0.0") /* Lottie Animation */

    implementation("com.google.code.gson:gson:2.10.1") /* Google's GSON for Json operations */
    implementation("io.netty:netty-all:4.1.92.Final") /* TCP Network Client library */

    implementation("androidx.datastore:datastore-preferences:1.1.0-alpha04") /* Jetpack Datastore */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.1") //1.6.4

    val compose = "1.5.0-beta01"
    implementation("androidx.compose.material:material-icons-core:$compose") //Material3 doesn't have icons (BOM)
    implementation("androidx.compose.material:material-icons-extended:$compose") //More Icons (BOM)
    implementation("androidx.compose.material:material-ripple:$compose") //Ripple effects (BOM)
    //implementation("androidx.compose.runtime:runtime-livedata:$compose") //LiveData compatibility (BOM)

    val material3 = "1.1.0"
    implementation("androidx.compose.material3:material3:$material3") //Material3 + Foundation + UI (core)
    implementation("androidx.compose.material3:material3-window-size-class:$material3") //Window size utils (BOM)
    //implementation("androidx.constraintlayout:constraintlayout-compose:$material3") /* ConstraintLayout */

    /* More compose add-ons */
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.0-alpha10") /* ConstraintLayout */
    //implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.0") //ViewModel for compose
    implementation("androidx.activity:activity-compose:1.7.2") //Activity compatibility
    implementation("com.godaddy.android.colorpicker:compose-color-picker-android:0.7.0")
    implementation("com.google.accompanist:accompanist-flowlayout:0.30.1")

    //implementation("com.google.android.material:material:1.9.0-alpha02")/* Google's MaterialComponents */
    implementation("androidx.preference:preference-ktx:1.2.0")/* AndroidX's Preferences - Kotlin ver */

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
    implementation("androidx.media3:media3-datasource-rtmp:$media3_version")
    implementation("androidx.media3:media3-ui:$media3_version")
    implementation("androidx.media3:media3-ui-leanback:$media3_version")
    implementation("androidx.media3:media3-session:$media3_version")
    implementation("androidx.media3:media3-extractor:$media3_version")
    implementation("androidx.media3:media3-cast:$media3_version")
    implementation("androidx.media3:media3-exoplayer-workmanager:$media3_version")
    implementation("androidx.media3:media3-transformer:$media3_version")
    //api("androidx.media3:media3-test-utils:$media3_version")
    //api("androidx.media3:media3-test-utils-robolectric:$media3_version")
    //api("androidx.media3:media3-database:$media3_version")
    implementation("androidx.media3:media3-decoder:$media3_version")
    implementation("androidx.media3:media3-datasource:$media3_version")
    implementation("androidx.media3:media3-common:$media3_version")

    /** Unnecessary-for-functionality Libraries */
    //implementation 'org.conscrypt:conscrypt-android:2.5.2' //Will use for TLSv1.3

    /* Related to miscellaneous functionality */
    //implementation 'com.blankj:utilcodex:1.31.1' //Bunch of useful Android utils


    /** Debugging tools */
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.11")

}