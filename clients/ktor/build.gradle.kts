@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "cloud.mallne.dicentra.aviator.client"
description = "DiCentra Aviator preconfigured for Spring Reactive."
version = project.findProperty("VERSION_NAME") ?: "1.0.0-SNAPSHOT"

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
                username = project.findProperty("dc.username") as? String ?: ""
                password = project.findProperty("dc.password") as? String ?: ""
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            pom {
                name = "DiCentra Aviator KTor"
                description =
                    "Ktor Implementation of DiCentra Aviator"
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
        namespace = "${project.group}.ktor"
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
                api(project(":core"))
                api(project(":koas"))
                implementation(libs.dc.polyfill)
                implementation(libs.ktor.client.core)
                api(libs.ktor.http)
                api(libs.ktor.openapi.schema)
            }
        }
    }
    jvmToolchain(25)
}