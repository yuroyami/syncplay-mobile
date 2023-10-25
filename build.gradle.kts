plugins {
    id("org.jetbrains.kotlin.multiplatform") version "1.9.10" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("org.jetbrains.kotlin.native.cocoapods") version "1.9.10" apply false
    id("com.android.application") version "8.1.1" apply false
    id("com.android.library") version "8.1.1" apply false
    id("org.jetbrains.compose") version "1.5.10-rc01" apply false
    id("dev.icerock.mobile.multiplatform-resources") version "0.23.0" apply false
    kotlin("plugin.serialization") version "1.9.10" apply false
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
    }
}
