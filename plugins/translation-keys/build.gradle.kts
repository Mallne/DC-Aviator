import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "cloud.mallne.dicentra.weaver.plugins"
version = "1.0.0-SNAPSHOT"
description = "DiCentra Aviator Plugin that uses Weavers Object Mapping to translate input and Output"

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
                name = "DiCentra Weaver X Aviator TranslationKeys"
                description = "DiCentra Aviator Plugin that uses Weavers Object Mapping to translate input and Output"
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
                        username = properties["dc.username"] as String?
                        password = properties["dc.password"] as String?
                    }
                }
            }
        }
    }
}

kotlin {
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":core"))
                //Dependency Substitution does not work in KMP for whatever Reason
                if (findProject(":polyfill") != null) {
                    implementation(project(":polyfill"))
                } else {
                    implementation(libs.dc.polyfill)
                }
                implementation(project(":koas"))
                implementation(libs.ktor.client.core)
            }
        }
    }
}

android {
    namespace = "cloud.mallne.dicentra.weaver.plugins"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}