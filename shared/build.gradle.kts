import io.github.yuroyami.kiteconfig.kiteConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kSerialization)
    alias(libs.plugins.ksp)
    //alias(libs.plugins.touchlab.skie)
    alias(libs.plugins.ktorfit)
}

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
        compileSdk { version = release(kiteConfig.compileSdk.get()) }
        minSdk = kiteConfig.minSdk.get()
        androidResources { enable = true }
    }

    // Desktop (JVM) target — Windows/macOS/Linux via Compose for Desktop, hosted by :desktopApp.
    jvm("desktop")

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
        summary = "${kiteConfig.appName.get()} Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/syncplay-mobile"
        version = "1.0.4"
        ios.deploymentTarget = "14.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = AppConfig.SHARED_MODULE_NAME
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

            /* Haze: backdrop blur for the glass popup/chrome surfaces. Only samples pixels that
             * Compose itself draws, so it blurs the whole UI everywhere but reaches video only on
             * KitePlayer's Compose-canvas path (see GlassSurface.kt). */
            implementation(libs.bundles.haze)

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

            /* KitePlayerVideo, the runtime-choice layer: one coordinate re-exports both
             * rendering products plus KitePlayer's default assembly, facade and core API.
             * The in-room renderer toggle rides its path parameter. */
            implementation(libs.kiteplayer.compose.ui)

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

            /* YouTube/SoundCloud/PeerTube stream URL extractor (no Python, pure JVM) */
            implementation(libs.newpipe.extractor)

            /* Ktor HTTP client engine for Android */
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            /* Ktor HTTP client engine for iOS */
            implementation(libs.ktor.client.darwin)
        }

        val desktopMain by getting {
            dependencies {
                /* Network and TLS: same Netty engine as Android (pure JVM); TLS comes from the JDK,
                 * no Conscrypt needed on desktop. */
                implementation(libs.netty)


                /* YouTube/SoundCloud/PeerTube stream URL extractor (pure JVM, same as Android) */
                implementation(libs.newpipe.extractor)

                /* Ktor HTTP client engine for desktop (also backs Coil's network fetcher) */
                implementation(libs.ktor.client.okhttp)

                /* Swing interop + desktop-specific Compose APIs */
                implementation(compose.desktop.common)

                /* Registers Dispatchers.Main on the AWT event thread — the whole codebase
                 * dispatches on Dispatchers.Main(.immediate), which has no default on JVM. */
                implementation(libs.kotlin.coroutines.swing)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }

        val desktopTest by getting {
            dependencies {
                /* Headless Compose rendering (ImageComposeScene) needs the host's Skiko native
                 * binary, which compose.desktop.common does not carry. Test-only. */
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

/* Skiko's native binary must match what Compose was compiled against — a transitive bump
 * would silently swap Skia under Compose. Bump `skiko` in libs.versions.toml together with
 * compose-multiplatform. Full rationale: CLAUDE.md "Key Dependencies". */
configurations.configureEach {
    resolutionStrategy.force("org.jetbrains.skiko:skiko:${libs.versions.skiko.get()}")
}

// Custom (non-plugin) propagators: Trinity color XML rewrite + default-strings fallback.
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

tasks.register("propagateSSOT") {
    group = "syncplay"
    description = "Run custom non-plugin propagators (Trinity colors, logo imageset, default-strings fallback)."
    doLast {
        with(AppConfig) {
            project.propagateAllCustom()
        }
    }
}
