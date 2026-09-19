@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "cloud.mallne.dicentra.aviator.plugin.adapter"
description = "DiCentra Aviator Adapter for JSON Bodies"
version = "1.0.0-SNAPSHOT"

plugins {
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.kmp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}


publishing {
    repositories {
        maven {
            name = "DiCentraArtefacts"
            url = uri("https://registry.mallne.cloud/repository/DiCentraArtefacts/")
            credentials {
                username = providers.environmentVariable("NEXUS_USERNAME").getOrElse("")
                password = providers.environmentVariable("NEXUS_PASSWORD").getOrElse("")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            pom {
                name = "DiCentra Aviator JSON Adapter"
                description =
                    "DiCentra Aviator Adapter for JSON Bodies"
                inceptionYear = "2025"
                developers {
                    developer {
                        name = "Mallne"
                        url = "mallne.cloud"
                    }
                }
            }
        }
    }
}

kotlin {
    jvm()
    android {
        namespace = "${project.group}.json"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }
    js {
        nodejs()
        browser()
    }
    wasmJs {
        browser()
        nodejs()
        d8()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        val commonMain = getByName("commonMain") {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":core"))
                implementation(libs.ktor.http)
            }
        }
    }
    jvmToolchain(25)
}