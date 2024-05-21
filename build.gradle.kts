plugins {
    val kotlin = "2.0.0-RC3"
    id("org.jetbrains.kotlin.multiplatform") version kotlin apply false
    id("org.jetbrains.kotlin.android") version kotlin apply false
    id("org.jetbrains.kotlin.native.cocoapods") version kotlin apply false
    kotlin("plugin.serialization") version kotlin apply false

    id("org.jetbrains.kotlin.plugin.compose") version kotlin apply false

    val agp = "8.5.0-beta02"
    id("com.android.application") version agp apply false
    id("com.android.library") version agp apply false

    val compose = "1.6.10" //Last stable: 1.6.10
    id("org.jetbrains.compose") version compose apply false

    id("com.google.devtools.ksp") version "$kotlin-1.0.20" apply false
}