plugins {
    id("org.jetbrains.kotlin.multiplatform") version "1.9.20" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("org.jetbrains.kotlin.native.cocoapods") version "1.9.20" apply false
    id("com.android.application") version "8.1.4" apply false
    id("com.android.library") version "8.1.4" apply false
    id("org.jetbrains.compose") version "1.6.0-dev1276" apply false
    id("dev.icerock.mobile.multiplatform-resources") version "0.23.0" apply false
    kotlin("plugin.serialization") version "1.9.20" apply false
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
