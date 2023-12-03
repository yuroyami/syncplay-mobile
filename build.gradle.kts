plugins {
    val kotlin = "1.9.21" //"2.0.0-Beta1"
    id("org.jetbrains.kotlin.multiplatform") version kotlin apply false
    id("org.jetbrains.kotlin.android") version kotlin apply false
    id("org.jetbrains.kotlin.native.cocoapods") version kotlin apply false
    kotlin("plugin.serialization") version kotlin apply false

    val agp = "8.2.0"
    id("com.android.application") version agp apply false
    id("com.android.library") version agp apply false

    val compose = "1.6.0-dev1296" //"1.5.11"
    id("org.jetbrains.compose") version compose apply false

    id("dev.icerock.mobile.multiplatform-resources") version "0.23.0" apply false

}

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    dependencies {
        classpath("dev.icerock.moko:resources-generator:0.23.0")
    }
}


allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
