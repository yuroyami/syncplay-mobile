import com.yuroyami.kmpssot.KmpSsotExtension

val ssot = rootProject.extensions.getByType(KmpSsotExtension::class.java)

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
    alias(libs.plugins.ktorfit)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "app"
        compileSdk { version = release(providers.gradleProperty("android.compileSdk").get().toInt()) }
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()
        androidResources { enable = true }
    }

    // Activating iOS targets (iosMain)
    listOf(
        iosSimulatorArm64(), //We enable this only if we're planning to test on a simulator
        iosArm64()
    ).forEach {
        it.compilations.getByName("main") {
            @Suppress("unused") val nsKVO by cinterops.creating {
                defFile("src/nativeInterop/cinterop/NSKeyValueObserving.def")
            }
            @Suppress("unused") val ifaddrsInterop by cinterops.creating {
                defFile("src/nativeInterop/cinterop/ifaddrs.def")
            }
        }
    }

    // iOS configuration
    cocoapods {
        summary = "${ssot.appName.get()} Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/syncplay-mobile"
        version = "1.0.4"
        ios.deploymentTarget = "14.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = ssot.sharedModule.get()
            isStatic = false
        }

        pod("MobileVLCKit", libs.versions.libvlc.ios.get()) //Adds the VLC player engine to iOS
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
                @Suppress("DEPRECATION") run {
                    enableLanguageFeature("ExplicitBackingFields") //same as -Xexplicit-backing-fields compiler flag
                    enableLanguageFeature("NestedTypeAliases") //-Xnested-type-aliases
                    enableLanguageFeature("ExpectActualClasses") //-Xexpect-actual-classes
                    enableLanguageFeature("ContextParameters") //Xcontext-parameters
                }
            }
        }

        commonMain.dependencies {
            /* Forcing Kotlin libs to match the compiler */
            implementation(libs.kotlin.stdlib)

            /* Explicitly specifying a newer coroutines version */
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

            /* Coil for async image loading (GIF panel) */
            implementation(libs.bundles.coil)

            /* Ktor HTTP client for REST API calls (Klipy GIF API) */
            implementation(libs.bundles.ktor.client)

            implementation(libs.ktorfit)
        }

        androidMain.dependencies {
            /* Coil GIF decoder for animated GIF support on Android */
            implementation(libs.coil.gif)

            /* Backward compatibility APIs from Google's Jetpack AndroidX */
            /* Contains AndroidX Libs: Core (+CoreSplashScreen +CorePiP), AppCompat, Activity Compose, DocumentFile */
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

            /* Ktor HTTP client engine for Android */
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            /* Ktor HTTP client engine for iOS */
            implementation(libs.ktor.client.darwin)
        }
    }
}

// Custom (non-plugin) propagators: Trinity color XML rewrite, logo→imageset copy,
// default-strings fallback. The kmp-ssot plugin handles version/appName/bundleId/
// locales/iOS pbxproj automatically (hooked into iOS framework link tasks).
with(AppConfig) {
    propagateAllCustom()
}

ktorfit {
    compilerPluginVersion.set("2.3.3")
}

buildConfig {
    buildConfigField("APP_NAME", ssot.appName.get())
    buildConfigField("APP_VERSION", ssot.versionName.get())
    buildConfigField("DEBUG", false)
    buildConfigField("DEBUG_SYNCPLAY_PROTOCOL", false)
    buildConfigField("EXOPLAYER_ONLY", AppConfig.exoOnly)
    buildConfigField("KLIPY_API_KEY", AppConfig.localProperties.getProperty("yuroyami.keyKlipyApi"))

    /* Trinity brand colors — exposed so Theming.kt reads them from the SSOT */
    buildConfigField("TRINITY_COLOR_1", AppConfig.TRINITY_1)
    buildConfigField("TRINITY_COLOR_2", AppConfig.TRINITY_2)
    buildConfigField("TRINITY_COLOR_3", AppConfig.TRINITY_3)
}

tasks.register("propagateSSOT") {
    group = "syncplay"
    description = "Run custom non-plugin propagators (Trinity colors, logo imageset, default-strings fallback)."
    doLast {
        with(AppConfig) {
            project.propagateAllCustom()
        }
    }
}