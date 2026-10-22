@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "cloud.mallne.dicentra.aviator"
version = project.findProperty("VERSION_NAME") ?: "1.0.0-SNAPSHOT"

plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.npm.publish)
}

kotlin {
    jvm()
    android {
        namespace = "${project.group}.koas"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }
    js {
        nodejs()
        binaries.library()
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
                implementation(libs.ktor.http)
                api(libs.ktor.openapi.schema)
                implementation(libs.dc.polyfill)
            }
        }
    }
    jvmToolchain(25)
}

npmPublish {
    organization = "dicentra"
    registries {
        register("nodeRegistry") {
            uri.set("https://registry.mallne.cloud/repository/NodeRegistry/")
            auth.set(providers.environmentVariable("NPM_TOKEN").orNull)
        }
    }
    packages {
        named("js") {
            packageName = "aviator-koas"
            version = project.version.toString().replace("-SNAPSHOT", "-snapshot")
        }
    }
}


mavenPublishing {
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
    }

    coordinates(group.toString(), project.name)
    pom {
        name = "Dicentra Aviator Koas"
        description = "An Kotlin Multiplatform OpenAPI specification and parser utilizing kotlinx.serialization"
        inceptionYear = "2024"
        developers {
            developer {
                name = "Mallne"
                url = "mallne.cloud"
            }
        }
    }
}
