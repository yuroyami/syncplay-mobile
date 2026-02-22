plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kSerialization)
    alias(libs.plugins.ksp)
    //alias(libs.plugins.touchlab.skie)
    alias(libs.plugins.buildConfig)
}

kotlin {
    jvmToolchain(AppConfig.javaVersion)

    android {
        namespace = "com.yuroyami.syncplay"
        compileSdk = AppConfig.compileSdk
        minSdk = AppConfig.minSdk
        androidResources { enable = true }
    }

    // Activating iOS targets (iosMain)
    iosArm64().also {
        it.compilations.getByName("main") {
            @Suppress("unused") val nsKVO by cinterops.creating {
                defFile("src/nativeInterop/cinterop/NSKeyValueObserving.def")
            }
        }
    }

    // iOS configuration
    cocoapods {
        summary = "Syncplay Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/syncplay-mobile"
        version = "1.0.2"
        ios.deploymentTarget = "14.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            isStatic = true
        }

        //pod("SPLPing", libs.versions.splping.get()) //Light-weight Objective-C library to add the ICMP ping functionality
        //pod("MobileVLCKit", libs.versions.libvlc.ios.get()) //Adds the VLC player engine to iOS
    }

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                optIn("kotlin.RequiresOptIn")
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("kotlin.uuid.ExperimentalUuidApi")
                optIn("kotlin.ExperimentalUnsignedTypes")
                optIn("kotlin.ExperimentalStdlibApi")
                optIn("kotlin.io.encoding.ExperimentalEncodingApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
                optIn("kotlinx.cinterop.ExperimentalForeignApi") //for iOS
                optIn("kotlinx.cinterop.BetaInteropApi") //for iOS
                optIn("kotlin.time.ExperimentalTime")
                enableLanguageFeature("ExplicitBackingFields") //same as -Xexplicit-baxcking-fields compiler flag
                enableLanguageFeature("NestedTypeAliases") //-Xnested-type-aliases
                enableLanguageFeature("ExpectActualClasses") //-Xexpect-actual-classes
                enableLanguageFeature("ContextParameters") //Xcontext-parameters
            }
        }

        commonMain.dependencies {
            /* Forcing Kotlin libs to match the compiler */
            implementation(libs.kotlin.stdlib)

            /* Explicitly specifying a newer koroutines version */
            implementation(libs.kotlin.coroutines.core)

            /* Official JetBrains Kotlin Date 'n time manager (i.e: generating date from epoch) */
            implementation(libs.kotlinx.datetime)

            /* JSON serializer/deserializer to communicate with Syncplay servers */
            implementation(libs.kotlinx.serialization.json)

            /* Network client */
            implementation(libs.bundles.ktor)

            /* Android's "Uri" class but rewritten for Kotlin multiplatform */
            implementation(libs.uriKmp)

            /* Jetpack Datastore for preferences and settings (accessible in Compose in real-time) */
            implementation(libs.datastore)

            /* Compose core dependencies */
            implementation(libs.bundles.compose.multiplatform)
            /*implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)*/

            /* ViewModel support */
            implementation(libs.compose.viewmodel)

            /* Navigation support with the modern nav3 library */
            implementation(libs.bundles.navigation3)

            /* ComposableHorizons' unstyled composables for more granularly-controlled components */
            implementation(libs.compose.unstyled)

            /* MaterialKolor generates Material3 themes from seed colors */
            implementation(libs.materialKolor)

            /* Helps with color calculations for color preferences */
            implementation(libs.kolorpicker)

            /* Hash digesters */
            implementation(libs.bundles.krypto)

            /* Logging */
            implementation(libs.logging.kermit)

            /* File opener/saver multiplatform */
            implementation(libs.filekit)

            /* Atomics (used only for logs at the moment) */
            implementation(libs.atomicfu)
        }

        androidMain.dependencies {
            /* Backward compatibility APIs from Google's Jetpack AndroidX */
            /* Contains AndroidX Libs: Core (+Core-SplashScreen, Core-PiP, Core-Google-Shortcuts), AppCompat, Activity Compose, DocumentFile */
            implementation(libs.bundles.jetpack.androidx.extensions)

            /* Extended coroutine support for Android threading */
            implementation(libs.kotlin.coroutines.android)
            implementation(libs.kotlin.coroutines.guava)

            /* Network and TLS */
            implementation(libs.netty)
            implementation(libs.conscrypt) //TLSv1.3 with backward compatibility

            /* Video player engine: Media3 (ExoPlayer and its extensions) */
            implementation(libs.bundles.media3)

            /* ExoPlayer's FFmpeg-powered audio renderer extension (this does not need to be updated with every media3 release)  */
            implementation(files(File(projectDir, "libs/libffmpeg_media3exo_1.8.0.aar")))

            /* Video player engine: VLC (via libVLC) */
            implementation(libs.libvlc.android)
        }

        iosMain.dependencies {
            //Nothing for the moment
        }
    }
}

buildConfig {
    buildConfigField("APP_VERSION", AppConfig.versionName)
    buildConfigField("DEBUG", true)
    buildConfigField("DEBUG_SYNCPLAY_PROTOCOL", true)
    buildConfigField("EXOPLAYER_ONLY", AppConfig.exoOnly)
}