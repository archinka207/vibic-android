// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Применяем плагины на уровне проекта (не нужны, если используем pluginManagement)
    id("com.google.gms.google-services") version "4.4.4" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.6.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.20-1.0.25")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}