@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

group = "cloud.mallne.dicentra.aviator.orchestration"
version = project.findProperty("VERSION_NAME") ?: "1.0.0-SNAPSHOT"
description = "Arazzo workflow orchestration for Aviator."

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mavenPublish)
}

mavenPublishing {
    coordinates(group.toString(), project.name)
}

kotlin {
    jvm()
    js {
        nodejs()
        browser()
    }
    wasmJs {
        browser()
        nodejs()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":orchestration:core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
    jvmToolchain(25)
}
