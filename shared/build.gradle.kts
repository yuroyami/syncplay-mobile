import io.github.yuroyami.kmpssot.kmpSsot

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

// Overridable from the CLI / gradle.properties (-PexoOnly=true); defaults to AppConfig.exoOnly.
// Must match androidApp's resolution so the build logic and BuildConfig agree.
val exoOnly = AppConfig.resolveExoOnly(providers)

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexplicit-backing-fields",
            "-Xexpect-actual-classes",
            "-Xcontext-parameters",
        )
    }

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
        summary = "${kmpSsot.appName.get()} Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/syncplay-mobile"
        version = "1.0.4"
        ios.deploymentTarget = "14.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = kmpSsot.sharedModule.get()
            isStatic = false
        }

        pod("VLCKit", libs.versions.libvlc.ios.get()) //Adds the VLC player engine to iOS
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
            implementation(libs.bundles.compose.unstyled)

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

            /* YouTube/SoundCloud/PeerTube stream URL extractor (no Python, pure JVM) */
            implementation(libs.newpipe.extractor)

            /* Ktor HTTP client engine for Android */
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            /* Ktor HTTP client engine for iOS */
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

/* Pin Skiko to a single explicit version across every configuration in this module.
 *
 * Compose Multiplatform 1.11.0-rc01 ships with skiko 0.144.6 (declared via `strictly`).
 * Coil 3.4.0 declares an older skiko 0.9.22.2. Today Compose's `strictly` constraint
 * wins resolution and everyone ends up on 0.144.6, but the outcome is implicit and a
 * future Coil bump that goes higher than Compose's bundled version would silently swap
 * Skia's native binaries underneath Compose — ABI breaks between Skiko minors do happen,
 * since the bundled libskiko.so/dylib has to match the Kotlin bindings Compose was
 * compiled against. Forcing the version here makes the choice explicit and survives
 * either library's transitive declarations changing.
 *
 * `force` (rather than `strictly` on a constraint) is used because the KMP DSL doesn't
 * expose a `dependencies { constraints { } }` block inside `commonMain.dependencies { }`,
 * and applying force across all configurations covers the per-target classpaths
 * (iosArm64MainCompileKlibraries, androidReleaseRuntimeClasspath, etc.) in one place.
 *
 * Bump `skiko` in libs.versions.toml whenever compose-multiplatform is upgraded — match
 * what Compose actually uses, which you can confirm with
 *     ./gradlew :shared:dependencies | grep skiko
 * after the bump. */
configurations.configureEach {
    resolutionStrategy.force("org.jetbrains.skiko:skiko:${libs.versions.skiko.get()}")
}

// Custom (non-plugin) propagators: Trinity color XML rewrite, logo→imageset copy,
// default-strings fallback. The kmp-ssot plugin handles version/appName/bundleId/
// locales/iOS pbxproj automatically (hooked into iOS framework link tasks).
with(AppConfig) {
    propagateAllCustom()
}

ktorfit {
    // The Ktorfit compiler plugin is built against a specific Kotlin compiler ABI, so this
    // must track the `kotlin` version in libs.versions.toml, NOT the ktorfit lib version.
    // A mismatch crashes compilation with "IrGenerationExtension cannot be cast to
    // ProjectExtensionDescriptor". Map: Kotlin 2.3.x -> 2.3.3, Kotlin 2.4.0+ -> 2.3.5.
    compilerPluginVersion.set("2.3.5")
}

buildConfig {
    buildConfigField("APP_NAME", kmpSsot.appName.get())
    buildConfigField("APP_VERSION", kmpSsot.versionName.get())
    buildConfigField("DEBUG", false)
    buildConfigField("DEBUG_SYNCPLAY_PROTOCOL", false)
    buildConfigField("EXOPLAYER_ONLY", exoOnly)
    buildConfigField("KLIPY_API_KEY", AppConfig.localProperties(rootDir).getProperty("yuroyami.keyKlipyApi"))

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
}//