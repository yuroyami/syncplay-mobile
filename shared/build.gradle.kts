import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import io.github.yuroyami.kiteconfig.kiteConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kSerialization)
    // The Ktorfit plugin needs KSP, because it registers its code generator as a KSP processor.
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    // Coverage comes from the desktop test run. The root build configures the report.
    alias(libs.plugins.kover)
}

// KiteConfig exposes only the major SDK level, so set the minor level here to keep AGP on the
// required 37.2 API (android.compileSdkMinor in gradle.properties).
extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
    finalizeDsl { dsl ->
        dsl.compileSdk {
            version = release(kiteConfig.compileSdk.get()) {
                minorApiLevel = providers.gradleProperty("android.compileSdkMinor").get().toInt()
            }
        }
    }
}

// The CocoaPods plugin records the custom header path in its generated definition file, but it
// does not track the header's contents. This input regenerates both VLCKit bindings (device and
// simulator) when VlcClock.h changes.
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

        // This module holds almost all the code. Without a lint task here, the repo's lint.xml
        // suppressions apply to nothing.
        lint {
            xmlReport = true
            htmlReport = true
            abortOnError = false
            checkDependencies = false
        }

        // Runs commonTest against the Android actuals on the host JVM.
        withHostTest { }
    }

    // Desktop (JVM) target for Windows, macOS and Linux through Compose for Desktop.
    // :desktopApp hosts the app.
    jvm("desktop")

    /* Web (browser) target through Kotlin/Wasm. The app shell is :webApp.
     *
     * Compose Multiplatform's web target is Beta while the other three are stable, so a failure
     * here may come from the target and not from the app. Two things a browser cannot do are kept
     * out of the web build, not worked around. It has no raw TCP socket, so the Syncplay protocol
     * needs a WebSocket transport. It has no native decoder, so none of the native engines (video
     * players) run here. Everything that needs either one lives in nonWebMain. */
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    // iOS targets (iosMain)
    listOf(
        iosSimulatorArm64(), // Needed only to run on the simulator
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
                    // Resolve both from the framework that this target's podBuild task copies.
                    compilerOpts("-I${project.file("build/cocoapods/synthetic/ios/build/Debug-$podSdk/XCFrameworkIntermediates/VLCKit/VLCKit.framework/Headers")}")
                }
            }
        }
    }

    // The iOS framework and its pods
    cocoapods {
        summary = "${kiteConfig.appName.get()} Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/syncplay-mobile"
        version = "1.0.4"
        ios.deploymentTarget = "15.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = AppConfig.SHARED_MODULE_NAME
            isStatic = false
        }

        pod("VLCKit") {
            version = libs.versions.libvlc.ios.get()
            // Keep the VlcClock.h bridge in this binding, so it compiles against the exact VLCKit
            // headers that ship in the pod.
            headers = project.file("src/nativeInterop/cinterop/VlcClock.h").absolutePath
        }
    }

    /* A hand-written dependsOn edge turns the default hierarchy template off. iosMain then
     * silently leaves the source set tree, and every iOS actual is lost. Applying the template
     * explicitly keeps both. */
    applyDefaultHierarchyTemplate()

    sourceSets {
        /**
         * Code for every target except the browser: Android, iOS and desktop.
         *
         * commonMain holds what all four targets can run. nonWebMain holds what the other three
         * can run and the web cannot: a TCP socket, a native player engine, a filesystem, a
         * thread that may block.
         *
         * Before you put code here, ask one question: does it compile in a browser? If it does,
         * it belongs in commonMain.
         */
        val nonWebMain by creating { dependsOn(commonMain.get()) }

        /**
         * The source set that Android and desktop share.
         *
         * Both run on the JVM and both use Netty and NewPipe, so code that is the same on both
         * lives here once and gets each fix once.
         *
         * Put code here only when it is the same on both. Anything that reads SAF (the Storage
         * Access Framework), a Context or Conscrypt belongs to Android alone and stays in
         * androidMain.
         */
        val jvmShared by creating { dependsOn(nonWebMain) }

        /* The Lyricist processor writes Strings.kt and the per-locale objects here. */
        commonMain.get().kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        androidMain.get().dependsOn(jvmShared)
        getByName("desktopMain").dependsOn(jvmShared)
        iosMain.get().dependsOn(nonWebMain)

        /* Tests follow the same split. A test that blocks a thread cannot run in a browser, so
         * it goes in nonWebTest, not commonTest. Coverage does not change, because the desktop
         * run still executes every test. */
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
            /* The @Preview annotation for the control previews (app.uicomponents.previews).
             * Annotation only: the IDE supplies the renderer, so this adds nothing to a shipped
             * build. */
            implementation(compose.components.uiToolingPreview)

            /* Keeps the Kotlin standard library on the compiler's version */
            implementation(libs.kotlin.stdlib)

            /* Pins the coroutines version instead of taking the transitive one */
            implementation(libs.kotlin.coroutines.core)

            /* Dates and times (for example, a date from an epoch timestamp) */
            implementation(libs.kotlinx.datetime)

            /* Holds the display language in Compose state, so switching it recomposes the app
             * instead of restarting it. The strings themselves are generated below. */
            implementation(libs.lyricist)

            /* JSON serialization for the Syncplay protocol */
            implementation(libs.kotlinx.serialization.json)

            /* A multiplatform version of Android's Uri class */
            implementation(libs.uriKmp)

            /* Jetpack DataStore for preferences; Compose observes the values live */
            implementation(libs.datastore)

            /* Compose core dependencies */
            implementation(libs.bundles.compose.multiplatform)

            /* ViewModel support */
            implementation(libs.compose.viewmodel)

            /* Screen navigation with Navigation 3 */
            implementation(libs.bundles.navigation3)

            /* Haze: backdrop blur for the glass popups and for the controls over the video. Haze
             * samples only pixels that Compose draws. So it blurs the whole UI, but it blurs video
             * only on KitePlayer's Compose-canvas path (see GlassSurface.kt). */
            implementation(libs.bundles.haze)

            /* MaterialKolor generates Material3 themes from seed colors */
            implementation(libs.materialKolor)

            /* Color calculations for the color preferences */
            implementation(libs.kolorpicker)

            /* Hash functions */
            implementation(libs.bundles.krypto)

            /* Logging */
            implementation(libs.logging.kermit)

            /* Multiplatform file picker and saver (FileKit) */
            implementation(libs.filekit)

            /* Atomics (kotlinx.atomicfu) */
            implementation(libs.atomicfu)

            /* Coil for async image loading (the GIF panel) */
            implementation(libs.bundles.coil)

            /* Ktor HTTP client for REST calls (the KLIPY GIF API and OpenSubtitles) */
            implementation(libs.bundles.ktor.client)

            implementation(libs.ktorfit)
        }

        nonWebMain.dependencies {
            /* Ktor raw TCP sockets, for the Ktor client transport and the iOS server engine.
             * The artifact does publish a web build, but its sockets are Node's, not a
             * browser's, so this stays out of commonMain. */
            implementation(libs.bundles.ktor)

            /* KitePlayerVideo, which picks the renderer at runtime. This one dependency
             * re-exports both renderers plus KitePlayer's default assembly, facade and core API.
             * The renderer toggle in the room passes its choice through the `path` parameter.
             * The decoder is FFmpeg through JNI and cinterop, so there is no web build. */
            implementation(libs.kiteplayer.compose)
            implementation(libs.kiteplayer.audioviz)
        }

        getByName("wasmJsMain").dependencies {
            /* The browser's own fetch(), behind the same Ktor client the app already uses. */
            implementation(libs.ktor.client.js)

            /* window, document, localStorage, WebSocket. Kotlin/JS gets these from the stdlib;
             * Kotlin/Wasm has them in a separate artifact. */
            implementation(libs.kotlinx.browser)
        }

        androidMain.dependencies {
            /* Coil GIF decoder for animated GIF support on Android */
            implementation(libs.coil.gif)

            /* AndroidX (Jetpack) libraries: Core, Core SplashScreen, AppCompat, Activity Compose
             * and DocumentFile */
            implementation(libs.bundles.jetpack.androidx.extensions)

            /* Coroutine support for the Android main thread */
            implementation(libs.kotlin.coroutines.android)

            /* Network and TLS */
            implementation(libs.netty.handler)
            implementation(libs.netty.codec)
            implementation(libs.netty.transport)
            implementation(libs.conscrypt) // TLS 1.3, also on older Android versions

            /* Video player engine: Media3 (ExoPlayer and its extensions) */
            implementation(libs.bundles.media3)

            /* ExoPlayer's FFmpeg audio renderer extension. It does not need an update with every
             * Media3 release. */
            implementation(files(File(projectDir, "libs/libffmpeg_media3exo_1.8.0.aar")))

            /* libmpv for Android, prebuilt by libmpvKt (mpv, FFmpeg, libass and libplacebo), with
             * its typed API and the view that hosts the video. The exoOnly flavor keeps both so the
             * engine code compiles; androidApp removes all of their native libraries at packaging
             * time. */
            implementation(libs.libmpvkt)
            implementation(libs.libmpvkt.view)

            /* NewPipe Extractor: stream URLs for YouTube, SoundCloud and PeerTube (pure JVM) */
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
                /* Network and TLS: the same Netty engine as Android (pure JVM). TLS comes from the
                 * JDK, so desktop needs no Conscrypt. */
                implementation(libs.netty.handler)
                implementation(libs.netty.codec)
                implementation(libs.netty.transport)


                /* NewPipe Extractor: stream URLs for YouTube, SoundCloud and PeerTube (pure JVM) */
                implementation(libs.newpipe.extractor)

                /* Ktor HTTP client engine for desktop (also backs Coil's network fetcher) */
                implementation(libs.ktor.client.okhttp)

                /* Swing interop and the desktop-specific Compose APIs */
                implementation(compose.desktop.common)

                /* Registers Dispatchers.Main on the AWT event thread. The whole codebase dispatches
                 * on Dispatchers.Main (and .immediate), which has no default on the JVM. */
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

/* Skiko's native binary must match what Compose was compiled against. A transitive bump would
 * silently swap Skia under Compose. Bump `skiko` in libs.versions.toml together with
 * compose-multiplatform (see the note on `skiko` there). */
configurations.configureEach {
    resolutionStrategy.force("org.jetbrains.skiko:skiko:${libs.versions.skiko.get()}")
}

/* The live subtitle test uses up part of the key's small daily quota, so it runs only on request:
 * ./gradlew :shared:desktopTest -PliveSubtitles --tests app.subtitles.SubtitleDownloadE2ETest */
tasks.withType<Test>().configureEach {
    systemProperty("synkplay.liveSubtitles", providers.gradleProperty("liveSubtitles").isPresent)
}

// The two tasks that rewrite source files in this repo, with declared inputs and outputs.
// Resource preparation depends on the strings task; the launcher colours task runs on demand
// (syncTrinityColors).
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

/* The generator writes the language table in the order it read the resource folders. That order
 * differs per machine and reaches the compiled code. The sort lets anyone rebuild a published APK
 * and compare it byte for byte. See buildSrc/LocaleOrder.kt. */
val sortGeneratedLocales = with(LocaleOrder) { registerLocaleOrderTask() }

/* Every compilation reads the generated sources, so each one waits for the generator and for the
 * sort after it. */
tasks.matching { it.name.startsWith("compile") || it.name.startsWith("ksp") }.configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn(sortGeneratedLocales)
}

/* Lint's host-test model reads KSP output. Gradle 9 does not infer that order and fails the build
 * with an implicit-dependency error, so the dependency is declared here. */
tasks.matching { it.name == "generateAndroidHostTestLintModel" || it.name == "lintAnalyzeAndroidHostTest" }
    .configureEach { dependsOn("kspAndroidHostTest") }

ktorfit {
    // The Ktorfit compiler plugin is built against a specific Kotlin compiler ABI, so this
    // version follows the `kotlin` version in libs.versions.toml, not the ktorfit library version.
    // A mismatch crashes compilation with "IrGenerationExtension cannot be cast to
    // ProjectExtensionDescriptor". Map: Kotlin 2.3.x -> 2.3.3, Kotlin 2.4.0+ -> 2.3.5.
    compilerPluginVersion.set("2.3.5")
}

tasks.register("propagateSSOT") {
    group = "syncplay"
    description = "Runs both source propagators: the launcher's brand colours and the default-strings fallback."
    dependsOn("syncTrinityColors", "syncDefaultStrings")
}
