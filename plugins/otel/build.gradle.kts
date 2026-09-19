import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "cloud.mallne.dicentra.aviator.plugin"
description = "DiCentra Aviator Plugin for OpenTelemetry distributed tracing"
version = project.findProperty("VERSION_NAME") ?: "1.0.0-SNAPSHOT"

plugins {
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.kmp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            pom {
                name = "DiCentra Aviator OpenTelemetry Plugin"
                description = "DiCentra Aviator Plugin for OpenTelemetry distributed tracing"
                inceptionYear = "2025"
                developers {
                    developer {
                        name = "Mallne"
                        url = "mallne.cloud"
                    }
                }
            }
            repositories {
                maven {
                    url = uri("https://registry.mallne.cloud/repository/DiCentraArtefacts/")
                    credentials {
                        username = project.findProperty("dc.username") as String?
                        password = project.findProperty("dc.password") as String?
                    }
                }
            }
        }
    }
}

kotlin {
    jvm()
    android {
        namespace = "${project.group}.otel"
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
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":core"))
                implementation(project(":clients:ktor"))
                implementation(libs.ktor.client.core)
                implementation(libs.opentelemetry.kotlin.api)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.opentelemetry.kotlin.exporters.inmemory)
            }
        }
    }
    jvmToolchain(25)
}
