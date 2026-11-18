@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val omitWasmJsForRelease = providers.gradleProperty("omitWasmJsForRelease").orNull == "true"

group = "cloud.mallne.dicentra.aviator.orchestration"
version = project.findProperty("VERSION_NAME") ?: "1.0.0-SNAPSHOT"
description = "Portable contracts for optional Aviator orchestration."

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mavenPublish)
}

mavenPublishing {
    coordinates(group.toString(), project.name)
}

kotlin {
    jvm()
    android {
        namespace = "${project.group}.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
    }
    js {
        nodejs()
        browser()
    }
    if (!omitWasmJsForRelease) {
        wasmJs { browser(); nodejs() }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
    jvmToolchain(25)
}
