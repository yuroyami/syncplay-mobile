plugins {
    val kotlin = "2.0.0-Beta1" //1.9.21
    id("org.jetbrains.kotlin.multiplatform") version kotlin apply false
    id("org.jetbrains.kotlin.android") version kotlin apply false
    id("org.jetbrains.kotlin.native.cocoapods") version kotlin apply false
    kotlin("plugin.serialization") version kotlin apply false

    val agp = "8.3.0-alpha18" //"8.1.4"
    id("com.android.application") version agp apply false
    id("com.android.library") version agp apply false

    val compose = "1.6.0-dev1347" //"1.5.11" //17
    id("org.jetbrains.compose") version compose apply false

    id("com.google.devtools.ksp") version "2.0.0-Beta1-1.0.15" apply false
}