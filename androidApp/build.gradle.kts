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
}

dependencies {
    implementation(projects.shared)

    implementation(files("libs/ext.aar")) /* ExoPlayer's FFmpeg extension  */
}