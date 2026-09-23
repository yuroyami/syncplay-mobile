plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    /* The browser app shell: one target and one binary. Everything else is in :shared. */
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                // index.html loads this file. The fixed name makes sure the page finds the bundle.
                outputFileName = "synkplay.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        getByName("wasmJsMain").dependencies {
            implementation(project(":shared"))

            /* :shared declares its dependencies with `implementation`, so the types that its
             * public API exposes must be declared again here, as in :desktopApp. */
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.compose.viewmodel)
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlin.coroutines.core)
        }
    }
}
