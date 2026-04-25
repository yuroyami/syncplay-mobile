import AppConfig.exoOnly
import NativeBuildConfig.registerNativeBuildTask
import NativeBuildConfig.validateNdk
import io.github.yuroyami.kmpssot.kmpSsot

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
}

val ndkRequired = providers.gradleProperty("android.ndkVersion").get()

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "androidApp"
    compileSdk = providers.gradleProperty("android.compileSdk").get().toInt()

    ndkVersion = ndkRequired

    signingConfigs {
        file("${rootDir}/keystore/syncplaykey.jks").takeIf { it.exists() }?.let { keystoreFile ->
            create("synkplay_keystore") {
                storeFile = keystoreFile
                AppConfig.localProperties.apply {
                    keyAlias = getProperty("keystore.keyAlias")
                    keyPassword = getProperty("keystore.keyPassword")
                    storePassword = getProperty("keystore.storePassword")
                }
            }
        }
    }

    defaultConfig {
        // applicationId / versionCode / versionName / manifestPlaceholders[appName] are
        // set eagerly by kmp-ssot plugin. The exoOnly override below runs in this block
        // (which evaluates AFTER plugins.withId fires), so it wins for the exoOnly case.
        if (exoOnly) applicationId = "com.reddnek.syncplay"
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("android.targetSdk").get().toInt()

        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        signingConfigs.findByName("synkplay_keystore")?.let { config ->
            signingConfig = config
        }
    }

    compileOptions {
        // sourceCompatibility / targetCompatibility set by kmp-ssot from javaVersion (default 21).
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = exoOnly //TODO Fix minified build when mpv and libVLC are included
        }
        debug {
            //applicationIdSuffix = ".dev"
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/versions/9/previous-compilation-data.bin"
            pickFirsts += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/license/**"
            excludes += "META-INF/native-image/**"

        }
    }

    packaging {
        jniLibs {
            pickFirsts += "**/libc++_shared.so" //Pick our local c++_shared only and not the one in VLC aar
        }
    }

    if (exoOnly) {
        packaging {
            jniLibs {
                //mpv libs
                for (mpvLib in AppConfig.mpvLibs) {
                    excludes += ("**/$mpvLib")
                }

                //vlc
                excludes += ("**/libvlc.so")
            }
        }
    } else {
        splits {
            abi {
                isEnable = true
                reset()
                for (abi in AppConfig.abiCodes) {
                    include(abi.key)
                }
                isUniversalApk = true
            }
        }
    }

    flavorDimensions.add("flavor")
    productFlavors {
        create(if (exoOnly) "exoOnly" else "full") {
            dimension = "flavor"
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs & Android App Bundles.
        includeInApk = false
        includeInBundle = false
    }

    // This block strips out odd, unused artifacts that the google-shortcuts library brings along,
    // none of which are needed for its main features.
    // This will remove them also from any other library that might use them
    configurations.all {
        exclude(group = "com.google.crypto.tink", module = "tink-android")
        exclude(group = "com.google.android.gms")
    }
}

if (!exoOnly) {
    afterEvaluate {
        validateNdk(androidComponents.sdkComponents.ndkDirectory.get().asFile, ndkRequired)
    }
    registerNativeBuildTask(
        sdkPathProvider = { androidComponents.sdkComponents.sdkDirectory.get().asFile },
        ndkPathProvider = { androidComponents.sdkComponents.ndkDirectory.get().asFile }
    )
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val abiFilter = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
                val v = kmpSsot.versionName.get()
                val name = kmpSsot.appName.get()
                val fileName = if (exoOnly) {
                    "syncplay-$v-exo-only.apk"
                } else {
                    val abiName = abiFilter ?: "universal"
                    "${name.lowercase()}-$v-full-${abiName}.apk"
                }
                output.outputFileName = fileName
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)
    implementation(projects.shared)
}

if (!exoOnly) {
    tasks.named("preBuild") {
        dependsOn("runAndroidMpvNativeBuildScripts")
    }
}

