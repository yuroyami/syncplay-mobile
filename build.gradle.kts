buildscript {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven(url = "https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.0-alpha04")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.21")
    }
}

tasks.register("clean") {
    delete(rootProject.buildDir)
}
