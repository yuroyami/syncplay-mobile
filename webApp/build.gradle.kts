plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    /* The browser shell. One target, one binary: everything else is in :shared. */
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                // What index.html loads. Pinned so the page and the bundle cannot drift apart.
                outputFileName = "synkplay.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        getByName("wasmJsMain").dependencies {
            implementation(project(":shared"))

            /* :shared declares its deps with `implementation`, so types leaking through its
             * public API have to be named again here. Same reason as :desktopApp. */
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.compose.viewmodel)
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlin.coroutines.core)
        }
    }
}
