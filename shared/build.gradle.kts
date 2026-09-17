import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import io.github.yuroyami.kiteconfig.kiteConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kSerialization)
    // Required by the Ktorfit plugin, which registers its code generator as a KSP processor.
    alias(libs.plugins.ksp)
    //alias(libs.plugins.touchlab.skie)
    alias(libs.plugins.ktorfit)
    // Coverage, measured on the desktop test run; the report is configured in the root build.
    alias(libs.plugins.kover)
}

// KiteConfig exposes only the major SDK; keep AGP on the required 37.2 API.
extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
    finalizeDsl { dsl ->
        dsl.compileSdk {
            version = release(kiteConfig.compileSdk.get()) {
                minorApiLevel = providers.gradleProperty("android.compileSdkMinor").get().toInt()
            }
        }
    }
}

// CocoaPods records the custom header path in its generated definition, but does not
// track that header's contents. Regenerate both bindings when the native bridge changes.
tasks.matching { it.name.startsWith("cinteropVLCKit") }.configureEach {
    inputs.file(layout.projectDirectory.file("src/nativeInterop/cinterop/VlcClock.h"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexplicit-backing-fields",
            "-Xexpect-actual-classes",
            "-Xcontext-parameters",
        )
    }

    android {
        namespace = "app"
        androidResources { enable = true }

        // This module holds essentially all the code, and produced no lint task at all, which is
        // also what made the repo's lint.xml suppression apply to nothing.
        lint {
            xmlReport = true
            htmlReport = true
            abortOnError = false
            checkDependencies = false
        }

        // commonTest compiled but never ran against the Android actuals. It does now.
        withHostTest { }
    }

    // Desktop (JVM) target — Windows/macOS/Linux via Compose for Desktop, hosted by :desktopApp.
    jvm("desktop")

    /* Web (browser), through Kotlin/Wasm; the app shell is :webApp.
     *
     * Compose Multiplatform's web target is Beta while the other three are stable, so treat a
     * failure here as the target's, not the app's. Two things a browser genuinely cannot do are
     * kept out of the way rather than worked around: it has no raw TCP socket, so the Syncplay
     * protocol needs a WebSocket transport, and it has no native decoder, so the four existing
     * engines do not exist here. Everything that depends on either lives in nonWebMain. */
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // Activating iOS targets (iosMain)
    listOf(
        iosSimulatorArm64(), //We enable this only if we're planning to test on a simulator
        iosArm64()
    ).forEach {
        val podSdk = if (it.name == "iosArm64") "iphoneos" else "iphonesimulator"
        it.compilations.getByName("main") {
            @Suppress("unused") val nsKVO by cinterops.creating {
                defFile("src/nativeInterop/cinterop/NSKeyValueObserving.def")
            }
            @Suppress("unused") val ifaddrsInterop by cinterops.creating {
                defFile("src/nativeInterop/cinterop/ifaddrs.def")
            }
            cinterops.configureEach {
                if (name == "VLCKit") {
                    // libVLC's C headers use <vlc/...> and the bridge uses <VLCMediaPlayer.h>.
                    // Resolve both from the same framework copied by this target's podBuild task.
                    compilerOpts("-I${project.file("build/cocoapods/synthetic/ios/build/Debug-$podSdk/XCFrameworkIntermediates/VLCKit/VLCKit.framework/Headers")}")
                }
            }
        }
    }

    // iOS configuration
    cocoapods {
        summary = "${kiteConfig.appName.get()} Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/syncplay-mobile"
        version = "1.0.4"
        ios.deploymentTarget = "14.1"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = AppConfig.SHARED_MODULE_NAME
            isStatic = false
        }

        pod("VLCKit") {
            version = libs.versions.libvlc.ios.get()
            // Keep the live clock bridge in this binding so it uses the exact bundled headers.
            headers = project.file("src/nativeInterop/cinterop/VlcClock.h").absolutePath
        }
    }

    /* Declaring a dependsOn edge by hand switches the default hierarchy template off, which
     * silently detaches iosMain from the tree and loses every iOS actual. Asking for it
     * explicitly keeps both. */
    applyDefaultHierarchyTemplate()

    sourceSets {
        /**
         * Everything except the browser: Android, iOS and desktop.
         *
         * Common code is what all four targets can run. This is what the other three can run and
         * the web cannot, which is a real category, not a dumping ground: a TCP socket, a native
         * player engine, a filesystem, a thread that may block. Adding the web target is what
         * made the distinction necessary. Before it, "not common" and "one platform" were the
         * only two options.
         *
         * Ask one question before putting something here: would this compile in a browser? If it
         * would, it belongs in commonMain.
         */
        val nonWebMain by creating { dependsOn(commonMain.get()) }

        /**
         * The JVM platforms' shared source set.
         *
         * Android and desktop are both the JVM and both run Netty and NewPipe, so code that is
         * genuinely identical between them lived as two files that had to be edited twice. Every
         * Netty fix in the ledger landed twice for exactly this reason.
         *
         * Only put something here when it is the same on both. Anything that reads SAF, a
         * Context, Conscrypt or a security scope is Android's alone and stays there.
         */
        val jvmShared by creating { dependsOn(nonWebMain) }

        /* Where the Lyricist processor writes Strings.kt and the per-locale objects. */
        commonMain.get().kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        androidMain.get().dependsOn(jvmShared)
        getByName("desktopMain").dependsOn(jvmShared)
        iosMain.get().dependsOn(nonWebMain)

        /* The test side mirrors the main side: a test that blocks a thread cannot run in a
         * browser, so it lives here rather than in commonTest. Coverage is unaffected, the
         * desktop run still executes all of it. */
        val nonWebTest by creating { dependsOn(commonTest.get()) }
        listOf("desktopTest", "iosTest", "androidHostTest").forEach { name ->
            findByName(name)?.dependsOn(nonWebTest)
        }

        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                optIn("kotlin.RequiresOptIn")
                optIn("kotlin.uuid.ExperimentalUuidApi")
                optIn("kotlin.ExperimentalUnsignedTypes")
                optIn("kotlin.ExperimentalStdlibApi")
                optIn("kotlin.io.encoding.ExperimentalEncodingApi")
                optIn("kotlin.time.ExperimentalTime")
            }
        }

        commonMain.dependencies {
            /* The @Preview annotation for the drawn control set. Annotation only: the IDE
             * supplies the renderer, so this adds nothing to a shipped build. */
            implementation(compose.components.uiToolingPreview)

            /* Forcing Kotlin libs to match the compiler */
            implementation(libs.kotlin.stdlib)

            /* Explicitly specifying a newer coroutines version */
            implementation(libs.kotlin.coroutines.core)

            /* Official JetBrains Kotlin Date 'n time manager (i.e: generating date from epoch) */
            implementation(libs.kotlinx.datetime)

            /* Holds the display language in Compose state, so switching it recomposes the app
             * instead of restarting it. The strings themselves are generated below. */
            implementation(libs.lyricist)

            /* JSON serializer/deserializer to communicate with Syncplay servers */
            implementation(libs.kotlinx.serialization.json)

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

            /* Coil for async image loading (GIF panel) */
            implementation(libs.bundles.coil)

            /* Ktor HTTP client for REST API calls (Klipy GIF API) */
            implementation(libs.bundles.ktor.client)

            implementation(libs.ktorfit)
        }

        nonWebMain.dependencies {
            /* Ktor raw TCP sockets, for the Ktor client transport and the iOS server engine.
             * The artifact does publish a web build, but its sockets are Node's, not a
             * browser's, so this stays out of commonMain. */
            implementation(libs.bundles.ktor)

            /* KitePlayerVideo, the runtime-choice layer: one coordinate re-exports both
             * rendering products plus KitePlayer's default assembly, facade and core API.
             * The in-room renderer toggle rides its path parameter. Its decoder is FFmpeg
             * through JNI and cinterop, so there is no web build of it to depend on. */
            implementation(libs.kiteplayer.compose)
            implementation(libs.kiteplayer.audioviz)
        }

        getByName("wasmJsMain").dependencies {
            /* The browser's own fetch(), behind the same Ktor client the app already uses. */
            implementation(libs.ktor.client.js)

            /* window, document, localStorage, WebSocket. Kotlin/JS gets these from the stdlib;
             * Kotlin/Wasm moved them into their own artifact. */
            implementation(libs.kotlinx.browser)
        }

        androidMain.dependencies {
            /* Coil GIF decoder for animated GIF support on Android */
            implementation(libs.coil.gif)

            /* Backward compatibility APIs from Google's Jetpack AndroidX */
            /* Contains AndroidX Libs: Core (+CoreSplashScreen +CorePiP), AppCompat, Activity Compose, DocumentFile */
            implementation(libs.bundles.jetpack.androidx.extensions)

            /* Extended coroutine support for Android threading */
            implementation(libs.kotlin.coroutines.android)

            /* Network and TLS */
            implementation(libs.netty.handler)
            implementation(libs.netty.codec)
            implementation(libs.netty.transport)
            implementation(libs.conscrypt) //TLSv1.3 with backward compatibility

            /* Video player engine: Media3 (ExoPlayer and its extensions) */
            implementation(libs.bundles.media3)

            /* ExoPlayer's FFmpeg-powered audio renderer extension (this does not need to be updated with every media3 release)  */
            implementation(files(File(projectDir, "libs/libffmpeg_media3exo_1.8.0.aar")))

            /* libmpv for Android, prebuilt by libmpvKt (mpv, FFmpeg, libass and libplacebo), with its
             * typed API and the view that hosts the video. The exoOnly flavor keeps both so the engine
             * code compiles; androidApp strips every native library they bring at packaging time. */
            implementation(libs.libmpvkt)

            /* YT/SoundCloud/PeerTube stream URL extractor (no Python, pure JVM) */
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
                implementation(libs.netty.handler)
                implementation(libs.netty.codec)
                implementation(libs.netty.transport)


                /* YT/SoundCloud/PeerTube stream URL extractor (pure JVM, same as Android) */
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
            // Virtual time, so sync tests advance the clock instead of sleeping.
            implementation(libs.kotlin.coroutines.test)
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

/* The live subtitle test spends a unit of the key's small daily quota, so it runs only on request:
 * ./gradlew :shared:desktopTest -PliveSubtitles --tests app.subtitles.SubtitleDownloadE2ETest */
tasks.withType<Test>().configureEach {
    systemProperty("synkplay.liveSubtitles", providers.gradleProperty("liveSubtitles").isPresent)
}

// The repo's two source rewrites, as tasks with declared inputs and outputs. The strings one is
// wired into resource preparation; the launcher colours are on demand (syncTrinityColors).
with(PropagationTasks) {
    registerPropagationTasks()
}

// Lyricist reads the strings.xml of every values folder and writes one Kotlin object per
// locale. It runs on the common metadata compilation, so all targets share the output.
dependencies {
    add("kspCommonMainMetadata", libs.lyricist.processor.xml)
}

ksp {
    arg("lyricist.packageName", "app.i18n")
    arg("lyricist.xml.moduleName", "app")
    arg("lyricist.xml.defaultLanguageTag", "en")
    arg("lyricist.xml.resourcesPath", file("src/commonMain/composeResources").absolutePath)
}

/* Every compilation reads the generated sources, so all of them wait for the generator. */
tasks.matching { it.name.startsWith("compile") || it.name.startsWith("ksp") }.configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}

/* Lint's host-test model reads KSP output. Gradle 9 refuses to infer the ordering and fails the
 * build with an implicit-dependency error, so it is declared. */
tasks.matching { it.name == "generateAndroidHostTestLintModel" || it.name == "lintAnalyzeAndroidHostTest" }
    .configureEach { dependsOn("kspAndroidHostTest") }

ktorfit {
    // The Ktorfit compiler plugin is built against a specific Kotlin compiler ABI, so this
    // must track the `kotlin` version in libs.versions.toml, NOT the ktorfit lib version.
    // A mismatch crashes compilation with "IrGenerationExtension cannot be cast to
    // ProjectExtensionDescriptor". Map: Kotlin 2.3.x -> 2.3.3, Kotlin 2.4.0+ -> 2.3.5.
    compilerPluginVersion.set("2.3.5")
}

tasks.register("propagateSSOT") {
    group = "syncplay"
    description = "Runs both source propagators: the launcher's brand colours and the default-strings fallback."
    dependsOn("syncTrinityColors", "syncDefaultStrings")
}
