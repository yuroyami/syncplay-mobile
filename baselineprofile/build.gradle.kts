plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

/* Generates the app's baseline profile (the code that Android compiles ahead of time at install)
 * and measures cold start. Both need a connected device and run only on request. The steps are in
 * docs/DEVELOPING.md, under "Baseline profile". */

// :androidApp builds one flavor at a time, so this module declares the same one.
val exoOnly = AppConfig.resolveExoOnly(providers)

android {
    namespace = "app.baselineprofile"
    compileSdk = providers.gradleProperty("android.compileSdk").get().toInt()

    defaultConfig {
        // Profile collection needs Android 9. On a device without root, it needs Android 13.
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":androidApp"

    flavorDimensions += "flavor"
    productFlavors {
        create(if (exoOnly) "exoOnly" else "full") {
            dimension = "flavor"
        }
    }
}

baselineProfile {
    // A connected physical device. This project does not test on a phone emulator.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// The tests read the application id of the installed copy, suffix included, from this argument.
androidComponents {
    onVariants { variant ->
        val loader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { loader.load(it)?.applicationId },
        )
    }
}
