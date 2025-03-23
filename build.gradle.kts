plugins {
    val kotlin = "2.1.20"
    id("org.jetbrains.kotlin.multiplatform") version kotlin apply false
    id("org.jetbrains.kotlin.android") version kotlin apply false
    id("org.jetbrains.kotlin.native.cocoapods") version kotlin apply false
    kotlin("plugin.serialization") version kotlin apply false

    id("org.jetbrains.kotlin.plugin.compose") version kotlin apply false

    val agp = "8.11.0-alpha02"
    id("com.android.application") version agp apply false
    id("com.android.library") version agp apply false

    val compose = "1.8.0-alpha04"
    id("org.jetbrains.compose") version compose apply false

    id("com.google.devtools.ksp") version "$kotlin-1.0.31" apply false
}